package com.dmzzero.mixin.client;

import com.dmzzero.client.ImpactClient;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// storm clouds around the strongest transformations, only for the client's own view of the sky
@Mixin(Level.class)
public abstract class LevelWeatherMixin {

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void dmzzero$stormRain(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!((Level) (Object) this).isClientSide) return;
        float storm = ImpactClient.storm();
        if (storm > cir.getReturnValueF()) cir.setReturnValue(storm);
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void dmzzero$stormThunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!((Level) (Object) this).isClientSide) return;
        float thunder = ImpactClient.thunder();
        if (thunder > cir.getReturnValueF()) cir.setReturnValue(thunder);
    }
}
