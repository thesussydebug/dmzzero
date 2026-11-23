package com.dmzzero.network;

import com.dmzzero.ZeroConfig;
import com.dmzzero.settings.SettingsTree;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

// the server's config/dmzzero/server.toml, on login and whenever the file reloads, so clients show what the server does
public record ServerSettingsS2C(Map<String, Object> values) {
    private static final int MAX_VALUES = 512;
    private static final int MAX_PATH = 128;
    private static final byte BOOLEAN = 0, INTEGER = 1, DOUBLE = 2;

    /** Every boolean, whole and decimal setting in the spec, by path. */
    public static ServerSettingsS2C of(ForgeConfigSpec spec) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (SettingsTree.Item item : SettingsTree.of(spec)) {
            Object v = item.value().get();
            if (v instanceof Boolean || v instanceof Integer || v instanceof Double) values.put(item.path(), v);
        }
        return new ServerSettingsS2C(values);
    }

    public static void encode(ServerSettingsS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.values.size());
        msg.values.forEach((path, v) -> {
            buf.writeUtf(path, MAX_PATH);
            if (v instanceof Boolean b) {
                buf.writeByte(BOOLEAN);
                buf.writeBoolean(b);
            } else if (v instanceof Integer i) {
                buf.writeByte(INTEGER);
                buf.writeInt(i);
            } else {
                buf.writeByte(DOUBLE);
                buf.writeDouble(((Number) v).doubleValue());
            }
        });
    }

    public static ServerSettingsS2C decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_VALUES) throw new DecoderException("too many dmzzero settings: " + count);
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String path = buf.readUtf(MAX_PATH);
            byte type = buf.readByte();
            Object v = switch (type) {
                case BOOLEAN -> buf.readBoolean();
                case INTEGER -> buf.readInt();
                case DOUBLE -> buf.readDouble();
                default -> throw new DecoderException("unknown dmzzero setting type " + type);
            };
            values.put(path, v);
        }
        return new ServerSettingsS2C(values);
    }

    // in singleplayer and when hosting, the client already reads the same file
    public static void handle(ServerSettingsS2C msg, Supplier<NetworkEvent.Context> ctx) {
        boolean sameProcess = ctx.get().getNetworkManager().isMemoryConnection();
        if (!sameProcess) ctx.get().enqueueWork(() -> ZeroConfig.useServerValues(msg.values));
        ctx.get().setPacketHandled(true);
    }
}
