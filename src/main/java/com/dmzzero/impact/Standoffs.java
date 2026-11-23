package com.dmzzero.impact;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Status;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// two auras up, face to face and close: the ground between them starts to give before anyone moves
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Standoffs {
    private static final int COST = TickCost.id("fighting.impacts");
    private static final double MAX_ANGLE = 35;
    private static final double MAX_HEIGHT_GAP = 4;
    // dev tests stand fake players in front of the real one
    private static final List<ServerPlayer> EXTRA = new ArrayList<>();
    private static int started;

    private Standoffs() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ZeroConfig.SERVER.standoffs.get()) return;
        // one pass over the players, and nothing more unless two of them have their aura up
        List<ServerPlayer> up = null;
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (!auraUp(p)) continue;
            if (up == null) up = new ArrayList<>();
            up.add(p);
        }
        for (ServerPlayer p : EXTRA) {
            if (!auraUp(p)) continue;
            if (up == null) up = new ArrayList<>();
            up.add(p);
        }
        if (up == null || up.size() < 2) return;
        Map<ServerLevel, List<ServerPlayer>> byLevel = new HashMap<>();
        for (ServerPlayer p : up) byLevel.computeIfAbsent(p.serverLevel(), k -> new ArrayList<>()).add(p);
        for (Map.Entry<ServerLevel, List<ServerPlayer>> e : byLevel.entrySet()) if (e.getValue().size() >= 2) scan(e.getKey(), e.getValue());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EXTRA.clear();
    }

    public static int scan(ServerLevel level, List<ServerPlayer> fighters) {
        double range = ZeroConfig.SERVER.standoffRange.get();
        int pairs = 0;
        for (int i = 0; i < fighters.size(); i++) {
            ServerPlayer a = fighters.get(i);
            for (int j = i + 1; j < fighters.size(); j++) {
                ServerPlayer b = fighters.get(j);
                if (!facingOff(a, b, range)) continue;
                String key = StandoffMath.key(a.getStringUUID(), b.getStringUUID());
                if (!ClashImpacts.isActive(key)) started++;
                Vec3 mid = a.position().add(b.position()).scale(0.5).add(0, 1.0, 0);
                ClashImpacts.standoff(key, level, mid, () -> List.<LivingEntity>of(a, b));
                pairs++;
            }
        }
        return pairs;
    }

    public static boolean facingOff(ServerPlayer a, ServerPlayer b, double range) {
        if (a.level() != b.level() || Math.abs(a.getY() - b.getY()) > MAX_HEIGHT_GAP) return false;
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        if (!StandoffMath.inRange(Math.sqrt(dx * dx + dz * dz), range)) return false;
        if (!StandoffMath.faceOff(a.getYRot(), a.getX(), a.getZ(), b.getYRot(), b.getX(), b.getZ(), MAX_ANGLE)) return false;
        return a.hasLineOfSight(b);
    }

    public static boolean auraUp(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return false;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (data == null || !data.getStatus().isHasCreatedCharacter()) return false;
        Status s = data.getStatus();
        return s.isAuraActive() || s.isPermanentAura() || s.isForcedAura();
    }

    public static void addTestFighter(ServerPlayer player) {
        if (!EXTRA.contains(player)) EXTRA.add(player);
    }

    public static void removeTestFighter(ServerPlayer player) {
        EXTRA.remove(player);
    }

    public static int started() {
        return started;
    }
}
