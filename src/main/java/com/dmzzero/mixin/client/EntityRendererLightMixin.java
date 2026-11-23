package com.dmzzero.mixin.client;

import com.dmzzero.light.AuraLightClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// players and mobs standing near an aura are lit by it too
@Mixin(EntityRenderer.class)
public abstract class EntityRendererLightMixin {

    @Inject(method = "getBlockLightLevel", at = @At("RETURN"), cancellable = true)
    private void dmzzero$auraLight(Entity entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int light = AuraLightClient.lightAt(pos.getX(), pos.getY(), pos.getZ());
        if (light > cir.getReturnValueI()) cir.setReturnValue(light);
    }
}
