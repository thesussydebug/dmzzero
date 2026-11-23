package com.dmzzero.mixin;

import com.dmzzero.potential.Awakening;
import com.dragonminez.common.network.C2S.NPCActionC2S;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// the masters who draw out hidden power, after dmz has done its part of the service
@Mixin(value = NPCActionC2S.class, remap = false)
public abstract class NpcActionMixin {

    @Inject(method = "handleGuru", at = @At("RETURN"))
    private static void dmzzero$guru(StatsData data, int action, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && data.getPlayer() instanceof ServerPlayer player) Awakening.awaken(player, "guru");
    }

    @Inject(method = "handleBabidi", at = @At("RETURN"))
    private static void dmzzero$babidi(ServerPlayer player, StatsData data, int action, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) Awakening.awaken(player, "babidi");
    }

    @Inject(method = "grantOldKaiChallengeReward", at = @At("HEAD"))
    private static void dmzzero$oldKai(ServerPlayer player, StatsData data, CallbackInfo ci) {
        if (NPCActionC2S.meetsOldKaiRequirements(data)) Awakening.awaken(player, "oldkai");
    }
}
