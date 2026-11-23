package com.dmzzero.swat;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.sense.CreaturePower;
import com.dmzzero.sense.PowerSense;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// red ribbon's bullets just bounced off goku: arrows and tridents glance off a fighter far stronger than whoever shot them.
// cancelling the attack makes vanilla's arrow turn back off the target at a tenth of its speed
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class ArrowBounce {
    private static int bounced;
    // the self-test's minimum, so it never writes the config (lessons.md)
    private static Integer testMinPower;

    private ArrowBounce() {
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow) || !(event.getEntity() instanceof ServerPlayer target)) return;
        if (!ZeroConfig.SERVER.arrowBounce.get() || !bounces(target, arrow.getOwner())) return;
        event.setCanceled(true);
        bounced++;
        ServerLevel level = target.serverLevel();
        Vec3 at = arrow.position();
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.3);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.15f, 2.0f);
    }

    public static boolean bounces(ServerPlayer target, Entity shooter) {
        StatsData d = StatsProvider.get(StatsCapability.INSTANCE, target).orElse(null);
        if (d == null || !d.getStatus().isHasCreatedCharacter()) return false;
        double theirs = shooter instanceof LivingEntity l ? power(l) : 0;
        int min = testMinPower != null ? testMinPower : ZeroConfig.SERVER.arrowMinPower.get();
        return ArrowMath.bounces(PowerSense.fullPower(d), theirs, theirs > 0, ZeroConfig.SERVER.arrowRatio.get(), min);
    }

    /** Null goes back to the config's value. */
    public static void setTestMinPower(Integer min) {
        testMinPower = min;
    }

    // a player is as strong as their full power, whatever they've released right now
    static double power(LivingEntity e) {
        if (e instanceof Player p) {
            StatsData d = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
            return d == null ? 0 : PowerSense.fullPower(d);
        }
        return CreaturePower.of(e);
    }

    public static int bounced() {
        return bounced;
    }
}
