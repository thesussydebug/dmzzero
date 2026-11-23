package com.dmzzero.network;

import com.dmzzero.kishare.KiToss;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// ctrl+alt+left-click on a player: throw them some ki
public record KiTossC2S(int targetId) {

    public static void encode(KiTossC2S msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.targetId);
    }

    public static KiTossC2S decode(FriendlyByteBuf buf) {
        return new KiTossC2S(buf.readVarInt());
    }

    public static void handle(KiTossC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) ctx.get().enqueueWork(() -> KiToss.request(sender, msg.targetId));
        ctx.get().setPacketHandled(true);
    }
}
