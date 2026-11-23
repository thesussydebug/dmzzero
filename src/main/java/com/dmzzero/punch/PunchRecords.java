package com.dmzzero.punch;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.init.entities.PunchMachineEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// dmz's punch machine shows each hit's damage and forgets it. this keeps each player's best hit and best total over a
// few seconds (PlayerPersisted.dmzzero.punch), says so when they beat one, and reads them out when they walk up
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class PunchRecords {
    private static final String ROOT = "dmzzero";
    private static final String KEY = "punch";
    private static final String BEST = "best";
    private static final String BEST_WINDOW = "bestWindow";
    private static final String WINDOW_SECONDS = "windowSeconds";
    private static final double SHOW_RANGE = 4.0;
    private static final Map<UUID, PunchMath.Window> WINDOWS = new HashMap<>();
    private static final Set<UUID> NEAR = new HashSet<>();
    private static final int COST = TickCost.id("world.punchMachine", WINDOWS::size, NEAR::size);
    private static String lastChat = "";
    private static String lastBar = "";

    private PunchRecords() {
    }

    /** The PunchMachineEntity.processHit mixin: DMZ calls it for a player's strike or ki blast on the machine. */
    public static void onHit(PunchMachineEntity machine, Player p, float damage) {
        if (!(p instanceof ServerPlayer player) || !ZeroConfig.SERVER.punchRecords.get()) return;
        hit(player, machine.getId(), damage, player.server.getTickCount());
    }

    public static void hit(ServerPlayer player, int machine, double damage, long now) {
        if (!Double.isFinite(damage) || damage <= 0) return;
        int window = windowTicks();
        PunchMath.Window before = WINDOWS.get(player.getUUID());
        if (before != null && (before.machine() != machine || PunchMath.closed(before, now, window))) {
            close(player, before);
            before = null;
        }
        PunchMath.Window w = PunchMath.add(before, machine, now, damage, window);
        WINDOWS.put(player.getUUID(), w);
        CompoundTag punch = punch(player);
        double best = punch.getDouble(BEST);
        if (PunchMath.better(best, damage)) {
            punch.putDouble(BEST, damage);
            save(player, punch);
            chat(player, "message.dmzzero.punch.best_hit", "New best hit on the Punch Machine: %s (was %s)",
                    PunchMath.format(damage), PunchMath.format(best));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        }
        bar(player, "message.dmzzero.punch.running", "%s in %s seconds so far (best %s)",
                PunchMath.format(w.sum()), windowSeconds(), PunchMath.format(bestWindow(punch)));
    }

    /** A finished total: kept if it beats the record. */
    static void close(ServerPlayer player, PunchMath.Window w) {
        WINDOWS.remove(player.getUUID());
        CompoundTag punch = punch(player);
        double best = bestWindow(punch);
        if (!PunchMath.better(best, w.sum())) return;
        punch.putDouble(BEST_WINDOW, w.sum());
        punch.putInt(WINDOW_SECONDS, windowSeconds());
        save(player, punch);
        chat(player, "message.dmzzero.punch.best_window", "New best in %s seconds on the Punch Machine: %s (was %s)",
                windowSeconds(), PunchMath.format(w.sum()), PunchMath.format(best));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (!ZeroConfig.SERVER.punchRecords.get()) return;
        long start = TickCost.start();
        check(player, player.server.getTickCount());
        TickCost.add(COST, start);
    }

    /** Once a second: close a total whose time is up, and read out the records to someone who just walked up to a machine. */
    public static void check(ServerPlayer player, long now) {
        PunchMath.Window w = WINDOWS.get(player.getUUID());
        if (PunchMath.closed(w, now, windowTicks())) close(player, w);
        boolean near = !player.isSpectator()
                && !player.level().getEntitiesOfClass(PunchMachineEntity.class, player.getBoundingBox().inflate(SHOW_RANGE)).isEmpty();
        if (!near) {
            NEAR.remove(player.getUUID());
            return;
        }
        if (!NEAR.add(player.getUUID()) || WINDOWS.containsKey(player.getUUID())) return;
        CompoundTag punch = punch(player);
        if (punch.getDouble(BEST) <= 0) bar(player, "message.dmzzero.punch.none", "Hit the Punch Machine to set your first record.");
        else bar(player, "message.dmzzero.punch.record", "Your best: %s in one hit, %s in %s seconds.",
                PunchMath.format(punch.getDouble(BEST)), PunchMath.format(bestWindow(punch)), windowSeconds());
    }

    public static double best(Player player) {
        return punch(player).getDouble(BEST);
    }

    public static double bestWindow(Player player) {
        return bestWindow(punch(player));
    }

    // a total kept under another window length isn't comparable
    private static double bestWindow(CompoundTag punch) {
        return punch.getInt(WINDOW_SECONDS) == windowSeconds() ? punch.getDouble(BEST_WINDOW) : 0;
    }

    public static void forget(Player player) {
        WINDOWS.remove(player.getUUID());
        NEAR.remove(player.getUUID());
    }

    /** The self-test's fake players keep their records between runs; this wipes them. */
    public static void reset(Player player) {
        forget(player);
        save(player, new CompoundTag());
    }

    /** The self-test reads these: a record goes to chat, and the running total or read-out to the action bar right after. */
    public static String lastChat() {
        return lastChat;
    }

    public static String lastBar() {
        return lastBar;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PunchMath.Window w = WINDOWS.get(player.getUUID());
        if (w != null) close(player, w);
        forget(player);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WINDOWS.clear();
        NEAR.clear();
    }

    private static int windowSeconds() {
        return ZeroConfig.SERVER.punchWindow.get();
    }

    private static int windowTicks() {
        return windowSeconds() * 20;
    }

    private static CompoundTag punch(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ROOT).getCompound(KEY);
    }

    private static void save(Player player, CompoundTag punch) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        if (punch.isEmpty()) root.remove(KEY);
        else root.put(KEY, punch);
        persisted.put(ROOT, root);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static void bar(ServerPlayer player, String key, String english, Object... args) {
        lastBar = key;
        player.displayClientMessage(Component.translatableWithFallback(key, english, args), true);
    }

    private static void chat(ServerPlayer player, String key, String english, Object... args) {
        lastChat = key;
        player.displayClientMessage(Component.translatableWithFallback(key, english, args), false);
    }
}
