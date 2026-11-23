package com.dmzzero.form;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// the moment a transformation finishes, a shockwave pushes nearby enemies back and throws incoming ki blasts back out
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class TransformBurst {
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static int bursts;

    private TransformBurst() {
    }

    public record Burst(UUID who, int tier, double radius, int pushed, int blasts) {
    }

    /** Called when a player reaches a higher form. Returns what it did, or null when it didn't go off. */
    public static Burst burst(ServerPlayer player, int tier) {
        if (!ZeroConfig.SERVER.transformBurst.get() || !player.isAlive() || player.isSpectator()) return null;
        long now = player.server.getTickCount();
        Long until = NEXT.get(player.getUUID());
        if (until != null && !BurstMath.ready(now, until)) return null;
        NEXT.put(player.getUUID(), now + ZeroConfig.SERVER.burstCooldown.get() * 20L);

        ServerLevel level = player.serverLevel();
        double radius = BurstMath.radius(ZeroConfig.SERVER.burstRadius.get(), tier);
        double strength = ZeroConfig.SERVER.burstStrength.get();
        Vec3 c = player.position();
        AABB box = player.getBoundingBox().inflate(radius);
        int pushed = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> Shove.shovable(player, e))) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double speed = BurstMath.speed(e.position().distanceTo(c), radius, tier, strength);
            if (speed <= 0) continue;
            double[] away = BurstMath.away(dx, dz);
            Shove.push(player, e, new Vec3(away[0] * speed, BurstMath.lift(tier) * Math.min(1, strength), away[1] * speed));
            pushed++;
        }
        int blasts = ZeroConfig.SERVER.burstBlasts.get() ? throwBack(level, player, box, c) : 0;
        bursts++;
        return new Burst(player.getUUID(), BurstMath.tier(tier), radius, pushed, blasts);
    }

    // incoming ki blasts are thrown back out the way they came; they stay the thrower's
    private static int throwBack(ServerLevel level, ServerPlayer player, AABB box, Vec3 c) {
        int n = 0;
        for (KiBlastEntity ball : level.getEntitiesOfClass(KiBlastEntity.class, box, b -> incoming(b, player))) {
            Vec3 out = ball.position().subtract(c);
            if (out.lengthSqr() < 1e-6) out = player.getLookAngle();
            Vec3 vel = out.normalize().scale(BurstMath.blastSpeed(ball.getDeltaMovement().length()));
            ball.setHomingTarget(-1);
            ball.setDeltaMovement(vel);
            ball.hasImpulse = true;
            ball.hurtMarked = true;
            n++;
        }
        return n;
    }

    private static boolean incoming(KiBlastEntity b, ServerPlayer player) {
        if (!b.isAlive() || !b.isFiring() || b.isChargingGenki() || b.isParked() || b.isControllable()) return false;
        Entity owner = b.getOwner();
        if (owner == player) return false;
        return !(owner instanceof Player p) || !TargetHelper.areFriendlyPlayers(player, p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        NEXT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        NEXT.clear();
    }

    // for the self-test
    public static void ready(UUID who) {
        NEXT.remove(who);
    }

    public static int bursts() {
        return bursts;
    }
}
