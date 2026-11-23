package com.dmzzero.mixin.client;

import com.dmzzero.client.KiShareTint;
import com.dmzzero.kishare.StreamMath;
import com.dragonminez.client.render.effects.AuraRenderer;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// a player receiving ki wears a little of each donor's color in their aura
@Mixin(value = AuraRenderer.class, remap = false)
public abstract class AuraLayerTintMixin {
    @Inject(method = "getAuraLayers", at = @At("RETURN"), require = 0)
    private static void dmzzero$tint(Player player, StatsData stats, float partialTick, boolean use3D,
                                     CallbackInfoReturnable<List<AuraRenderer.AuraLayer>> cir) {
        float[][] donors = KiShareTint.tintFor(player.getId());
        if (donors == null || cir.getReturnValue() == null) return;
        float[] last = null;
        for (AuraRenderer.AuraLayer layer : cir.getReturnValue()) {
            if (layer.color == null || layer.color.length < 3) continue;
            layer.color = StreamMath.tint(layer.color, donors);
            last = layer.color;
        }
        if (last != null) KiShareTint.tinted(last);
    }
}
