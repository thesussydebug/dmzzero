package com.dmzzero.mixin.client;

import com.dmzzero.client.CloudHoles;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// wraps the call that draws the clouds. vanilla's clouds, embeddium's (it takes over renderClouds) and dmz's own dimension clouds
// (forge asks the dimension's effects inside renderClouds) all happen in that call
@Mixin(LevelRenderer.class)
public abstract class LevelRendererCloudsMixin {
    private static final String CLOUDS = "Lnet/minecraft/client/renderer/LevelRenderer;renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FDDD)V";

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = CLOUDS), require = 0)
    private void dmzzero$beforeClouds(PoseStack pose, float partialTick, long nanos, boolean outline, Camera camera, GameRenderer gameRenderer,
                                      LightTexture light, Matrix4f projection, CallbackInfo ci) {
        CloudHoles.beforeClouds(pose, camera.getPosition());
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = CLOUDS, shift = At.Shift.AFTER), require = 0)
    private void dmzzero$afterClouds(PoseStack pose, float partialTick, long nanos, boolean outline, Camera camera, GameRenderer gameRenderer,
                                     LightTexture light, Matrix4f projection, CallbackInfo ci) {
        CloudHoles.afterClouds();
    }
}
