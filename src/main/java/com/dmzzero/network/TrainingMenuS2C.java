package com.dmzzero.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// the time chamber training menu: what a player can refight, as ids, lang keys and numbers. the client builds the words
public record TrainingMenuS2C(int out, int max, List<Section> sections) {
    public static final int SAGA = 0;
    public static final int SIDE = 1;
    public static final int MOBS = 2;
    private static final int MAX_SECTIONS = 64;
    // a finished story has about 356 saga and side-quest bosses, then up to 48 beaten mobs
    public static final int MAX_FOES = 512;
    private static final int TEXT = 256;
    // minecraft refuses a mod packet over 1 MiB
    private static final int BUDGET = 900_000;

    /** {@code name}: the saga's name (a lang key or plain text) for {@link #SAGA}, empty otherwise. */
    public record Section(int kind, String name, List<Foe> foes) {
    }

    /** {@code entity}: the entity type's lang key. {@code quest}: the quest title for a boss, empty for a beaten mob. */
    public record Foe(String id, String entity, int count, String quest, long health, int times) {
    }

    public static void encode(TrainingMenuS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.out);
        buf.writeVarInt(msg.max);
        int budget = BUDGET, foes = 0;
        List<Section> sent = new ArrayList<>();
        for (Section s : msg.sections) {
            if (sent.size() >= MAX_SECTIONS) break;
            budget -= size(s.name) + 8;
            List<Foe> kept = new ArrayList<>();
            for (Foe f : s.foes) {
                int cost = size(f.id) + size(f.entity) + size(f.quest) + 20;
                if (foes >= MAX_FOES || cost > budget) break;
                budget -= cost;
                foes++;
                kept.add(f);
            }
            sent.add(new Section(s.kind, s.name, kept));
        }
        buf.writeVarInt(sent.size());
        for (Section s : sent) {
            buf.writeVarInt(s.kind);
            buf.writeUtf(clip(s.name), TEXT);
            buf.writeVarInt(s.foes.size());
            for (Foe f : s.foes) {
                buf.writeUtf(clip(f.id), TEXT);
                buf.writeUtf(clip(f.entity), TEXT);
                buf.writeVarInt(Math.max(1, f.count));
                buf.writeUtf(clip(f.quest), TEXT);
                buf.writeVarLong(Math.max(0, f.health));
                buf.writeVarInt(Math.max(0, f.times));
            }
        }
    }

    public static TrainingMenuS2C decode(FriendlyByteBuf buf) {
        int out = buf.readVarInt(), max = buf.readVarInt();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_SECTIONS) throw new IllegalArgumentException("training menu with " + n + " sections");
        List<Section> sections = new ArrayList<>(n);
        int total = 0;
        for (int i = 0; i < n; i++) {
            int kind = buf.readVarInt();
            String name = buf.readUtf(TEXT);
            int m = buf.readVarInt();
            total += m;
            if (m < 0 || total > MAX_FOES) throw new IllegalArgumentException("training menu with over " + MAX_FOES + " opponents");
            List<Foe> foes = new ArrayList<>(m);
            for (int j = 0; j < m; j++) {
                foes.add(new Foe(buf.readUtf(TEXT), buf.readUtf(TEXT), buf.readVarInt(), buf.readUtf(TEXT), buf.readVarLong(), buf.readVarInt()));
            }
            sections.add(new Section(kind, name, List.copyOf(foes)));
        }
        return new TrainingMenuS2C(out, max, List.copyOf(sections));
    }

    public static void handle(TrainingMenuS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.dmzzero.client.TrainingScreen.show(msg));
        ctx.get().setPacketHandled(true);
    }

    private static String clip(String s) {
        return s == null ? "" : s.length() > TEXT ? s.substring(0, TEXT) : s;
    }

    private static int size(String s) {
        return clip(s).getBytes(StandardCharsets.UTF_8).length + 3;
    }
}
