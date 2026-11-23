package com.dmzzero.heal;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// per dimension: the damaged blocks waiting to heal (data/dmzzero_healing.dat). block states are saved once each in a palette
public final class HealSave extends SavedData {
    private static final String NAME = "dmzzero_healing";
    // the list's limit is WorldHealing's to decide (HealMath.room), read live from the config
    private final HealQueue queue = new HealQueue();

    public static HealSave get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(HealSave::load, HealSave::new, NAME);
    }

    public HealQueue queue() {
        return queue;
    }

    /** Reads a saved file's contents; the self-test reads back what it saved. */
    public static HealSave load(CompoundTag tag) {
        HealSave s = new HealSave();
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        int[] ids = new int[palette.size()];
        for (int i = 0; i < ids.length; i++) {
            CompoundTag state = palette.getCompound(i);
            // a block from a mod that's gone isn't put back
            ResourceLocation name = ResourceLocation.tryParse(state.getString("Name"));
            ids[i] = name != null && BuiltInRegistries.BLOCK.containsKey(name)
                    ? Block.getId(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), state)) : -1;
        }
        long[] pos = tag.getLongArray("pos");
        int[] original = tag.getIntArray("original");
        int[] left = tag.getIntArray("left");
        long[] due = tag.getLongArray("due");
        int n = Math.min(Math.min(pos.length, due.length), Math.min(original.length, left.length));
        for (int i = 0; i < n; i++) {
            int o = original[i], l = left[i];
            if (o < 0 || o >= ids.length || l < 0 || l >= ids.length || ids[o] < 0 || ids[l] < 0) continue;
            s.queue.record(pos[i], ids[o], ids[l], due[i]);
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        return write(queue.all(), tag);
    }

    // an autosave copies the list here, on the server thread, and HealWriter sorts it, turns it into the file and writes it
    // on a thread of its own: a full 50,000-block list took 0.1 s of a server tick written the vanilla way, 1,000,000 took 2 s
    @Override
    public void save(File file) {
        if (!isDirty()) return;
        HealQueue.Snapshot copy = queue.snapshot();
        setDirty(false);
        HealWriter.write(file, copy);
    }

    /** The saved form of a list, earliest first: each block state once in a palette, then one entry per block. Any thread. */
    static CompoundTag write(List<HealQueue.Entry> all, CompoundTag tag) {
        ListTag palette = new ListTag();
        Map<Integer, Integer> index = new HashMap<>();
        long[] pos = new long[all.size()];
        int[] original = new int[all.size()];
        int[] left = new int[all.size()];
        long[] due = new long[all.size()];
        for (int i = 0; i < all.size(); i++) {
            HealQueue.Entry e = all.get(i);
            pos[i] = e.pos();
            original[i] = slot(e.original(), index, palette);
            left[i] = slot(e.left(), index, palette);
            due[i] = e.due();
        }
        tag.put("palette", palette);
        tag.putLongArray("pos", pos);
        tag.putIntArray("original", original);
        tag.putIntArray("left", left);
        tag.putLongArray("due", due);
        return tag;
    }

    private static int slot(int stateId, Map<Integer, Integer> index, ListTag palette) {
        return index.computeIfAbsent(stateId, id -> {
            BlockState state = Block.stateById(id);
            palette.add(NbtUtils.writeBlockState(state));
            return palette.size() - 1;
        });
    }
}
