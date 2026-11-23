package com.dmzzero.network;

import com.dmzzero.magic.Magic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// a glind pressed dmz's Action key (cast), let go of it (release) or pressed Second Function (next spell) in Racial
// mode. Magic.request rechecks it all
public record SpellC2S(int action) {

    public static void encode(SpellC2S msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action);
    }

    public static SpellC2S decode(FriendlyByteBuf buf) {
        return new SpellC2S(buf.readByte());
    }

    public static void handle(SpellC2S msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sender = c.getSender();
            if (sender != null) Magic.request(sender, msg.action);
        });
        c.setPacketHandled(true);
    }
}
