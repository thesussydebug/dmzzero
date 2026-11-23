package com.dmzzero.moon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// per dimension: whether its moon is gone, until when, and who did it (data/dmzzero_moon.dat)
public final class MoonSave extends SavedData {
    private static final String NAME = "dmzzero_moon";
    private boolean gone;
    private long returnsAt;
    private String by = "";

    public static MoonSave get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MoonSave::load, MoonSave::new, NAME);
    }

    private static MoonSave load(CompoundTag tag) {
        MoonSave s = new MoonSave();
        s.gone = tag.getBoolean("gone");
        s.returnsAt = tag.getLong("returnsAt");
        s.by = tag.getString("by");
        return s;
    }

    public boolean gone() {
        return gone;
    }

    public long returnsAt() {
        return returnsAt;
    }

    public String by() {
        return by;
    }

    public void destroy(long returnsAt, String by) {
        this.gone = true;
        this.returnsAt = returnsAt;
        this.by = by == null ? "" : by;
        setDirty();
    }

    public void restore() {
        gone = false;
        returnsAt = 0;
        by = "";
        setDirty();
    }

    // the self-test puts back whatever was there before it
    public void set(boolean gone, long returnsAt, String by) {
        this.gone = gone;
        this.returnsAt = returnsAt;
        this.by = by == null ? "" : by;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("gone", gone);
        tag.putLong("returnsAt", returnsAt);
        tag.putString("by", by);
        return tag;
    }
}
