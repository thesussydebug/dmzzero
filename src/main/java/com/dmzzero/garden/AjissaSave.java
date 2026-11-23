package com.dmzzero.garden;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

// per dimension: ajissa saplings namekians planted, when each grows, and when to stop trying (data/dmzzero_ajissa.dat)
public final class AjissaSave extends SavedData {
    private static final String NAME = "dmzzero_ajissa";
    public static final int MAX = 256;
    private final List<Sapling> saplings = new ArrayList<>();

    public record Sapling(long pos, long due, long until) {
    }

    public static AjissaSave get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(AjissaSave::load, AjissaSave::new, NAME);
    }

    public static AjissaSave load(CompoundTag tag) {
        AjissaSave s = new AjissaSave();
        ListTag list = tag.getList("saplings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && s.saplings.size() < MAX; i++) {
            CompoundTag c = list.getCompound(i);
            s.saplings.add(new Sapling(c.getLong("pos"), c.getLong("due"), c.getLong("until")));
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Sapling s : saplings) {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", s.pos());
            c.putLong("due", s.due());
            c.putLong("until", s.until());
            list.add(c);
        }
        tag.put("saplings", list);
        return tag;
    }

    public List<Sapling> all() {
        return List.copyOf(saplings);
    }

    public boolean has(long pos) {
        for (Sapling s : saplings) if (s.pos() == pos) return true;
        return false;
    }

    public boolean add(Sapling s) {
        if (saplings.size() >= MAX || has(s.pos())) return false;
        saplings.add(s);
        setDirty();
        return true;
    }

    public void forget(long pos) {
        if (saplings.removeIf(s -> s.pos() == pos)) setDirty();
    }

    public void setDue(long pos, long due) {
        for (int i = 0; i < saplings.size(); i++) {
            if (saplings.get(i).pos() != pos) continue;
            saplings.set(i, new Sapling(pos, due, saplings.get(i).until()));
            setDirty();
            return;
        }
    }

    public int size() {
        return saplings.size();
    }
}
