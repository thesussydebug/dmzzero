package com.dmzzero.mixin;

import com.dmzzero.heal.WorldHealing;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// the single blocks a ki attack breaks or burns away on its own are remembered for healing too.
// this hooks the start and end of dmz's two methods, not the block call inside them: overhaul redirects that call,
// and two redirects of one call can't both apply (MixinClashTest)
@Mixin(value = AbstractKiProjectile.class, remap = false)
public abstract class KiBlockHealMixin {
    @Unique
    private BlockState dmzzero$before;

    @Inject(method = "setKiBlockToAir", at = @At("HEAD"), require = 0)
    private void dmzzero$beforeAir(BlockPos pos, int flags, CallbackInfoReturnable<Boolean> cir) {
        dmzzero$before = ((Entity) (Object) this).level().getBlockState(pos);
    }

    @Inject(method = "setKiBlockToAir", at = @At("RETURN"), require = 0)
    private void dmzzero$remember(BlockPos pos, int flags, CallbackInfoReturnable<Boolean> cir) {
        dmzzero$record(pos, cir.getReturnValueZ());
    }

    // a block that will come back drops nothing, so a fight can't be farmed for blocks. with the healing list full it drops as usual
    @ModifyVariable(method = "destroyKiBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private boolean dmzzero$noDrops(boolean drop, BlockPos pos) {
        Level level = ((Entity) (Object) this).level();
        dmzzero$before = level.getBlockState(pos);
        return drop && !(level instanceof ServerLevel server && WorldHealing.willRecord(server, pos, dmzzero$before, true));
    }

    @Inject(method = "destroyKiBlock", at = @At("RETURN"), require = 0)
    private void dmzzero$rememberBroken(BlockPos pos, boolean drop, CallbackInfoReturnable<Boolean> cir) {
        dmzzero$record(pos, cir.getReturnValueZ());
    }

    // dmz's own check (canKiDestroyBlock) or overhaul's support-block protection returns false with nothing changed
    @Unique
    private void dmzzero$record(BlockPos pos, boolean changed) {
        BlockState before = dmzzero$before;
        dmzzero$before = null;
        Level level = ((Entity) (Object) this).level();
        if (changed && before != null && level instanceof ServerLevel server) {
            WorldHealing.record(server, pos, before, level.getBlockState(pos), true);
        }
    }
}
