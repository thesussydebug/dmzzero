package com.dmzzero.garden;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// per dimension: planted trees of might (data/dmzzero_might_trees.dat): where, whose, when planted, the stage shown, when
// it last bore fruit, and the grass it drained, so that grass can come back when it's cut down
public final class MightTreeSave extends SavedData {
    private static final String NAME = "dmzzero_might_trees";
    public static final int MAX = 32;
    private final List<Tree> trees = new ArrayList<>();

    public record Tree(long base, UUID owner, long planted, int stage, long lastFruit, List<Long> drained) {
    }

    public static MightTreeSave get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MightTreeSave::load, MightTreeSave::new, NAME);
    }

    public static MightTreeSave load(CompoundTag tag) {
        MightTreeSave s = new MightTreeSave();
        ListTag list = tag.getList("trees", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && s.trees.size() < MAX; i++) {
            CompoundTag c = list.getCompound(i);
            if (!c.hasUUID("owner")) continue;
            List<Long> drained = new ArrayList<>();
            for (long p : c.getLongArray("drained")) drained.add(p);
            s.trees.add(new Tree(c.getLong("base"), c.getUUID("owner"), c.getLong("planted"), c.getInt("stage"), c.getLong("fruit"), drained));
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Tree t : trees) {
            CompoundTag c = new CompoundTag();
            c.putLong("base", t.base());
            c.putUUID("owner", t.owner());
            c.putLong("planted", t.planted());
            c.putInt("stage", t.stage());
            c.putLong("fruit", t.lastFruit());
            c.put("drained", new LongArrayTag(t.drained()));
            list.add(c);
        }
        tag.put("trees", list);
        return tag;
    }

    public List<Tree> all() {
        return List.copyOf(trees);
    }

    public Tree find(long base) {
        for (Tree t : trees) if (t.base() == base) return t;
        return null;
    }

    public boolean has(long base) {
        return find(base) != null;
    }

    public int owned(UUID owner) {
        int n = 0;
        for (Tree t : trees) if (t.owner().equals(owner)) n++;
        return n;
    }

    public boolean add(Tree t) {
        if (trees.size() >= MAX || has(t.base())) return false;
        trees.add(t);
        setDirty();
        return true;
    }

    /** Replaces the tree at the same base. */
    public void put(Tree t) {
        for (int i = 0; i < trees.size(); i++) {
            if (trees.get(i).base() != t.base()) continue;
            trees.set(i, t);
            setDirty();
            return;
        }
    }

    public void forget(long base) {
        if (trees.removeIf(t -> t.base() == base)) setDirty();
    }

    /** Self-test only: move a tree's clocks. */
    public void setPlanted(long base, long planted) {
        Tree t = find(base);
        if (t != null) put(new Tree(base, t.owner(), planted, t.stage(), t.lastFruit(), t.drained()));
    }

    public void setLastFruit(long base, long lastFruit) {
        Tree t = find(base);
        if (t != null) put(new Tree(base, t.owner(), t.planted(), t.stage(), lastFruit, t.drained()));
    }

    public int size() {
        return trees.size();
    }
}
