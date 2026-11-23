package com.dmzzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// kami's watch: the strong powers sensed in the dimension right now. An empty list ends the watch
public record KamiWatchS2C(List<Entry> entries) {
    public static final int MAX = 32;
    private static final int NAME_MAX = 16;

    public record Entry(double x, double z, int distance, int color, float strength, String name) {
    }

    public static void encode(KamiWatchS2C msg, FriendlyByteBuf buf) {
        int n = Math.min(MAX, msg.entries.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = msg.entries.get(i);
            buf.writeDouble(e.x());
            buf.writeDouble(e.z());
            buf.writeVarInt(Math.max(0, e.distance()));
            buf.writeInt(e.color());
            buf.writeFloat(e.strength());
            String name = e.name() == null ? "" : e.name();
            buf.writeUtf(name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name, NAME_MAX);
        }
    }

    public static KamiWatchS2C decode(FriendlyByteBuf buf) {
        int n = Math.min(MAX, Math.max(0, buf.readVarInt()));
        List<Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            entries.add(new Entry(buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readInt(), buf.readFloat(), buf.readUtf(NAME_MAX)));
        }
        return new KamiWatchS2C(entries);
    }

    public static void handle(KamiWatchS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.dmzzero.client.KamiWatchClient.received(msg));
        ctx.get().setPacketHandled(true);
    }
}
