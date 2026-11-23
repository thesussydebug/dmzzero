package com.dmzzero.impact;

import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath.V;
import com.dmzzero.heal.WorldHealing;
import com.dragonminez.common.init.MainGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// shockwaves, sonic booms and smashes near trees tear leaves loose. the particles are vanilla's, so everyone sees them;
// only a strong form's shockwave strips the leaf blocks, and those heal like any other ki damage
public final class Leaves {
    private static final int MAX_SPOTS = 24;
    private static final int PER_SPOT = 3;
    private static final int MAX_STRIPPED = 64;

    private Leaves() {
    }

    public static int tear(ServerLevel level, Vec3 at, double radius, float strength) {
        if (!ZeroConfig.SERVER.leaves.get()) return 0;
        List<BlockPos> spots = find(level, at, radius, 2);
        int n = Math.min(MAX_SPOTS, spots.size());
        for (int i = 0; i < n; i++) {
            BlockPos pos = spots.get(i);
            BlockParticleOption leaf = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(pos));
            V vel = LeafMath.velocity(new V(pos.getX() + 0.5 - at.x, pos.getY() + 0.5 - at.y, pos.getZ() + 0.5 - at.z), radius, strength);
            for (int k = 0; k < PER_SPOT; k++) {
                level.sendParticles(leaf, pos.getX() + level.random.nextDouble(), pos.getY() + level.random.nextDouble(),
                        pos.getZ() + level.random.nextDouble(), 0, vel.x(), vel.y(), vel.z(), 1.0);
            }
        }
        if (n > 0) level.playSound(null, at.x, at.y, at.z, SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.BLOCKS, 0.6f + 0.4f * strength, 0.9f);
        return n;
    }

    public static int strip(ServerLevel level, ServerPlayer player, Vec3 at, double radius, int tier) {
        if (!ZeroConfig.SERVER.leafStrip.get() || !LeafMath.strips(tier, ZeroConfig.SERVER.leafStripTier.get())) return 0;
        int n = 0;
        for (BlockPos pos : find(level, at, radius, 1)) {
            if (n >= MAX_STRIPPED) break;
            BlockState state = level.getBlockState(pos);
            if (!MainGameRules.canKiGrief(level, pos, player)) continue;
            if (level.destroyBlock(pos, false, player)) {
                WorldHealing.record(level, pos, state, level.getBlockState(pos), false);
                n++;
            }
        }
        return n;
    }

    // closest first; never looks into unloaded chunks
    static List<BlockPos> find(ServerLevel level, Vec3 at, double radius, int step) {
        int r = LeafMath.reach(radius);
        List<BlockPos> found = new ArrayList<>();
        if (r == 0) return found;
        BlockPos center = BlockPos.containing(at);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int r2 = r * r;
        for (int dx = -r; dx <= r; dx += step) {
            for (int dy = -r; dy <= r; dy += step) {
                for (int dz = -r; dz <= r; dz += step) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.LEAVES)) found.add(pos.immutable());
                }
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(at)));
        return found;
    }
}
