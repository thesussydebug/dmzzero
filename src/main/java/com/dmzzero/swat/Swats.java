package com.dmzzero.swat;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath.V;
import com.dmzzero.mixin.AbstractArrowAccess;
import com.dmzzero.network.ImpactPulseS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Status;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// punch an incoming ki ball at the right moment and it flies off where you look. it's yours then, so it can hit whoever fired it
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Swats {
    private static final double CONE_DEG = 60;
    private static final double MIN_SPEED = 0.8;
    // a ball this close and coming at you, punched too early or too late, counts as a miss
    private static final double MISS_RANGE = 8;
    private static final int AFTER_SWAT_TICKS = 4;
    private static final double JOLT_RANGE = 16;
    private static final Map<UUID, Long> LOCKED_UNTIL = new HashMap<>();
    // a client can send swing packets at any rate, so each player's swings are looked at once a tick
    private static final Map<UUID, Long> LAST_SWING = new HashMap<>();
    private static int swats;
    private static int misses;
    private static int caught;
    private static Swat last;

    /** What the last swat did, for the self-test. */
    public record Swat(UUID by, UUID was, int ball, Vec3 velocity) {
    }

    private Swats() {
    }

    public static void swing(ServerPlayer p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return;
        boolean swatting = ZeroConfig.SERVER.swat.get();
        if (!swatting && !ZeroConfig.SERVER.arrowCatch.get()) return;
        if (!p.isAlive() || p.isSpectator()) return;
        StatsData d = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
        if (d != null && !free(d.getStatus())) return;
        long now = p.server.getTickCount();
        Long locked = LOCKED_UNTIL.get(p.getUUID());
        if (locked != null && !SwatMath.ready(now, locked)) return;
        Long lastSwing = LAST_SWING.put(p.getUUID(), now);
        if (lastSwing != null && lastSwing == now) return;

        ServerLevel level = p.serverLevel();
        double reach = ZeroConfig.SERVER.swatReach.get();
        Vec3 eyeV = p.getEyePosition(), lookV = p.getLookAngle();
        V eye = v(eyeV), look = v(lookV);
        List<KiBlastEntity> near = !swatting ? List.of() : level.getEntitiesOfClass(KiBlastEntity.class, new AABB(eyeV, eyeV).inflate(MISS_RANGE),
                b -> swattable(b, p));
        KiBlastEntity best = null;
        double bestD = Double.MAX_VALUE;
        boolean incoming = false;
        for (KiBlastEntity b : near) {
            V at = v(center(b)), vel = v(b.getDeltaMovement());
            if (vel.dot(eye.sub(at)) > 0) incoming = true;
            if (!SwatMath.inWindow(eye, look, at, vel, reach, CONE_DEG)) continue;
            if (!SwatMath.strongEnough(b.getKiDamage(), p.getMaxHealth(), ZeroConfig.SERVER.swatMaxShare.get())) continue;
            double dist = at.sub(eye).length();
            if (dist < bestD) {
                best = b;
                bestD = dist;
            }
        }
        if (best == null) {
            if (ZeroConfig.SERVER.arrowCatch.get() && catchArrow(level, p, eye, look, reach)) {
                LOCKED_UNTIL.put(p.getUUID(), now + AFTER_SWAT_TICKS);
                return;
            }
            // swung at a ball coming in, but not in time: open for a moment
            if (incoming) {
                misses++;
                LOCKED_UNTIL.put(p.getUUID(), now + ZeroConfig.SERVER.swatMissTicks.get());
            }
            return;
        }
        swat(level, p, best, lookV);
        LOCKED_UNTIL.put(p.getUUID(), now + AFTER_SWAT_TICKS);
    }

    // knocked down, stunned or frozen fighters can't swat
    static boolean free(Status s) {
        return !s.isKnockedDown() && !s.isStunned() && !s.isStunEffect() && !s.isMatchFrozen();
    }

    // a ki ball in flight that isn't yours and isn't one of dmz's special ones
    static boolean swattable(KiBlastEntity b, ServerPlayer p) {
        if (b.isRemoved() || !b.isFiring() || b.isControllable() || b.isParked() || b.isChargingGenki()) return false;
        int render = b.getKiRenderType();
        if (render == KiBlastEntity.RENDER_FAKE_MOON || render == KiBlastEntity.RENDER_SOUL_PUNISHER) return false;
        if (p.getUUID().equals(b.getOwnerUUID())) return false;
        return SwatMath.smallEnough(b.getSize(), ZeroConfig.SERVER.swatMaxSize.get().floatValue());
    }

    private static void swat(ServerLevel level, ServerPlayer p, KiBlastEntity b, Vec3 look) {
        UUID was = b.getOwnerUUID();
        V vel = SwatMath.velocity(v(look), b.getDeltaMovement().length(), MIN_SPEED, ZeroConfig.SERVER.swatBoost.get());
        if (vel == null) return;
        Vec3 motion = new Vec3(vel.x(), vel.y(), vel.z());
        b.setHomingTarget(-1);
        b.setOwner(p);
        b.setDeltaMovement(motion);
        b.hasImpulse = true;
        b.hurtMarked = true;
        swats++;
        last = new Swat(p.getUUID(), was, b.getId(), motion);
        Vec3 at = center(b);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.4);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.5);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 1.1f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25f, 1.9f);
        ZeroNetwork.sendAround(level, at, JOLT_RANGE, new ImpactPulseS2C(at.x, at.y, at.z, 0.2f, (float) JOLT_RANGE, 0, 0f, 0f));
    }

    // gohan caught bullets as the great saiyaman: the same moment as a swat catches an arrow, and a player's arrow comes back as an item
    static boolean catchArrow(ServerLevel level, ServerPlayer p, V eye, V look, double reach) {
        Vec3 e = new Vec3(eye.x(), eye.y(), eye.z());
        List<AbstractArrow> arrows = level.getEntitiesOfClass(AbstractArrow.class, new AABB(e, e).inflate(reach + 1),
                a -> !a.isRemoved() && a.getOwner() != p && !((AbstractArrowAccess) a).dmzzero$inGround());
        AbstractArrow best = null;
        double bestD = Double.MAX_VALUE;
        for (AbstractArrow a : arrows) {
            V at = v(a.position()), vel = v(a.getDeltaMovement());
            if (!SwatMath.inWindow(eye, look, at, vel, reach, CONE_DEG)) continue;
            if (!SwatMath.strongEnough((float) ArrowMath.hit(a.getBaseDamage(), vel.length()), p.getMaxHealth(), ZeroConfig.SERVER.swatMaxShare.get())) continue;
            double dist = at.sub(eye).length();
            if (dist < bestD) {
                best = a;
                bestD = dist;
            }
        }
        if (best == null) return false;
        ItemStack item = best.pickup == AbstractArrow.Pickup.ALLOWED ? ((AbstractArrowAccess) best).dmzzero$pickupItem() : ItemStack.EMPTY;
        Vec3 at = best.position();
        best.discard();
        if (!item.isEmpty() && !p.getInventory().add(item)) p.drop(item, false);
        caught++;
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.2);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    public static int caughtCount() {
        return caught;
    }

    private static Vec3 center(KiBlastEntity b) {
        return b.position().add(0, b.getBbHeight() * 0.5, 0);
    }

    private static V v(Vec3 v) {
        return new V(v.x, v.y, v.z);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LOCKED_UNTIL.remove(event.getEntity().getUUID());
        LAST_SWING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOCKED_UNTIL.clear();
        LAST_SWING.clear();
    }

    public static int swatCount() {
        return swats;
    }

    public static int missCount() {
        return misses;
    }

    public static Swat last() {
        return last;
    }

    /** The self-test swings again straight away. */
    public static void unlock(UUID player) {
        LOCKED_UNTIL.remove(player);
        LAST_SWING.remove(player);
    }
}
