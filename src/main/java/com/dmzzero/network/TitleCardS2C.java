package com.dmzzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// a title that shrinks to fit the screen, vanilla's is always four times the font size
public record TitleCardS2C(Component title, Component subtitle, int stay) {

    public static void encode(TitleCardS2C msg, FriendlyByteBuf buf) {
        buf.writeComponent(msg.title);
        buf.writeComponent(msg.subtitle);
        buf.writeVarInt(msg.stay);
    }

    public static TitleCardS2C decode(FriendlyByteBuf buf) {
        return new TitleCardS2C(buf.readComponent(), buf.readComponent(), buf.readVarInt());
    }

    public static void handle(TitleCardS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.dmzzero.client.TitleCard.show(msg));
        ctx.get().setPacketHandled(true);
    }
}
