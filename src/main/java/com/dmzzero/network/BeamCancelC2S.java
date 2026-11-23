package com.dmzzero.network;

import com.dmzzero.beam.BeamCancel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// the cancel-beam key: end my own ki wave. Carries nothing; the server finds the sender's beams itself
public record BeamCancelC2S() {

    public static void encode(BeamCancelC2S msg, FriendlyByteBuf buf) {
    }

    public static BeamCancelC2S decode(FriendlyByteBuf buf) {
        return new BeamCancelC2S();
    }

    public static void handle(BeamCancelC2S msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sender = c.getSender();
            if (sender != null) BeamCancel.request(sender);
        });
        c.setPacketHandled(true);
    }
}
