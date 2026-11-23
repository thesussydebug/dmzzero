package com.dmzzero.beam;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath.V;
import com.dmzzero.mixin.KiWaveAccess;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// blocking a much weaker fighter's beam knocks it up into the sky: it bends off you and keeps going upward
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class BeamDeflect {
    // a deflected beam never hits the one who deflected it
    private static final Map<Integer, UUID> DEFLECTED = new HashMap<>();
    private static int deflected;

    private BeamDeflect() {
    }

    /** Called when the beam is about to hit target. True when the hit is blocked: the beam was deflected, now or before. */
    public static boolean blocks(KiWaveEntity wave, LivingEntity target) {
        if (wave.level().isClientSide() || !(target instanceof ServerPlayer defender)) return false;
        UUID by = DEFLECTED.get(wave.getId());
        if (by != null) return by.equals(defender.getUUID());
        if (!canDeflect(wave, defender)) return false;
        deflect(wave, defender);
        return true;
    }

    public static boolean canDeflect(KiWaveEntity wave, ServerPlayer defender) {
        if (!ZeroConfig.SERVER.beamDeflect.get() || !defender.isAlive() || wave.isClashLocked()) return false;
        Entity owner = wave.getOwner();
        if (owner == defender) return false;
        StatsData d = stats(defender);
        if (d == null || !d.getStatus().isBlocking() || d.getStatus().isKnockedDown() || d.getStatus().isStunned()) return false;
        Vec3 look = defender.getLookAngle();
        Vec3 dir = Vec3.directionFromRotation(wave.getXRot(), wave.getYRot());
        if (!DeflectMath.facing(new V(look.x, look.y, look.z), new V(dir.x, dir.y, dir.z), ZeroConfig.SERVER.deflectAngle.get())) return false;
        StatsData o = owner instanceof Player p ? stats(p) : null;
        if (o != null && o.getStatus().isHasCreatedCharacter()) {
            return DeflectMath.strongerBy(d.getBattlePower(), o.getBattlePower(), ZeroConfig.SERVER.deflectRatio.get());
        }
        return DeflectMath.weakHit(wave.getDamagePerHit(), defender.getMaxHealth(), ZeroConfig.SERVER.deflectShare.get());
    }

    private static StatsData stats(Player p) {
        return StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
    }

    /** The beam now starts at the defender and points up into the sky; it stops following its caster's aim. */
    public static void deflect(KiWaveEntity wave, ServerPlayer defender) {
        float yaw = wave.getYRot();
        float pitch = DeflectMath.deflectedPitch(wave.getXRot());
        wave.setContinuousFollow(false);
        Vec3 at = defender.position().add(0, defender.getBbHeight() * 0.6, 0);
        wave.setPos(at.x, at.y, at.z);
        wave.setYRot(yaw);
        wave.setXRot(pitch);
        wave.getEntityData().set(KiWaveAccess.dmzzero$fixedYaw(), yaw);
        wave.getEntityData().set(KiWaveAccess.dmzzero$fixedPitch(), pitch);
        wave.setBeamLength(1.0f);
        DEFLECTED.put(wave.getId(), defender.getUUID());
        ServerLevel level = defender.serverLevel();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 24, 0.4, 0.4, 0.4, 0.4);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.6);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4f, 1.6f);
        deflected++;
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof KiWaveEntity) DEFLECTED.remove(event.getEntity().getId());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DEFLECTED.clear();
    }

    public static boolean wasDeflected(KiWaveEntity wave) {
        return DEFLECTED.containsKey(wave.getId());
    }

    public static int deflected() {
        return deflected;
    }
}
