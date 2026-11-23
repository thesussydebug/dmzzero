package com.dmzzero.mixin.client;

import com.dmzzero.client.ImpactClient;
import com.dragonminez.client.systems.FormVisualTransition;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.FormTransition;
import com.dragonminez.common.stats.StatsData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(value = FormVisualTransition.class, remap = false)
public abstract class FormFlickerMixin {

    @Inject(method = "color", at = @At("HEAD"), require = 0)
    private static void dmzzero$remember(StatsData stats, float[] base, Function<FormConfig.FormData, float[]> rgb,
                                         CallbackInfoReturnable<float[]> cir) {
        ImpactClient.colorStats = stats;
    }

    // only the blend toward the next form, the revert fade stays smooth
    @Redirect(method = "color", at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/common/stats/FormTransition$Charge;factor()F"), require = 0)
    private static float dmzzero$flicker(FormTransition.Charge charge) {
        return ImpactClient.flicker(charge, ImpactClient.colorStats);
    }
}
