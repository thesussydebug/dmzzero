package com.dmzzero.flight;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.flight.FlightImpacts.Surface;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.init.MainParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

// running faster than vanilla can (dmz's sprint skill, a dash, a speed boost) kicks up dust, sand, snow or bits of grass
// behind your feet. particles only, sent by the server, so players without dmz zero see them too
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class RunningDust {
    private static final int COST = TickCost.id("travel.running", () -> RunningDust.TRACKS.size());
    private static final Map<UUID, FlightMath.Track> TRACKS = new HashMap<>();
    private static int puffs;

    private RunningDust() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!ZeroConfig.SERVER.runningDust.get()) return;
        long start = TickCost.start();
        step(player, player.position(), onFoot(player), Touchdowns.grounded(player));
        TickCost.add(COST, start);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TRACKS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        TRACKS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TRACKS.clear();
    }

    private static boolean onFoot(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isInWater()
                && !player.isFallFlying() && !player.getAbilities().flying;
    }

    /** One tick of a player's running; the strength of the dust kicked up, 0 for none. The self-test calls it by hand. */
    public static float step(ServerPlayer player, Vec3 at, boolean onFoot, boolean grounded) {
        if (!onFoot) {
            TRACKS.remove(player.getUUID());
            return 0f;
        }
        FlightMath.Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new FlightMath.Track(RunMath.SPAN));
        // height is left out, so jumps and stairs don't count as speed; a sprint jump still kicks up dust where it lands
        track.add(at.x, 0, at.z);
        float strength = RunMath.strength(track.speed(), ZeroConfig.SERVER.runningSpeed.get());
        if (strength <= 0f || !grounded) return 0f;
        return kick(player.serverLevel(), at, track.direction(), strength) ? strength : 0f;
    }

    private static boolean kick(ServerLevel level, Vec3 at, double[] dir, float strength) {
        BlockPos feet = BlockPos.containing(at.x, at.y + 0.05, at.z);
        if (!level.isLoaded(feet)) return false;
        // grass or snow at the feet flies up before the block underneath does
        BlockState state = level.getBlockState(feet);
        Surface surface = state.isAir() ? null : FlightImpacts.surface(state, false);
        if (surface != Surface.PLANTS && surface != Surface.SNOW) {
            state = level.getBlockState(feet.below());
            // standing on the edge of a block, nothing is right underneath to kick up
            if (state.isAir()) return false;
            surface = FlightImpacts.surface(state, false);
        }
        ParticleOptions bits = surface == Surface.GROUND ? MainParticles.DUST.get() : new BlockParticleOption(ParticleTypes.BLOCK, state);
        double x = at.x - dir[0] * 0.4, z = at.z - dir[2] * 0.4;
        level.sendParticles(bits, x, at.y + 0.1, z, RunMath.bits(strength), 0.2, 0.05, 0.2, 0.05 + 0.15 * strength);
        puffs++;
        return true;
    }

    public static void forget(UUID id) {
        TRACKS.remove(id);
    }

    public static int puffs() {
        return puffs;
    }
}
