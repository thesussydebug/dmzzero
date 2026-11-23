package com.dmzzero.impact;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class ImpactData {
    private static final String ROOT = "dmzzero";
    private static final String NO_DESTRUCTION = "noDestruction";

    private ImpactData() {
    }

    public static boolean destructionOff(CompoundTag persisted) {
        return persisted.getCompound(ROOT).getBoolean(NO_DESTRUCTION);
    }

    public static void setDestructionOff(CompoundTag persisted, boolean off) {
        CompoundTag root = persisted.getCompound(ROOT);
        if (off) root.putBoolean(NO_DESTRUCTION, true);
        else root.remove(NO_DESTRUCTION);
        persisted.put(ROOT, root);
    }

    public static boolean destructionOff(Player player) {
        return destructionOff(persisted(player));
    }

    public static void setDestructionOff(Player player, boolean off) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        setDestructionOff(persisted, off);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }
}
