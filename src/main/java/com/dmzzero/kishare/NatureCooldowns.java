package com.dmzzero.kishare;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// per dimension: where nature has already given, so it isn't drained twice in a row
public final class NatureCooldowns extends SavedData {
    private static final String NAME = "dmzzero_nature";
    private final NatureMath.AreaCooldowns areas;

    private NatureCooldowns(NatureMath.AreaCooldowns areas) {
        this.areas = areas;
    }

    public static NatureCooldowns get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> new NatureCooldowns(NatureMath.AreaCooldowns.load(tag)),
                () -> new NatureCooldowns(new NatureMath.AreaCooldowns()), NAME);
    }

    public long remaining(BlockPos pos, long now, int radius) {
        return areas.remaining(pos.getX(), pos.getZ(), now, radius);
    }

    public void mark(BlockPos pos, long now, long ticks) {
        areas.prune(now);
        areas.mark(pos.getX(), pos.getZ(), now, ticks);
        setDirty();
    }

    // the self-test leaves no resting land behind
    public void forget(BlockPos pos) {
        areas.forget(pos.getX(), pos.getZ());
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        return areas.save(tag);
    }
}
