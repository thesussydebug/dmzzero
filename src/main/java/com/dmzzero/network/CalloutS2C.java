package com.dmzzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// a strike attack just landed: its name goes over the striker. strikes have no projectile to carry it, unlike ki attacks
public record CalloutS2C(int entity, String name, String technique, int color) {
    public static final int MAX_TEXT = 64;

    public static void encode(CalloutS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entity);
        buf.writeUtf(msg.name, MAX_TEXT);
        buf.writeUtf(msg.technique, MAX_TEXT);
        buf.writeInt(msg.color);
    }

    public static CalloutS2C decode(FriendlyByteBuf buf) {
        return new CalloutS2C(buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readInt());
    }

    public static void handle(CalloutS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.dmzzero.client.Callouts.strike(msg.entity, msg.name, msg.technique, msg.color));
        ctx.get().setPacketHandled(true);
    }
}
