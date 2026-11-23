package com.dmzzero.mixin;

import com.dmzzero.realm.NullRealm;
import com.dragonminez.common.init.MainGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// the null realm's stage is rebuilt for every battle, so ki can break it whatever the gamerule says (and nothing else there)
@Mixin(value = MainGameRules.class, remap = false)
public abstract class KiGriefMixin {

    @Inject(method = "canKiGrief", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dmzzero$stageBreaks(Level level, BlockPos pos, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (level != null && pos != null && level.dimension().equals(NullRealm.KEY)) cir.setReturnValue(NullRealm.griefable(pos));
    }
}
