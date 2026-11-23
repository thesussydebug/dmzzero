package com.dmzzero.potential;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class PotentialData {
    private static final String KEY = "dmzzero";
    private static final String POTENTIAL = "potential";

    private PotentialData() {
    }

    public static int read(CompoundTag persisted) {
        int value = persisted.getCompound(KEY).getInt(POTENTIAL);
        return Potential.isValid(value) ? value : 0;
    }

    public static void write(CompoundTag persisted, int potential) {
        CompoundTag ours = persisted.getCompound(KEY);
        ours.putInt(POTENTIAL, potential);
        persisted.put(KEY, ours);
    }

    // forge copies PlayerPersisted over death and the end portal
    public static int of(Player player) {
        return read(player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG));
    }

    public static void store(Player player, int potential) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        write(persisted, potential);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
