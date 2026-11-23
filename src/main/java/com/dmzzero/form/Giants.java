package com.dmzzero.form;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.impact.ImpactProfile;
import com.dmzzero.impact.ImpactWorld;
import com.dragonminez.common.combat.util.KiTerrainDestruction;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// giant forms fight like giants (dmz already gives them reach by size): they stomp when they land, and smaller fighters can't knock them back
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Giants {
    private static final double MIN_FALL = 1.0;
    private static final double STOMP_LIFT = 0.35;
    // a giant's last knocked-at moment counts this many ticks for who hit it
    private static final int HIT_WINDOW = 2;
    private static final Map<UUID, Long> NEXT_STOMP = new HashMap<>();
    private static int stomps;
    private static Stomp last;

    private Giants() {
    }

    public record Stomp(UUID who, double scale, double radius, int pushed, boolean crater) {
    }

    /** How big this one is: a player's form size from DMZ, anything else by its height. */
    public static double scaleOf(LivingEntity e) {
        if (e instanceof Player p) {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
            if (data != null && data.getStatus().isHasCreatedCharacter()) return GiantMath.scale(data.getCharacter().getResolvedModelScaling());
        }
        return Math.max(1.0, e.getBbHeight() / 1.8);
    }

    public static boolean isGiant(LivingEntity e) {
        return ZeroConfig.SERVER.giants.get() && GiantMath.isGiant(scaleOf(e), ZeroConfig.SERVER.giantScale.get());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !GiantMath.stomps(event.getDistance(), MIN_FALL)) return;
        if (!ZeroConfig.SERVER.giantStomp.get() || !isGiant(player)) return;
        stomp(player);
    }

    /** A giant's landing: a shockwave pushes away everyone nearby it may hit, and the ground gives a little. */
    public static Stomp stomp(ServerPlayer player) {
        long now = player.server.getTickCount();
        Long until = NEXT_STOMP.get(player.getUUID());
        if (until != null && now < until) return null;
        NEXT_STOMP.put(player.getUUID(), now + ZeroConfig.SERVER.stompCooldown.get() * 20L);
        ServerLevel level = player.serverLevel();
        double scale = scaleOf(player);
        double radius = GiantMath.stompRadius(scale);
        Vec3 c = player.position();
        double strength = ZeroConfig.SERVER.stompStrength.get();
        int pushed = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius, 2.5, radius), e -> Shove.shovable(player, e))) {
            double speed = GiantMath.stompSpeed(e.position().distanceTo(c), radius, strength);
            if (speed <= 0) continue;
            double[] away = BurstMath.away(e.getX() - c.x, e.getZ() - c.z);
            Shove.push(player, e, new Vec3(away[0] * speed, STOMP_LIFT * strength, away[1] * speed));
            pushed++;
        }
        ImpactWorld.shockwave(level, c.add(0, 0.1, 0), new ImpactProfile(3, ImpactProfile.SHOCKWAVE));
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9f, 0.55f);
        boolean crater = false;
        if (ZeroConfig.SERVER.stompCraters.get() && ImpactWorld.destructionAllowed(player)) {
            float r = (float) GiantMath.craterRadius(scale);
            KiTerrainDestruction.carve(level, BlockPos.containing(c).below(), r, r, Block.UPDATE_ALL, player);
            crater = true;
        }
        stomps++;
        last = new Stomp(player.getUUID(), scale, radius, pushed, crater);
        return last;
    }

    /** DMZ's heavy knockback on a giant: from someone much smaller it does nothing. */
    public static Vec3 steady(LivingEntity target, Vec3 knock) {
        if (knock == null || !ZeroConfig.SERVER.giants.get() || !ZeroConfig.SERVER.giantSteady.get()) return knock;
        double scale = scaleOf(target);
        if (!GiantMath.isGiant(scale, ZeroConfig.SERVER.giantScale.get())) return knock;
        LivingEntity from = Shove.pusher() != null ? Shove.pusher() : recentAttacker(target);
        double fromScale = from == null ? 1.0 : scaleOf(from);
        if (!GiantMath.resists(scale, fromScale, ZeroConfig.SERVER.giantSteadyRatio.get())) return knock;
        return target.getDeltaMovement();
    }

    private static LivingEntity recentAttacker(LivingEntity target) {
        LivingEntity by = target.getLastHurtByMob();
        return by != null && target.tickCount - target.getLastHurtByMobTimestamp() <= HIT_WINDOW ? by : null;
    }

    // vanilla knockback: a sword, a mob's hit, an explosion's push
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !ZeroConfig.SERVER.giants.get() || !ZeroConfig.SERVER.giantSteady.get()) return;
        double scale = scaleOf(target);
        if (!GiantMath.isGiant(scale, ZeroConfig.SERVER.giantScale.get())) return;
        LivingEntity from = recentAttacker(target);
        if (GiantMath.resists(scale, from == null ? 1.0 : scaleOf(from), ZeroConfig.SERVER.giantSteadyRatio.get())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        NEXT_STOMP.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        NEXT_STOMP.clear();
    }

    // for the self-test
    public static void ready(UUID who) {
        NEXT_STOMP.remove(who);
    }

    public static int stomps() {
        return stomps;
    }

    public static Stomp last() {
        return last;
    }
}
