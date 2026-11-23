package com.dmzzero.mixin;

import com.dmzzero.transmission.Shoulders;
import com.dragonminez.common.network.C2S.InstantTransmissionTravelC2S;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// dmz's instant transmission to a master: whoever holds on to the traveller's shoulder goes along.
// dmz has checked and charged the traveller by the departure effect, and moved them by the arrival one
@Mixin(value = InstantTransmissionTravelC2S.class, remap = false)
public abstract class InstantTransmissionTravelMixin {

    @Inject(method = "lambda$handle$0", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/common/util/ITTeleportHelper;departVfx(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/sounds/SoundEvent;)V"))
    private void dmzzero$depart(ServerPlayer player, StatsData data, CallbackInfo ci) {
        Shoulders.depart(player);
    }

    @Inject(method = "lambda$handle$0", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/common/util/ITTeleportHelper;arriveVfx(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/sounds/SoundEvent;)V"))
    private void dmzzero$arrive(ServerPlayer player, StatsData data, CallbackInfo ci) {
        Shoulders.arrive(player);
    }
}
