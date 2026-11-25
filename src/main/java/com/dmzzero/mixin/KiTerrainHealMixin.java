package com.dmzzero.mixin;

import com.dmzzero.heal.HealBlame;
import com.dmzzero.heal.WorldHealing;
import com.dragonminez.common.init.MainGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

// dmz's craters, cuts and ki spheres all carve the ground here, a few hundred blocks a tick; each one is remembered so it can heal,
// with the player whose attack it was
@Mixin(targets = "com.dragonminez.common.combat.util.KiTerrainDestruction$Job", remap = false)
public abstract class KiTerrainHealMixin {
    @Shadow
    @Final
    private MainGameRules.KiGriefGate gate;
    // looked up with the first block, since a job carves for many ticks and the shooter may log off meanwhile
    @Unique
    private UUID dmzzero$by;
    @Unique
    private boolean dmzzero$byKnown;

    @Redirect(method = "visitSection", require = 0, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean dmzzero$remember(ServerLevel level, BlockPos pos, BlockState state, int flags) {
        BlockState before = level.getBlockState(pos);
        boolean changed = level.setBlock(pos, state, flags);
        if (changed) WorldHealing.record(level, pos, before, state, true, dmzzero$by());
        return changed;
    }

    @Unique
    private UUID dmzzero$by() {
        if (!dmzzero$byKnown) {
            dmzzero$by = HealBlame.playerBehind(((KiGriefGateAccess) (Object) gate).dmzzero$source());
            dmzzero$byKnown = true;
        }
        return dmzzero$by;
    }
}
