package com.dmzzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// your own ki while ki boost holds it above full, and your normal full. dmz clamps the client's copy of ki to the
// normal full, so the hud reads this instead. energy at or under max ends it
public record KiOverchargeS2C(float energy, float max) {

    public static void encode(KiOverchargeS2C msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.energy);
        buf.writeFloat(msg.max);
    }

    public static KiOverchargeS2C decode(FriendlyByteBuf buf) {
        return new KiOverchargeS2C(buf.readFloat(), buf.readFloat());
    }

    public static void handle(KiOverchargeS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.dmzzero.client.ClientOvercharge.set(msg.energy, msg.max));
        ctx.get().setPacketHandled(true);
    }
}
