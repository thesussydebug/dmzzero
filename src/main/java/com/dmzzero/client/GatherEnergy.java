package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath.V;
import com.dmzzero.charge.GatherMath;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

// overcharging a technique pulls dust and specks of light in toward the hands, the way the air bends in before a big attack.
// the charge comes from dmz's synced stats; your screen only
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class GatherEnergy {
    private static final double RANGE = 48;
    private static final int FLIGHT_TICKS = 10;
    private static final int MAX_FIGHTERS = 8;
    private static final DustParticleOptions DUST = new DustParticleOptions(new Vector3f(0.62f, 0.58f, 0.52f), 0.7f);
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
        int rgb = ball != null ? ball.getColor() : 0xFFFFFF;
        Vector3f energy = new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        V hands = new V(at.x, at.y, at.z);
        RandomSource random = level.random;
        int n = GatherMath.specks(strength);
        for (int i = 0; i < n; i++) {
            V dir = new V(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian());
            V from = GatherMath.from(hands, dir, 1.6 + 1.6 * strength + random.nextDouble());
            V vel = GatherMath.velocity(from, hands, FLIGHT_TICKS);
            ParticleOptions kind = i % 2 == 0 ? new DustParticleOptions(energy, 0.6f + 0.4f * strength) : (random.nextBoolean() ? DUST : ParticleTypes.END_ROD);
            Particle speck = mc.particleEngine.createParticle(kind, from.x(), from.y(), from.z(), 0, 0, 0);
            if (speck == null) continue;
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
