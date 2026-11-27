package com.dmzzero.garden;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.scouter.Scouters;
import com.dmzzero.status.TickCost;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// on joining, one chat line names what of yours became ready since you left: senzu, saibaman seeds, trees of might, and
// ripe senzu also get a line over the hotbar. the world's clock stands still while a server is stopped (and in singleplayer
// while you're out), so only time it ran counts
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class AwayNotice {
    private static final String ROOT = "dmzzero";
    private static final String LEFT_AT = "gardenLeftAt";
    private static final String K = "message.dmzzero.away.";
    // a joining player still sees the loading screen for a moment, and a hotbar line only lasts 3 seconds
    private static final int HARVEST_DELAY = 100;
    private static final Map<UUID, Integer> HARVEST_DUE = new HashMap<>();
    private static final int COST = TickCost.id("world.garden", HARVEST_DUE::size);

    private AwayNotice() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Component notice = notice(player);
        if (notice != null) player.sendSystemMessage(notice);
        if (harvest(player) != null) HARVEST_DUE.put(player.getUUID(), player.server.getTickCount() + HARVEST_DELAY);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || HARVEST_DUE.isEmpty()) return;
        long start = TickCost.start();
        MinecraftServer server = event.getServer();
        for (Iterator<Map.Entry<UUID, Integer>> it = HARVEST_DUE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> due = it.next();
            if (server.getTickCount() < due.getValue()) continue;
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(due.getKey());
            // asked again, in case they've picked it since joining
            Component harvest = player == null ? null : harvest(player);
            if (harvest != null) player.displayClientMessage(harvest, true);
        }
        TickCost.add(COST, start);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HARVEST_DUE.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        HARVEST_DUE.remove(player.getUUID());
        noteLeft(player);
    }

    // a stopping server drops its players without a logout event; this runs before it saves them
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        event.getServer().getPlayerList().getPlayers().forEach(AwayNotice::noteLeft);
    }

    private static void noteLeft(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        root.putLong(LEFT_AT, player.server.overworld().getGameTime());
        persisted.put(ROOT, root);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** The chat line for this player, or null when nothing of theirs became ready since they last left. */
    public static Component notice(ServerPlayer player) {
        List<Component> ready = ready(player).stream().map(AwayNotice::phrase).toList();
        if (ready.isEmpty()) return null;
        int named = Math.min(ready.size(), ZeroConfig.SERVER.awayNoticeMax.get());
        MutableComponent list = ComponentUtils.formatList(ready.subList(0, named), Component.literal("; ")).copy();
        if (named < ready.size()) list = line("more", "%s; and %s more", list, ready.size() - named);
        return line("header", "While you were away: %s.", list).withStyle(ChatFormatting.GREEN);
    }

    /** The line over the hotbar, or null when none of this player's senzu ripened while they were away. */
    public static Component harvest(ServerPlayer player) {
        return ready(player).stream().anyMatch(g -> g.kind() == Growth.Kind.BUSH)
                ? line("harvest", "Your senzu(s) are ready to harvest.") : null;
    }

    // what became ready since they last left and still waits, earliest first
    private static List<Growth> ready(ServerPlayer player) {
        if (!ZeroConfig.SERVER.awayNotice.get()) return List.of();
        CompoundTag root = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ROOT);
        if (!root.contains(LEFT_AT)) return List.of();
        long left = root.getLong(LEFT_AT);
        return Growth.of(player).stream()
                .filter(g -> SenzuMath.becameReady(g.readyAt(), left, g.level().getGameTime()))
                .sorted(Comparator.comparingLong(Growth::readyAt))
                .toList();
    }

    private static Component phrase(Growth g) {
        String at = GardenCommand.at(g.pos()), world = Scouters.worldName(g.level());
        return switch (g.kind()) {
            case BUSH -> line("bush", "Senzu ready at %s on %s", at, world);
            case SPROUT -> line("sprout", "a Saibaman seed ready at %s on %s", at, world);
            case TREE -> line("tree", "a Tree of Might ready to finish growing at %s on %s", at, world);
            case FRUIT -> line("fruit", "a Tree of Might fruit due at %s on %s", at, world);
        };
    }

    private static MutableComponent line(String key, String english, Object... args) {
        return Component.translatableWithFallback(K + key, english, args);
    }
}
