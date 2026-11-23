package com.dmzzero.garden;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// per dimension: the senzu bushes growing there (data/dmzzero_senzu.dat). since is when it was planted or last picked;
// korin is whether korin was near when it was planted (0.17.2, missing in older saves)
public final class SenzuSave extends SavedData {
    private static final String NAME = "dmzzero_senzu";
    public static final int MAX = 64;
    private final List<Bush> bushes = new ArrayList<>();

    public record Bush(long pos, UUID owner, long since, boolean picked, boolean korin) {
    }

    public static SenzuSave get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SenzuSave::load, SenzuSave::new, NAME);
    }

    public static SenzuSave load(CompoundTag tag) {
        SenzuSave s = new SenzuSave();
        ListTag list = tag.getList("bushes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && s.bushes.size() < MAX; i++) {
            CompoundTag c = list.getCompound(i);
            if (c.hasUUID("owner")) s.bushes.add(new Bush(c.getLong("pos"), c.getUUID("owner"), c.getLong("since"), c.getBoolean("picked"),
                    c.getBoolean("korin")));
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Bush b : bushes) {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", b.pos());
            c.putUUID("owner", b.owner());
            c.putLong("since", b.since());
            c.putBoolean("picked", b.picked());
            if (b.korin()) c.putBoolean("korin", true);
            list.add(c);
        }
        tag.put("bushes", list);
        return tag;
    }

    public List<Bush> all() {
        return List.copyOf(bushes);
    }

    public Bush find(long pos) {
        for (Bush b : bushes) if (b.pos() == pos) return b;
        return null;
    }

    public boolean has(long pos) {
        return find(pos) != null;
    }

    public int owned(UUID owner) {
        int n = 0;
        for (Bush b : bushes) if (b.owner().equals(owner)) n++;
        return n;
    }

    public boolean add(Bush b) {
        if (bushes.size() >= MAX || has(b.pos())) return false;
        bushes.add(b);
        setDirty();
        return true;
    }

    /** Picked: the clock starts again from now. */
    public void picked(long pos, long now) {
        Bush b = find(pos);
        if (b == null) return;
        bushes.set(bushes.indexOf(b), new Bush(pos, b.owner(), now, true, b.korin()));
        setDirty();
    }

    public void forget(long pos) {
        if (bushes.removeIf(b -> b.pos() == pos)) setDirty();
    }

    /** Self-test only: move a bush's clock. */
    public void setSince(long pos, long since) {
        Bush b = find(pos);
        if (b == null) return;
        bushes.set(bushes.indexOf(b), new Bush(pos, b.owner(), since, b.picked(), b.korin()));
        setDirty();
    }

    public int size() {
        return bushes.size();
    }
}
