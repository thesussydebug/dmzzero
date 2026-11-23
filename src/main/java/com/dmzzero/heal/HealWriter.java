package com.dmzzero.heal;

import com.dmzzero.DMZZero;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// writes the healing lists (data/dmzzero_healing.dat) off the server thread, one at a time and in the order the saves came.
// the file is the same one vanilla would write, written to a temp file first and then moved over the old one, so a crash in
// the middle leaves the last good list. the server's stop waits for the last write
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class HealWriter {
    private static final long STOP_WAIT_SECONDS = 60;
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DMZ Zero healing save");
        t.setDaemon(true);
        return t;
    });

    private HealWriter() {
    }

    static void write(File file, HealQueue.Snapshot copy) {
        WRITER.execute(() -> {
            Path target = file.toPath(), tmp = target.resolveSibling(target.getFileName() + ".tmp");
            try {
                CompoundTag root = new CompoundTag();
                root.put("data", HealSave.write(copy.sorted(), new CompoundTag()));
                NbtUtils.addCurrentDataVersion(root);
                NbtIo.writeCompressed(root, tmp.toFile());
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException e) {
                DMZZero.LOG.error("Could not save the healing list to {}", file, e);
            }
        });
    }

    /** Waits until every write asked for so far is on disk. Returns false if that took longer than a minute. */
    public static boolean flush() {
        try {
            WRITER.submit(() -> {
            }).get(STOP_WAIT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            DMZZero.LOG.warn("The healing list was still being saved after {} s", STOP_WAIT_SECONDS, e);
            return false;
        }
    }

    // after the world's last save, before the game exits or loads another world
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        flush();
    }
}
