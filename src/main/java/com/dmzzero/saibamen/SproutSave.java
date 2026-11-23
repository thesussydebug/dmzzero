package com.dmzzero.saibamen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// per dimension: saibaman seeds in the ground, waiting to climb out (data/dmzzero_saibamen.dat)
public final class SproutSave extends SavedData {
    private static final String NAME = "dmzzero_saibamen";
    public static final int MAX = 64;
    private final List<Sprout> sprouts = new ArrayList<>();

    public record Sprout(long soil, UUID owner, long readyAt) {
    }

    public static SproutSave get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SproutSave::load, SproutSave::new, NAME);
    }

    public static SproutSave load(CompoundTag tag) {
        SproutSave s = new SproutSave();
        ListTag list = tag.getList("sprouts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && s.sprouts.size() < MAX; i++) {
            CompoundTag c = list.getCompound(i);
            if (c.hasUUID("owner")) s.sprouts.add(new Sprout(c.getLong("soil"), c.getUUID("owner"), c.getLong("ready")));
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Sprout s : sprouts) {
            CompoundTag c = new CompoundTag();
            c.putLong("soil", s.soil());
            c.putUUID("owner", s.owner());
            c.putLong("ready", s.readyAt());
            list.add(c);
        }
        tag.put("sprouts", list);
        return tag;
    }

    public List<Sprout> all() {
        return List.copyOf(sprouts);
    }

    public boolean has(long soil) {
        return sprouts.stream().anyMatch(s -> s.soil() == soil);
    }

    public int owned(UUID owner) {
        return (int) sprouts.stream().filter(s -> s.owner().equals(owner)).count();
    }

    public boolean add(Sprout s) {
        if (sprouts.size() >= MAX || has(s.soil())) return false;
        sprouts.add(s);
        setDirty();
        return true;
    }

    public void remove(Sprout s) {
        if (sprouts.remove(s)) setDirty();
    }

    public int size() {
        return sprouts.size();
    }
}
