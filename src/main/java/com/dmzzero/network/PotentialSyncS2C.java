package com.dmzzero.network;

import com.dmzzero.potential.ClientPotential;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PotentialSyncS2C(int potential) {

    public static void encode(PotentialSyncS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.potential);
    }

    public static PotentialSyncS2C decode(FriendlyByteBuf buf) {
        return new PotentialSyncS2C(buf.readVarInt());
    }

    public static void handle(PotentialSyncS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ClientPotential.set(msg.potential);
        ctx.get().setPacketHandled(true);
    }
}
