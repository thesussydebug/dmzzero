package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath.V;
import com.dmzzero.charge.GatherMath;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.init.particles.KiSheddingParticle;
import com.dragonminez.common.init.particles.KiTrailParticle;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// overcharging a technique pulls ki specks and dust in toward the hands, the way the air bends in before a big attack.
// they are dmz's own beam particles in the technique's colour; the charge comes from its synced stats. your screen only
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class GatherEnergy {
    private static final double RANGE = 48;
    private static final int FLIGHT_TICKS = 10;
    private static final int MAX_FIGHTERS = 8;
    // the share of its speed each of dmz's particles keeps every tick
    private static final double STREAK_DRAG = 0.95;
    private static final double SPARK_DRAG = 0.9;
    private static final double DUST_DRAG = 0.93;
    private static final float[] WHITE = {1f, 1f, 1f};
    private static int specks;

    private GatherEnergy() {
    }

    private static boolean enabled() {
        try {
            return ZeroConfig.CLIENT.gather.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || !enabled()) return;
        float startAt = ZeroConfig.CLIENT.gatherAt.get().floatValue();
        int fighters = 0;
        for (AbstractClientPlayer p : level.players()) {
            if (fighters >= MAX_FIGHTERS) break;
            if (p.distanceToSqr(mc.player) > RANGE * RANGE || p.isInvisibleTo(mc.player)) continue;
            StatsData d = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
            if (d == null) continue;
            var t = d.getTechniques();
            if (!(t.isTechniqueCharging() || t.isTechniqueChargeActive())) continue;
            float strength = GatherMath.strength(t.getTechniqueChargePercent(), startAt);
            if (strength <= 0) continue;
            fighters++;
            gather(mc, level, p, strength);
        }
    }

    private static void gather(Minecraft mc, ClientLevel level, AbstractClientPlayer p, float strength) {
        AbstractKiProjectile ball = Callouts.charging(p);
        Vec3 at = ball != null ? orbCentre(ball)
                : p.getEyePosition().add(p.getLookAngle().scale(0.7)).add(0, -0.4, 0);
        // the colour dmz gives its own beams' particles
        float[] colour = ball != null ? ball.getRgbColorBorder() : WHITE;
        V hands = new V(at.x, at.y, at.z);
        // specks pass right by the camera in your own first person view, where full size would cover the screen
        float size = p == mc.player && mc.options.getCameraType().isFirstPerson() ? 0.4f : 1f;
        RandomSource random = level.random;
        int n = GatherMath.specks(strength);
        for (int i = 0; i < n; i++) {
            V dir = new V(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian());
            V from = GatherMath.from(hands, dir, 1.6 + 1.6 * strength + random.nextDouble());
            boolean streak = i % 2 == 0;
            boolean dust = !streak && random.nextBoolean();
            SimpleParticleType type = streak ? MainParticles.KI_SHEDDING.get() : dust ? MainParticles.DUST.get() : MainParticles.KI_TRAIL.get();
            Particle speck = mc.particleEngine.createParticle(type, from.x(), from.y(), from.z(), 0, 0, 0);
            if (speck == null) continue;
            if (speck instanceof KiSheddingParticle shed) {
                shed.setKiColor(colour[0], colour[1], colour[2]);
            } else if (speck instanceof KiTrailParticle trail) {
                trail.setKiColor(colour[0], colour[1], colour[2]);
                // dmz's ki texture fills under half its square, so a smaller one all but vanishes
                trail.setKiScale(0.15f + 0.2f * strength);
            } else {
                // at its own size dmz's dust reads as smoke clouds that hide the specks
                speck.scale(0.4f);
            }
            speck.scale(size);
            V vel = GatherMath.velocity(from, hands, FLIGHT_TICKS, streak ? STREAK_DRAG : dust ? DUST_DRAG : SPARK_DRAG);
            speck.setParticleSpeed(vel.x(), vel.y(), vel.z());
            speck.setLifetime(FLIGHT_TICKS);
            specks++;
        }
    }

    // KiWaveRenderer draws a wave's charge orb at the entity's origin, while KiProjectileRenderer lifts a ball by half its
    // hitbox. a wave's hitbox grows with its size, so lifting it too sent the specks blocks above a big wave's orb
    private static Vec3 orbCentre(AbstractKiProjectile ball) {
        if (ball instanceof KiWaveEntity) return ball.position();
        return ball.position().add(0, ball.getBbHeight() * 0.5, 0);
    }

    // for the dev autotest
    public static int specks() {
        return specks;
    }
}
