package com.dmzzero.mixin;

import com.dmzzero.transmission.Shoulders;
import com.dragonminez.common.network.C2S.InstantTransmissionTravelToPlayerC2S;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// dmz's instant transmission to another player: whoever holds on to the traveller's shoulder goes along.
// dmz's teleportTo(traveller, target) plays the departure effect before moving the traveller and the arrival one after
@Mixin(value = InstantTransmissionTravelToPlayerC2S.class, remap = false)
public abstract class InstantTransmissionToPlayerMixin {

    @Inject(method = "teleportTo", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/common/util/ITTeleportHelper;departVfx(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/sounds/SoundEvent;)V"))
    private void dmzzero$depart(ServerPlayer traveller, ServerPlayer target, CallbackInfo ci) {
        Shoulders.depart(traveller);
    }

    @Inject(method = "teleportTo", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/common/util/ITTeleportHelper;arriveVfx(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/sounds/SoundEvent;)V"))
    private void dmzzero$arrive(ServerPlayer traveller, ServerPlayer target, CallbackInfo ci) {
        Shoulders.arrive(traveller);
    }
}
