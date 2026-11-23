package com.dmzzero.flight;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.impact.ImpactWorld;
import com.dmzzero.impact.Leaves;
import com.dmzzero.network.ImpactPulseS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// flying fast and low leaves a wake, and breaking through boomSpeed goes off with a bang
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class FlightImpacts {
    private static final int COST = TickCost.id("travel.flight", () -> FlightImpacts.STATES.size());
    private static final double BOOM_RANGE = 48;
    private static final double BOOM_SHATTER = 4.0;
    private static final double BOOM_LEAVES = 8.0;
    private static final double BOW_AHEAD = 1.2;
    private static final double SERVER_LAG_TICKS = 2.0;
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static int wakes;
    private static int booms;

    public enum Surface { WATER, PLANTS, LEAVES, SAND, SNOW, GROUND }

    static final class State {
        final FlightMath.Track track = new FlightMath.Track();
        final FlightMath.Boom boom = new FlightMath.Boom();
    }

    private FlightImpacts() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        long start = TickCost.start();
        playerTick(event);
        TickCost.add(COST, start);
    }

    private static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        boolean touchdowns = Touchdowns.enabled();
        boolean flight = ZeroConfig.SERVER.wake.get() || ZeroConfig.SERVER.sonicBoom.get() || ZeroConfig.SERVER.throughTrees.get();
        if (!touchdowns && !flight) return;
        boolean skill = flySkill(player);
        long now = player.server.getTickCount();
        if (touchdowns) Touchdowns.step(player, player.position(), Touchdowns.grounded(player), skill, now);
        if (flight) step(player, player.position(), skill && airborne(player), now);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        State s = STATES.get(event.getEntity().getUUID());
        if (s != null) s.track.reset();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
    }

    private static boolean airborne(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.onGround() && !player.isInWater() && !player.isPassenger();
    }

    // dmz's flight stays switched on when a flyer touches down
    static boolean flySkill(ServerPlayer player) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        return data != null && data.getSkills().isSkillActive("fly");
    }

    // the self-test moves fake players through here by hand
    public static void step(ServerPlayer player, Vec3 at, boolean flying, long now) {
        State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        if (!flying) {
            s.track.reset();
            s.boom.update(0, 1, now);
            return;
        }
        s.track.add(at.x, at.y, at.z);
        double speed = s.track.speed();
        if (speed > peakSpeed) peakSpeed = speed;
        ServerLevel level = player.serverLevel();
        if (ZeroConfig.SERVER.sonicBoom.get() && s.boom.update(speed, ZeroConfig.SERVER.boomSpeed.get(), now)) {
            boom(level, player, at, s.track.direction(), speed);
        }
        if (ZeroConfig.SERVER.wake.get() && speed >= ZeroConfig.SERVER.wakeSpeed.get()) wake(level, at, s.track.direction(), speed);
        if (ZeroConfig.SERVER.throughTrees.get() && speed >= ZeroConfig.SERVER.throughSpeed.get()) {
            ThroughTrees.clear(level, player, at, s.track.direction(), speed);
        }
    }

    static void wake(ServerLevel level, Vec3 at, double[] dir, double speed) {
        int maxHeight = ZeroConfig.SERVER.wakeHeight.get();
        int x = Mth.floor(at.x), z = Mth.floor(at.z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, Mth.floor(at.y), z);
        for (int i = 0; i <= maxHeight + 1; i++, pos.move(0, -1, 0)) {
            if (!level.isLoaded(pos)) return;
            BlockState state = level.getBlockState(pos);
            boolean water = level.getFluidState(pos).is(FluidTags.WATER);
            if (state.isAir() && !water) continue;
            double top = water ? pos.getY() + 0.9 : pos.getY() + Math.max(0.1, state.getShape(level, pos).max(net.minecraft.core.Direction.Axis.Y));
            float strength = FlightMath.wake(speed, at.y - top, ZeroConfig.SERVER.wakeSpeed.get(), maxHeight);
            if (strength <= 0f) return;
            spray(level, surface(state, water), state, at.x, top, at.z, dir, strength, speed);
            wakes++;
            return;
        }
    }

    public static Surface surface(BlockState state, boolean water) {
        if (water) return Surface.WATER;
        if (state.is(BlockTags.LEAVES)) return Surface.LEAVES;
        if (state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS)) {
            return state.is(Blocks.SNOW) ? Surface.SNOW : Surface.PLANTS;
        }
        if (state.is(BlockTags.SNOW) || state.is(Blocks.POWDER_SNOW)) return Surface.SNOW;
        if (state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.DIRT) && !state.is(Blocks.GRASS_BLOCK)) return Surface.SAND;
        return Surface.GROUND;
    }

    // thrown out to both sides of the flight path, the way a boat's bow wave is
    private static void spray(ServerLevel level, Surface surface, BlockState state, double x, double y, double z, double[] dir, float strength, double speed) {
        double sideX = -dir[2], sideZ = dir[0];
        double len = Math.sqrt(sideX * sideX + sideZ * sideZ);
        if (len < 1e-6) {
            sideX = 1;
            sideZ = 0;
        } else {
            sideX /= len;
            sideZ /= len;
        }
        int n = 2 + Math.round(8 * strength);
        double out = 0.15 + 0.35 * strength;
        ParticleOptions bits = switch (surface) {
            case WATER -> ParticleTypes.SPLASH;
            case SNOW -> ParticleTypes.SNOWFLAKE;
            case PLANTS, LEAVES, SAND -> new BlockParticleOption(ParticleTypes.BLOCK, state);
            case GROUND -> MainParticles.DUST.get();
        };
        // the bow wave rises a little ahead and to the sides, where the flyer can see it; the trail stays behind
        // the server sees a player a tick or two behind where their own screen shows them, so the bow wave leads by that much more
        double ahead = BOW_AHEAD + speed * SERVER_LAG_TICKS;
        double aheadX = x + dir[0] * ahead, aheadZ = z + dir[2] * ahead;
        for (int side = -1; side <= 1; side += 2) {
            double px = aheadX + sideX * side * 0.6, pz = aheadZ + sideZ * side * 0.6;
            for (int i = 0; i < n; i++) {
                level.sendParticles(bits, px, y + 0.1, pz, 0, sideX * side * out, 0.35 + 0.35 * strength, sideZ * side * out, 1.0);
            }
        }
        if (surface == Surface.WATER) {
            // the fishing particle is vanilla's own wake line on the water
            for (int side = -1; side <= 1; side += 2) {
                level.sendParticles(ParticleTypes.FISHING, x + sideX * side * 0.4, y + 0.1, z + sideZ * side * 0.4, 0,
                        sideX * side * 0.2 - dir[0] * 0.1, 0, sideZ * side * 0.2 - dir[2] * 0.1, 1.0);
            }
            level.sendParticles(ParticleTypes.SPLASH, x - dir[0], y + 0.1, z - dir[2], n * 2, 0.5, 0.05, 0.5, 0.3 + 0.3 * strength);
            // blue drops vanish against the water, the white mist is what shows
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i < 2 + n / 2; i++) {
                    double back = level.random.nextDouble() * 1.5;
                    level.sendParticles(ParticleTypes.CLOUD, aheadX - dir[0] * back + sideX * side * 0.5, y + 0.2, aheadZ - dir[2] * back + sideZ * side * 0.5, 0,
                            sideX * side * out * 0.8, 0.12 + 0.2 * strength, sideZ * side * out * 0.8, 1.0);
                }
            }
            level.sendParticles(ParticleTypes.BUBBLE_POP, x, y + 0.1, z, 2 + n, 0.4, 0.02, 0.4, 0.02);
            if (strength > 0.5f) level.sendParticles(ParticleTypes.FALLING_WATER, x - dir[0], y + 0.8, z - dir[2], 2, 0.3, 0.2, 0.3, 0.0);
            if (level.random.nextFloat() < 0.15f * strength) {
                level.playSound(null, x, y, z, SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.25f * strength, 1.2f);
            }
        } else if (surface == Surface.LEAVES || surface == Surface.PLANTS) {
            if (level.random.nextFloat() < 0.1f * strength) {
                level.playSound(null, x, y, z, SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, 0.3f * strength, 1.3f);
            }
        } else {
            level.sendParticles(MainParticles.DUST.get(), x - dir[0], y + 0.2, z - dir[2], n, 0.4, 0.05, 0.4, 0.04);
        }
    }

    static void boom(ServerLevel level, ServerPlayer player, Vec3 at, double[] dir, double speed) {
        booms++;
        double x = at.x, y = at.y + player.getBbHeight() * 0.5, z = at.z;
        // a ring around the flight path, blown back the way the player came
        double ax = dir[0], ay = dir[1], az = dir[2];
        double ux, uy, uz;
        if (Math.abs(ay) < 0.9) {
            ux = -az;
            uy = 0;
            uz = ax;
        } else {
            ux = 1;
            uy = 0;
            uz = 0;
        }
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        double vx = ay * uz - az * uy, vy = az * ux - ax * uz, vz = ax * uy - ay * ux;
        // the vapor cone: two rings, the inner one slow so it hangs in the air behind the player
        int n = 48;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double cos = Math.cos(a), sin = Math.sin(a);
            double rx = ux * cos + vx * sin, ry = uy * cos + vy * sin, rz = uz * cos + vz * sin;
            level.sendParticles(ParticleTypes.CLOUD, x + rx * 1.2, y + ry * 1.2, z + rz * 1.2, 0,
                    rx * 0.35 - ax * 0.15, ry * 0.35 - ay * 0.15, rz * 0.35 - az * 0.15, 1.0);
            level.sendParticles(ParticleTypes.POOF, x + rx * 0.6 - ax, y + ry * 0.6 - ay, z + rz * 0.6 - az, 0,
                    rx * 0.12, ry * 0.12, rz * 0.12, 1.0);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, x - ax, y - ay, z - az, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.55f);
        level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.PLAYERS, 3.0f, 0.6f);
        ZeroNetwork.sendAround(level, at, BOOM_RANGE, new ImpactPulseS2C(x, y, z, 1.1f, (float) BOOM_RANGE, 0xFFFFFF, 0.12f, 0f));
        if (ImpactWorld.destructionAllowed(player)) ImpactWorld.shatterAll(level, player, new Vec3(x, y, z), BOOM_SHATTER, 24);
        Leaves.tear(level, new Vec3(x, y, z), BOOM_LEAVES, 1f);
        DMZZero.LOG.debug("sonic boom by {} at {} b/t", player.getGameProfile().getName(), String.format("%.2f", speed));
    }

    // the fastest anyone has flown since the last reset, for the dev tests
    private static volatile double peakSpeed;

    public static double takePeakSpeed() {
        double p = peakSpeed;
        peakSpeed = 0;
        return p;
    }

    public static int wakes() {
        return wakes;
    }

    public static int booms() {
        return booms;
    }
}
