package com.dmzzero.mixin;

import com.dmzzero.heal.WorldHealing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// dmz's craters, cuts and ki spheres all carve the ground here, a few hundred blocks a tick; each one is remembered so it can heal
@Mixin(targets = "com.dragonminez.common.combat.util.KiTerrainDestruction$Job", remap = false)
public abstract class KiTerrainHealMixin {

    @Redirect(method = "visitSection", require = 0, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean dmzzero$remember(ServerLevel level, BlockPos pos, BlockState state, int flags) {
        BlockState before = level.getBlockState(pos);
        boolean changed = level.setBlock(pos, state, flags);
        if (changed) WorldHealing.record(level, pos, before, state, true);
        return changed;
    }
}
