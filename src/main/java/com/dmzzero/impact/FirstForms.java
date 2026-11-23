package com.dmzzero.impact;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

// forms a character has reached, so the first time can be made a moment
public final class FirstForms {
    private static final String ROOT = "dmzzero";
    private static final String FORMS = "forms";

    private FirstForms() {
    }

    public static String key(String group, String form, boolean stack) {
        return (stack ? "s:" : "f:") + lower(group) + ":" + lower(form);
    }

    // mastery above zero means the character was in this form before dmz zero kept track
    public static boolean isFirst(CompoundTag persisted, String key, double masteryBefore) {
        return masteryBefore <= 0 && !seen(persisted, key);
    }

    public static boolean seen(CompoundTag persisted, String key) {
        ListTag list = persisted.getCompound(ROOT).getList(FORMS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (list.getString(i).equals(key)) return true;
        return false;
    }

    public static void mark(CompoundTag persisted, String key) {
        if (seen(persisted, key)) return;
        CompoundTag root = persisted.getCompound(ROOT);
        ListTag list = root.getList(FORMS, Tag.TAG_STRING);
        list.add(StringTag.valueOf(key));
        root.put(FORMS, list);
        persisted.put(ROOT, root);
    }

    public static void forget(CompoundTag persisted, String key) {
        CompoundTag root = persisted.getCompound(ROOT);
        ListTag list = root.getList(FORMS, Tag.TAG_STRING);
        list.removeIf(t -> t.getAsString().equals(key));
        root.put(FORMS, list);
        persisted.put(ROOT, root);
    }

    public static int count(CompoundTag persisted) {
        return persisted.getCompound(ROOT).getList(FORMS, Tag.TAG_STRING).size();
    }

    public static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static void save(Player player, CompoundTag persisted) {
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
