package com.dmzzero.magic;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath.V;
import com.dmzzero.kishare.TossMath;
import com.dmzzero.sense.CreaturePower;
import com.dmzzero.sense.PowerSense;
import com.dmzzero.smash.Smashes;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

import static com.dmzzero.magic.SpellKit.LILAC;
import static com.dmzzero.magic.SpellKit.offLimits;
import static com.dmzzero.magic.SpellKit.pay;
import static com.dmzzero.magic.SpellKit.sparkle;
import static com.dmzzero.magic.SpellKit.stats;
import static com.dmzzero.magic.SpellKit.v;

// a glind spell: one shove that throws back every mob in front of you. it does no damage itself; a mob shoved into a
// wall shows it the way smashed into the scenery does
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class TelekineticWave implements Spell {
    public static final TelekineticWave SPELL = new TelekineticWave();
    private static final int MAX_PUSHED = 24;
    private static final TossMath.Cooldowns COOLDOWNS = new TossMath.Cooldowns();
    private static int waves;

    private TelekineticWave() {
    }

    @Override
    public String id() {
        return "telekineticWave";
    }

    @Override
    public String english() {
        return "Telekinetic wave";
    }

    @Override
    public ForgeConfigSpec.BooleanValue toggle() {
        return ZeroConfig.SERVER.wave;
    }

    @Override
    public String cast(ServerPlayer caster, StatsData data, long now) {
        long wait = COOLDOWNS.remaining(caster.getUUID(), now);
        if (wait > 0) return tell(caster, "cooldown", "Telekinetic wave ready in %ss.", false, (wait + 19) / 20);
        if (!pay(caster, data, SpellMath.cost(data.getMaxEnergy(), ZeroConfig.SERVER.waveKiCost.get()))) {
            return tell(caster, "no_ki", "Not enough ki.", false);
        }
        COOLDOWNS.start(caster.getUUID(), now, ZeroConfig.SERVER.waveCooldown.get() * 20L);
        int pushed = push(caster, data, around(caster));
        show(caster);
        waves++;
        if (pushed == 0) tell(caster, "nothing", "The wave hits nothing.", false);
        else tell(caster, "pushed", "Shoved %s away.", true, pushed);
        return "ok";
    }

    static List<LivingEntity> around(ServerPlayer caster) {
        double range = ZeroConfig.SERVER.waveRange.get();
        return caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range),
                e -> e != caster && e.isAlive() && !e.isSpectator());
    }

    /** Shoves whatever in around is in front of the caster and weak enough. How many moved. The self-test calls it straight. */
    public static int push(ServerPlayer caster, StatsData data, List<LivingEntity> around) {
        double range = ZeroConfig.SERVER.waveRange.get(), angle = ZeroConfig.SERVER.waveAngle.get();
        double strength = ZeroConfig.SERVER.waveStrength.get(), ratio = ZeroConfig.SERVER.tkStrengthRatio.get();
        double mine = PowerSense.fullPower(data);
        V eye = v(caster.getEyePosition()), look = v(caster.getViewVector(1f));
        int pushed = 0;
        for (LivingEntity e : around.stream().sorted(Comparator.comparingDouble(caster::distanceToSqr)).toList()) {
            if (pushed >= MAX_PUSHED) break;
            if (e == caster || !e.isAlive() || e.level() != caster.level() || offLimits(e, caster) || Spells.holding(e)) continue;
            if (e.getType().is(Tags.EntityTypes.BOSSES)) continue;
            double theirs = CreaturePower.of(e);
            if (e instanceof Player p) {
                if (!SpellKit.mayHurt(caster, p)) continue;
                StatsData their = stats(p);
                theirs = their == null ? 0 : PowerSense.fullPower(their);
            }
            V to = v(e.position().add(0, e.getBbHeight() * 0.5, 0)).sub(eye);
            if (to.length() > range || !WaveMath.inCone(look, to, angle)) continue;
            if (!TelekinesisMath.canLift(mine, theirs, ratio)) continue;
            V shove = WaveMath.push(to, look, range, strength);
            Vec3 motion = new Vec3(shove.x(), shove.y(), shove.z());
            e.setDeltaMovement(motion);
            e.hasImpulse = true;
            e.hurtMarked = true;
            Smashes.launched(e, motion);
            sparkle((ServerLevel) caster.level(), e, 6);
            pushed++;
        }
        return pushed;
    }

    // a fan of lilac sparks along the cone
    private static void show(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();
        double range = ZeroConfig.SERVER.waveRange.get(), angle = Math.toRadians(ZeroConfig.SERVER.waveAngle.get());
        Vec3 eye = caster.getEyePosition(), look = caster.getViewVector(1f);
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        for (double d = 1.5; d <= range; d += 1.5) {
            for (int k = -2; k <= 2; k++) {
                double a = angle * k / 2.0;
                Vec3 dir = look.scale(Math.cos(a)).add(side.scale(Math.sin(a)));
                Vec3 p = eye.add(dir.scale(d));
                level.sendParticles(LILAC, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.01);
            }
        }
        Vec3 front = eye.add(look.scale(1.5));
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y, front.z, 1, 0, 0, 0, 0);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1f, 0.9f);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.7f);
    }

    private static String tell(ServerPlayer caster, String why, String english, boolean bright, Object... args) {
        return SpellKit.tell(caster, "wave", why, english, bright, args);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        COOLDOWNS.clear();
    }

    /** Self-test only: forget the cooldowns. */
    public static void clearAll() {
        COOLDOWNS.clear();
    }

    public static int waves() {
        return waves;
    }
}
