package com.dmzzero.kishare;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.impact.ImpactServer;
import com.dmzzero.network.ImpactPulseS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.sense.KnownKi;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.server.events.players.KiShareService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// rides on dmz's own ki sharing: who's giving to whom, once the link has settled
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class KiShare {
    private static final int COST = TickCost.id("ki.share", () -> KiShare.GROUP_TICKS.size());
    private static final double HUM_RANGE = 24;
    // fake players never make it into dmz's links, the self-test plugs them in here
    private static final Map<ServerPlayer, List<ServerPlayer>> TEST_LINKS = new LinkedHashMap<>();
    private static final Map<UUID, Integer> GROUP_TICKS = new HashMap<>();
    private static int hums;

    private KiShare() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long tick = server.getTickCount();
        List<ServerPlayer> receivers = new ArrayList<>(server.getPlayerList().getPlayers());
        for (ServerPlayer p : TEST_LINKS.keySet()) if (!receivers.contains(p)) receivers.add(p);
        for (ServerPlayer receiver : receivers) step(receiver, tick);
        TEST_LINKS.keySet().removeIf(p -> p.isRemoved());
    }

    public static void step(ServerPlayer receiver, long tick) {
        if (receiver.isRemoved()) return;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, receiver).orElse(null);
        if (data == null || !data.getStatus().isHasCreatedCharacter()) return;
        List<ServerPlayer> donors = donors(receiver);
        if (donors.isEmpty()) {
            GROUP_TICKS.remove(receiver.getUUID());
            Transfusion.idle(receiver, data);
            return;
        }
        if (tick % 20 == 0 && ZeroConfig.SERVER.teachKi.get()) teach(receiver, donors);
        group(receiver, donors.size(), tick);
        Transfusion.tick(receiver, data, donors, tick);
    }

    public static List<ServerPlayer> donors(ServerPlayer receiver) {
        List<ServerPlayer> out = new ArrayList<>();
        // isReceiving is dmz's cheap check; settledDonors reads its config and builds a list for every player every tick
        if (receiver.getServer() != null && KiShareService.isReceiving(receiver.getUUID())) {
            for (KiShareService.SettledDonor d : KiShareService.settledDonors(receiver.getServer(), receiver.getUUID(), receiver.level().getGameTime())) {
                out.add(d.player());
            }
        }
        List<ServerPlayer> test = TEST_LINKS.get(receiver);
        if (test != null) for (ServerPlayer d : test) if (!d.isRemoved() && !out.contains(d)) out.add(d);
        return out;
    }

    static void teach(ServerPlayer receiver, List<ServerPlayer> donors) {
        for (ServerPlayer donor : donors) {
            KnownKi.learn(receiver, donor);
            KnownKi.learn(donor, receiver);
        }
    }

    static void group(ServerPlayer receiver, int donors, long tick) {
        int tier = StreamMath.groupTier(donors);
        if (tier == 0 || !ZeroConfig.SERVER.groupShare.get()) {
            GROUP_TICKS.remove(receiver.getUUID());
            return;
        }
        int ticks = GROUP_TICKS.merge(receiver.getUUID(), 1, Integer::sum);
        float progress = StreamMath.groupProgress(ticks);
        ImpactServer.groupShare(receiver, tier, progress, tick);
        if (ticks % 20 == 1) {
            hums++;
            ZeroNetwork.sendAround(receiver.serverLevel(), receiver.position(), HUM_RANGE, new ImpactPulseS2C(receiver.getX(), receiver.getY(), receiver.getZ(),
                    (0.12f + 0.06f * tier) * (0.4f + 0.6f * progress), (float) HUM_RANGE, 0, 0f, 0f));
            receiver.serverLevel().playSound(null, receiver.getX(), receiver.getY(), receiver.getZ(), SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 0.5f + 0.15f * tier, 0.6f + 0.1f * tier);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        GROUP_TICKS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TEST_LINKS.clear();
        GROUP_TICKS.clear();
    }

    public static void addTestLink(ServerPlayer donor, ServerPlayer receiver) {
        TEST_LINKS.computeIfAbsent(receiver, r -> new ArrayList<>()).add(donor);
    }

    public static void clearTestLinks() {
        TEST_LINKS.clear();
    }

    public static int hums() {
        return hums;
    }
}
