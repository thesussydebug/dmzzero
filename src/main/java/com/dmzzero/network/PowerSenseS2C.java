package com.dmzzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// a far stronger power just rose at this spot
public record PowerSenseS2C(double x, double y, double z, int color, float strength) {

    public static void encode(PowerSenseS2C msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeInt(msg.color);
        buf.writeFloat(msg.strength);
    }

    public static PowerSenseS2C decode(FriendlyByteBuf buf) {
        return new PowerSenseS2C(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readFloat());
    }

    public static void handle(PowerSenseS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.dmzzero.client.SenseClient.felt(msg));
        ctx.get().setPacketHandled(true);
    }
}
