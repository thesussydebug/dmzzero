package com.dmzzero.mixin.client;

import com.dmzzero.light.AuraLight;
import com.dmzzero.light.AuraLightClient;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// the light chunks, particles and block entities are drawn with; embeddium's chunk builder asks here too
@Mixin(LevelRenderer.class)
public abstract class LevelRendererLightMixin {

    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void dmzzero$auraLight(BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int light = AuraLightClient.lightAt(pos.getX(), pos.getY(), pos.getZ());
        if (light > 0) cir.setReturnValue(AuraLight.brighten(cir.getReturnValueI(), light));
    }
}
