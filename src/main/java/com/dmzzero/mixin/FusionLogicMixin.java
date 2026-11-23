package com.dmzzero.mixin;

import com.dmzzero.fusion.FusionMoments;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.server.util.FusionLogic;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// before dmz clears who was fused with whom
@Mixin(value = FusionLogic.class, remap = false)
public abstract class FusionLogicMixin {
    @Inject(method = "endFusion", at = @At("HEAD"), require = 0)
    private static void dmzzero$split(ServerPlayer player, StatsData data, boolean forcedByDeath, CallbackInfo ci) {
        FusionMoments.splitting(player, data);
    }
}
