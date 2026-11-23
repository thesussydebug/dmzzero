package com.dmzzero.network;

import com.dmzzero.moon.MoonState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// whether the moon of the level you're in is gone; blast when it was destroyed just now, so it blows up on your screen
public record MoonS2C(boolean gone, boolean blast) {

    public static void encode(MoonS2C msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.gone);
        buf.writeBoolean(msg.blast);
    }

    public static MoonS2C decode(FriendlyByteBuf buf) {
        return new MoonS2C(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(MoonS2C msg, Supplier<NetworkEvent.Context> ctx) {
        MoonState.setClient(msg.gone);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.dmzzero.client.MoonClient.received(msg.gone, msg.blast));
        ctx.get().setPacketHandled(true);
    }
}
