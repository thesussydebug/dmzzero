package com.dmzzero.network;

import com.dmzzero.scouter.Scouters;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// the scouter ping key: mark what you're aiming at (an entity, or else the spot) for your channel
public record ScouterPingC2S(int entityId, double x, double y, double z) {

    public static void encode(ScouterPingC2S msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId + 1);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
    }

    public static ScouterPingC2S decode(FriendlyByteBuf buf) {
        return new ScouterPingC2S(buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(ScouterPingC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) ctx.get().enqueueWork(() -> Scouters.ping(sender, msg.entityId, new Vec3(msg.x, msg.y, msg.z)));
        ctx.get().setPacketHandled(true);
    }
}
