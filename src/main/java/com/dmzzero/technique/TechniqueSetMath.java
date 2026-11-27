package com.dmzzero.technique;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** Saved technique layouts, kept with the character in the order they were first saved, and how one goes back into DMZ's slots. */
public final class TechniqueSetMath {
    public static final int MAX_NAME = 16;
    private static final String ROOT = "dmzzero";
    private static final String SETS = "techniqueSets";
    private static final String LAST = "techniqueSetLast";

    private TechniqueSetMath() {
    }

    public static List<String> names(CompoundTag persisted) {
        ListTag sets = sets(persisted);
        List<String> out = new ArrayList<>(sets.size());
        for (int i = 0; i < sets.size(); i++) out.add(sets.getCompound(i).getString("name"));
        return out;
    }

    /** A saved set's technique id per slot ("" for an empty one), or null when there's no set by that name. */
    public static String[] slots(CompoundTag persisted, String name) {
        ListTag sets = sets(persisted);
        int i = indexOf(sets, name);
        if (i < 0) return null;
        ListTag saved = sets.getCompound(i).getList("slots", Tag.TAG_STRING);
        String[] out = new String[saved.size()];
        for (int s = 0; s < out.length; s++) out[s] = saved.getString(s);
        return out;
    }

    /** Saves a layout under a name, replacing a set with that name in its place. False when a new one doesn't fit. */
    public static boolean put(CompoundTag persisted, String name, String[] slots, int max) {
        ListTag sets = sets(persisted);
        int i = indexOf(sets, name);
        if (i < 0 && sets.size() >= max) return false;
        ListTag saved = new ListTag();
        for (String id : slots) saved.add(StringTag.valueOf(id == null ? "" : id));
        CompoundTag set = new CompoundTag();
        set.putString("name", name);
        set.put("slots", saved);
        if (i < 0) sets.add(set);
        else sets.set(i, set);
        store(persisted, sets);
        return true;
    }

    /** Returns the removed set's name as it was saved, or null when there's none by that name. */
    public static String remove(CompoundTag persisted, String name) {
        ListTag sets = sets(persisted);
        int i = indexOf(sets, name);
        if (i < 0) return null;
        String stored = sets.getCompound(i).getString("name");
        sets.remove(i);
        store(persisted, sets);
        return stored;
    }

    /** The set after the last one used, going round; the first one when that's gone or none was used. Null with no sets. */
    public static String next(CompoundTag persisted) {
        ListTag sets = sets(persisted);
        if (sets.isEmpty()) return null;
        int last = indexOf(sets, persisted.getCompound(ROOT).getString(LAST));
        return sets.getCompound((last + 1) % sets.size()).getString("name");
    }

    public static void used(CompoundTag persisted, String name) {
        CompoundTag root = persisted.getCompound(ROOT);
        root.putString(LAST, name);
        persisted.put(ROOT, root);
    }

    /**
     * Puts a saved layout into the slots, in place, so a rule that counts what's equipped sees it as it fills. A fixed slot
     * keeps what it holds. Returns how many of the saved techniques didn't go in where they were saved.
     */
    public static int apply(String[] slots, String[] wanted, boolean[] fixed, BiPredicate<Integer, String> allowed) {
        for (int i = 0; i < slots.length; i++) if (!fixed[i]) slots[i] = "";
        int missed = 0;
        for (int i = 0; i < slots.length && i < wanted.length; i++) {
            String id = wanted[i];
            if (id.isEmpty()) continue;
            if (!fixed[i] && !holds(slots, id) && allowed.test(i, id)) slots[i] = id;
            else if (!id.equals(slots[i])) missed++;
        }
        return missed;
    }

    private static boolean holds(String[] slots, String id) {
        for (String s : slots) if (id.equals(s)) return true;
        return false;
    }

    private static int indexOf(ListTag sets, String name) {
        for (int i = 0; i < sets.size(); i++) if (sets.getCompound(i).getString("name").equalsIgnoreCase(name)) return i;
        return -1;
    }

    private static ListTag sets(CompoundTag persisted) {
        return persisted.getCompound(ROOT).getList(SETS, Tag.TAG_COMPOUND);
    }

    private static void store(CompoundTag persisted, ListTag sets) {
        CompoundTag root = persisted.getCompound(ROOT);
        if (sets.isEmpty()) root.remove(SETS);
        else root.put(SETS, sets);
        persisted.put(ROOT, root);
    }
}
