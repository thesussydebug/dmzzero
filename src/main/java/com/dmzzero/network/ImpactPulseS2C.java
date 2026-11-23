package com.dmzzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// a jolt felt around a spot: camera shake fading with distance, an optional flash and a zoom kick
public record ImpactPulseS2C(double x, double y, double z, float shake, float range, int flashColor, float flash, float fovKick) {

    public static void encode(ImpactPulseS2C msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.shake);
        buf.writeFloat(msg.range);
        buf.writeInt(msg.flashColor);
        buf.writeFloat(msg.flash);
        buf.writeFloat(msg.fovKick);
    }

    public static ImpactPulseS2C decode(FriendlyByteBuf buf) {
        return new ImpactPulseS2C(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                buf.readInt(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(ImpactPulseS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.dmzzero.client.ImpactClient.pulse(msg));
        ctx.get().setPacketHandled(true);
    }
}
