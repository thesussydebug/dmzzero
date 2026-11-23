package com.dmzzero.mixin.client;

import com.dmzzero.client.MoonClient;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// vanilla draws the moon as a quad 20 wide either way (the only 20.0f in renderSky; the sun's is 30). a destroyed moon is drawn
// swelling for a second, then at size 0, which draws nothing
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMoonMixin {

    @ModifyConstant(method = "renderSky", constant = @Constant(floatValue = 20.0F), require = 0)
    private float dmzzero$moonSize(float size) {
        return MoonClient.moonSize(size);
    }
}
