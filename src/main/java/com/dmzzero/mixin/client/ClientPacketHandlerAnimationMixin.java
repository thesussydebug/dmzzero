package com.dmzzero.mixin.client;

import com.dmzzero.client.ChargePoseWatch;
import com.dragonminez.common.network.ClientPacketHandler;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

// the charge pose watch notes dmz's last play or stop animation packet about the local player
@Mixin(value = ClientPacketHandler.class, remap = false)
public abstract class ClientPacketHandlerAnimationMixin {

    @Inject(method = "handleTriggerAnimationPacket", at = @At("HEAD"), require = 0)
    private static void dmzzero$noteAnimation(UUID player, TriggerAnimationS2C.AnimationType type, int arg, int duration, String name, CallbackInfo ci) {
        ChargePoseWatch.packet(player, type == null ? "?" : type.name(), arg, name);
    }
}
