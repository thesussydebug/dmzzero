package com.dmzzero.sense;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// players whose ki you know from fighting them; PlayerPersisted.dmzzero.knownKi, oldest first
public final class KnownKi {
    public static final int LIMIT = 64;
    private static final String ROOT = "dmzzero";
    private static final String LIST = "knownKi";
    private static final String ID = "id";
    private static final String NAME = "name";

    private KnownKi() {
    }

    private static ListTag list(CompoundTag persisted) {
        return persisted.getCompound(ROOT).getList(LIST, Tag.TAG_COMPOUND);
    }

    private static int find(ListTag list, UUID id) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (e.hasUUID(ID) && e.getUUID(ID).equals(id)) return i;
        }
        return -1;
    }

    public static boolean knows(CompoundTag persisted, UUID id) {
        return find(list(persisted), id) >= 0;
    }

    public static String name(CompoundTag persisted, UUID id) {
        ListTag list = list(persisted);
        int i = find(list, id);
        return i < 0 ? null : list.getCompound(i).getString(NAME);
    }

    public static int count(CompoundTag persisted) {
        return list(persisted).size();
    }

    // the names as last seen, oldest first
    public static List<String> names(CompoundTag persisted) {
        ListTag list = list(persisted);
        List<String> names = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) names.add(list.getCompound(i).getString(NAME));
        return names;
    }

    // true when something changed
    public static boolean learn(CompoundTag persisted, UUID id, String name) {
        CompoundTag root = persisted.getCompound(ROOT);
        ListTag list = root.getList(LIST, Tag.TAG_COMPOUND);
        int i = find(list, id);
        if (i >= 0) {
            CompoundTag e = list.getCompound(i);
            if (e.getString(NAME).equals(name)) return false;
            e.putString(NAME, name);
        } else {
            CompoundTag e = new CompoundTag();
            e.putUUID(ID, id);
            e.putString(NAME, name);
            list.add(e);
            while (list.size() > LIMIT) list.remove(0);
        }
        root.put(LIST, list);
        persisted.put(ROOT, root);
        return true;
    }

    public static boolean knows(Player knower, UUID id) {
        return knows(knower.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG), id);
    }

    public static boolean learn(Player knower, Player known) {
        CompoundTag data = knower.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (!learn(persisted, known.getUUID(), known.getGameProfile().getName())) return false;
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        return true;
    }
}
