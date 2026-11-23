package com.dmzzero.fusion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

// which pairs a character has fused with (and split from) before; PlayerPersisted.dmzzero.fusions
public final class FusionRecord {
    public static final String FUSED = "fused";
    public static final String SPLIT = "split";
    public static final int LIMIT = 64;
    private static final String ROOT = "dmzzero";
    private static final String FUSIONS = "fusions";

    private FusionRecord() {
    }

    public static String pair(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    // true the first time, and records it
    public static boolean first(CompoundTag persisted, String kind, String key) {
        CompoundTag root = persisted.getCompound(ROOT);
        CompoundTag fusions = root.getCompound(FUSIONS);
        ListTag list = fusions.getList(kind, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(key)) return false;
        }
        if (list.size() >= LIMIT) return false;
        list.add(StringTag.valueOf(key));
        fusions.put(kind, list);
        root.put(FUSIONS, fusions);
        persisted.put(ROOT, root);
        return true;
    }

    public static int count(CompoundTag persisted, String kind) {
        return persisted.getCompound(ROOT).getCompound(FUSIONS).getList(kind, Tag.TAG_STRING).size();
    }

    public static String name(String fusionName, String a, String b) {
        return fusionName == null || fusionName.isBlank() ? a + " + " + b : fusionName;
    }
}
