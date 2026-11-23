package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath;
import com.dmzzero.beam.BeamWakeMath;
import com.dmzzero.flight.FlightImpacts;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// a beam fired low over the ground stirs it up along its path: spray over water, grass and leaves, dust or snow, thrown out to both sides.
// only on this screen; the ground isn't changed
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class BeamWakes {
    private static final double RANGE = 80;
    private static final double SPACING = 3;
    private static final int MAX_SAMPLES = 32;
    // the most particles the wakes add in one tick, whatever is firing
    private static final int BUDGET = 160;
    private static final List<AbstractKiProjectile> BEAMS = new ArrayList<>();
    private static long ticks;
    private static int stirred;

    private BeamWakes() {
    }

    private static boolean enabled() {
        return ZeroConfig.read(ZeroConfig.CLIENT.beamWake, true);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof KiWaveEntity || event.getEntity() instanceof KiLaserEntity) BEAMS.add((AbstractKiProjectile) event.getEntity());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) BEAMS.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        ticks++;
        Iterator<AbstractKiProjectile> it = BEAMS.iterator();
        int budget = BUDGET;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        boolean on = enabled();
        while (it.hasNext()) {
            AbstractKiProjectile beam = it.next();
            if (beam.isRemoved() || beam.level() != mc.level) {
                it.remove();
                continue;
            }
            if (!on || budget <= 0) continue;
            budget -= stir(mc.level, beam, eye, budget);
        }
    }

    // every other spot each tick, the rest the next one
    private static int stir(ClientLevel level, AbstractKiProjectile beam, Vec3 eye, int budget) {
        BeamMath.V dir = BeamMath.direction(beam.getClashPitch(), beam.getClashYaw());
        if (!BeamWakeMath.skims(dir.y())) return 0;
        double length = beam.getClashBeamLength();
        int samples = BeamWakeMath.samples(length, SPACING, MAX_SAMPLES);
        if (samples == 0) return 0;
        double spacing = Math.max(SPACING, (length - BeamWakeMath.START) / samples);
        float size = beam.getSize();
        double reach = BeamWakeMath.reach(size);
        double[] side = BeamWakeMath.side(dir.x(), dir.z());
        RandomSource random = level.random;
        int used = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 1 + (int) (ticks % 2); i <= samples && used < budget; i += 2) {
            double along = BeamWakeMath.sampleAt(i, spacing);
            double x = beam.getX() + dir.x() * along, y = beam.getY() + dir.y() * along, z = beam.getZ() + dir.z() * along;
            if ((x - eye.x) * (x - eye.x) + (z - eye.z) * (z - eye.z) > RANGE * RANGE) continue;
            pos.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
            for (int down = 0; down <= reach + 1; down++, pos.move(0, -1, 0)) {
                if (!level.isLoaded(pos)) break;
                BlockState state = level.getBlockState(pos);
                boolean water = level.getFluidState(pos).is(FluidTags.WATER);
                if (state.isAir() && !water) continue;
                double top = water ? pos.getY() + 0.9 : pos.getY() + Math.max(0.1, state.getShape(level, pos).max(Direction.Axis.Y));
                float strength = BeamWakeMath.strength(y - top, size);
                if (strength > 0.05f) used += spray(level, random, FlightImpacts.surface(state, water), state, x, top, z, side, dir, strength);
                break;
            }
        }
        stirred += used;
        return used;
    }

    private static int spray(ClientLevel level, RandomSource random, FlightImpacts.Surface surface, BlockState state, double x, double y, double z,
                             double[] side, BeamMath.V dir, float strength) {
        ParticleOptions bits = switch (surface) {
            case WATER -> ParticleTypes.SPLASH;
            case SNOW -> ParticleTypes.SNOWFLAKE;
            case PLANTS, LEAVES, SAND -> new BlockParticleOption(ParticleTypes.BLOCK, state);
            case GROUND -> MainParticles.DUST.get();
        };
        int n = 2 + Math.round(4 * strength);
        double out = 0.12 + 0.35 * strength;
        for (int k = 0; k < n; k++) {
            double s = random.nextBoolean() ? 1 : -1;
            double jitter = (random.nextDouble() - 0.5) * 1.5;
            double px = x + side[0] * s * 0.5 + dir.x() * jitter, pz = z + side[1] * s * 0.5 + dir.z() * jitter;
            level.addParticle(bits, px, y + 0.1, pz, side[0] * s * out, 0.2 + 0.35 * strength, side[1] * s * out);
        }
        int added = n;
        if (surface == FlightImpacts.Surface.WATER) {
            // the white mist is what shows against the water
            level.addParticle(ParticleTypes.CLOUD, x, y + 0.2, z, side[0] * out * 0.6, 0.08 + 0.15 * strength, side[1] * out * 0.6);
            if (random.nextFloat() < 0.5f) level.addParticle(ParticleTypes.BUBBLE_POP, x, y + 0.1, z, 0, 0.02, 0);
            added += 2;
        } else if (surface != FlightImpacts.Surface.SNOW) {
            level.addParticle(MainParticles.DUST.get(), x, y + 0.2, z, 0, 0.04, 0);
            added++;
        }
        return added;
    }

    // for the dev autotest
    public static int stirred() {
        return stirred;
    }

    public static int tracked() {
        return BEAMS.size();
    }
}
