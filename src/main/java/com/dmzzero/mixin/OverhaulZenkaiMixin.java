package com.dmzzero.mixin;

import com.dmzzero.potential.ZenkaiPotential;
import com.dragonminez.common.stats.StatsData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// overhaul replaces dmz's zenkai with its own stacks; its only caller of this is the award itself
@Pseudo
@Mixin(targets = "com.dmzrevamp.racial.impl.SaiyanRpgZenkaiEvents", remap = false)
public abstract class OverhaulZenkaiMixin {

    @Inject(method = "getStatBonusPerStack", at = @At("RETURN"), cancellable = true, require = 0)
    private static void dmzzero$potential(StatsData data, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(ZenkaiPotential.scale(data, cir.getReturnValueD(), "overhaul"));
    }
}
