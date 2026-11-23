package com.dmzzero.mixin;

import com.dmzzero.swat.Swats;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// every client sends a swing when its player punches, even at thin air, so swatting works without dmz zero on the client
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SwingSwatMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleAnimate", at = @At("TAIL"), require = 0)
    private void dmzzero$swat(ServerboundSwingPacket packet, CallbackInfo ci) {
        Swats.swing(player, packet.getHand());
    }
}
