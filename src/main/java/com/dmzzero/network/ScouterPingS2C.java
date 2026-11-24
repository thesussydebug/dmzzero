package com.dmzzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// a mark from the scouter network: someone's ping, or a power spike your scouter picked up
public record ScouterPingS2C(int kind, int channel, String sender, String label, double x, double y, double z,
                             int entityId, double bp, double from) {
    public static final int PING = 0;
    public static final int SPIKE = 1;

    public static void encode(ScouterPingS2C msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.kind);
        buf.writeByte(msg.channel);
        buf.writeUtf(msg.sender, 64);
        // a named mob's label can pass the limit, and writeUtf throws then
        buf.writeUtf(msg.label.length() > 128 ? msg.label.substring(0, 128) : msg.label, 128);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeVarInt(msg.entityId + 1);
        buf.writeDouble(msg.bp);
        buf.writeDouble(msg.from);
    }

    public static ScouterPingS2C decode(FriendlyByteBuf buf) {
        return new ScouterPingS2C(buf.readByte(), buf.readByte(), buf.readUtf(64), buf.readUtf(128), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readVarInt() - 1, buf.readDouble(), buf.readDouble());
    }

    public static void handle(ScouterPingS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.dmzzero.client.ScouterClient.received(msg));
        ctx.get().setPacketHandled(true);
    }
}
