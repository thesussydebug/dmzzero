package com.dmzzero.flight;

import com.dmzzero.ZeroConfig;
import com.dmzzero.heal.WorldHealing;
import com.dmzzero.impact.ImpactWorld;
import com.dragonminez.common.init.MainGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.List;

// fast flight breaks the leaves and glass ahead, so a flyer goes through a tree instead of stopping dead against it.
// dmz also hurts a flyer who hits something side-on at speed, and a cleared path spares them that
public final class ThroughTrees {
    // a little wider than the player, so clipping a branch with a shoulder doesn't stop them
    private static final double HALF_WIDTH = 0.45;
    private static final int MAX_CELLS = 160;
    private static final int MAX_BREAKS = 24;
    private static int broken;

    private ThroughTrees() {
    }

    public static boolean breaks(BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        return state.is(BlockTags.LEAVES) || state.is(Tags.Blocks.GLASS) || state.is(Tags.Blocks.GLASS_PANES);
    }

    public static int clear(ServerLevel level, ServerPlayer player, Vec3 at, double[] dir, double speed) {
        if (!ImpactWorld.destructionAllowed(player)) return 0;
        double length = PathMath.length(speed, PathMath.leadTicks(ZeroConfig.SERVER.throughAhead.get(), player.latency));
        List<int[]> cells = PathMath.cells(at.x, at.y, at.z, dir[0], dir[1], dir[2], length, HALF_WIDTH, player.getBbHeight(), MAX_CELLS);
        int n = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int[] c : cells) {
            if (n >= MAX_BREAKS) break;
            pos.set(c[0], c[1], c[2]);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!breaks(state) || !MainGameRules.canKiGrief(level, pos, player)) continue;
            BlockPos hit = pos.immutable();
            if (level.destroyBlock(hit, false, player)) {
                WorldHealing.record(level, hit, state, level.getBlockState(hit), false);
                n++;
            }
        }
        broken += n;
        return n;
    }

    public static int broken() {
        return broken;
    }
}
