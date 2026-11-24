package com.dmzzero.charge;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.network.KiOverchargeS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.network.StatsDeltaSync;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Status;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// dmz's ki overcharge is the surge: keep charging at full ki, the burst arms, then the surge lasts a while. with the ki
// boost skill your ki charges past full through both, 12.5% a level by default, and afterwards the extra fades back.
// only the server's cap is raised (StatsDataKiOverchargeMixin); dmz zero clients get their ki above full for the hud
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class KiOvercharge {
    public static final String SKILL = "kiboost";
    private static final int COST = TickCost.id("ki.overcharge", () -> KiOvercharge.FADING.size() + KiOvercharge.CHARGED.size());
    private static final int EVERY = 4;
    // the cap while the extra fades, only for the StatsData it was measured on (a respawn makes a new one)
    private static final Map<UUID, Fade> FADING = new ConcurrentHashMap<>();
    // ki at the last check while charging in an overcharge
    private static final Map<UUID, Float> CHARGED = new ConcurrentHashMap<>();
    // the ki above full last sent to each player's hud
    private static final Map<UUID, Float> SENT = new ConcurrentHashMap<>();
    // the StatsData whose max ki is being read without the overcharge, server thread only
    private static StatsData raw;

    private record Fade(StatsData data, float ceiling) {
    }

    private KiOvercharge() {
    }

    public static boolean overcharging(Status status) {
        return status != null && (status.isKiBurstArmed() || status.isSurgeActive());
    }

    /** This player's share past full during an overcharge, in percent, from their Ki Boost level and the settings. */
    public static double extraPercent(StatsData data) {
        if (!ZeroConfig.SERVER.overcharge.get()) return 0.0;
        return OverchargeMath.extraPercent(data.getSkills().getSkillLevel(SKILL), ZeroConfig.SERVER.overchargePerLevel.get(),
                ZeroConfig.SERVER.overchargeMax.get());
    }

    /** What StatsData.getMaxEnergy returns: raised on the server during an overcharge and while the extra fades. */
    public static float capacity(StatsData data, float max) {
        if (data == null || data == raw) return max;
        Player player = data.getPlayer();
        if (player == null || player.level().isClientSide()) return max;
        if (overcharging(data.getStatus())) return OverchargeMath.capacity(max, extraPercent(data), true);
        if (FADING.isEmpty()) return max;
        Fade f = FADING.get(player.getUUID());
        return f != null && f.data() == data && f.ceiling() > max ? f.ceiling() : max;
    }

    /** The max ki without the overcharge. */
    public static float normalMax(StatsData data) {
        StatsData before = raw;
        raw = data;
        try {
            return data.getMaxEnergy();
        } finally {
            raw = before;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % EVERY != 0) return;
        long start = TickCost.start();
        step(player);
        TickCost.add(COST, start);
    }

    /** One check of a player's overcharge: the fill while charging, or the fade after. Public for the self-test. */
    public static void step(ServerPlayer player) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        UUID id = player.getUUID();
        if (data == null) return;
        float normal = normalMax(data);
        float current = data.getResources().getCurrentEnergy();
        if (overcharging(data.getStatus())) {
            float cap = data.getMaxEnergy();
            if (cap > normal) FADING.put(id, new Fade(data, cap));
            Float last = CHARGED.get(id);
            if (data.getStatus().isChargingKi() && cap > normal) {
                if (last != null) {
                    float perTick = OverchargeMath.fillPerTick(normal, extraPercent(data), ZeroConfig.SERVER.overchargeFillSeconds.get());
                    float next = OverchargeMath.topUp(last, current, cap, perTick, EVERY);
                    if (next > current) {
                        data.getResources().setCurrentEnergy(next);
                        StatsDeltaSync.markDirty(player);
                        current = data.getResources().getCurrentEnergy();
                    }
                }
                CHARGED.put(id, current);
            } else {
                CHARGED.remove(id);
            }
            tellHud(player, current, normal);
            return;
        }
        CHARGED.remove(id);
        Fade f = FADING.get(id);
        float ceiling = f != null && f.data() == data ? f.ceiling() : current;
        if (!(ceiling > normal) && !(current > normal)) {
            FADING.remove(id);
            tellHud(player, current, normal);
            return;
        }
        // the whole extra of this player's level fades in the fade time, or at least the share it actually reached
        double share = Math.max(extraPercent(data), (ceiling - normal) / normal * 100.0);
        float perTick = OverchargeMath.fadePerTick(normal, share, ZeroConfig.SERVER.overchargeFadeSeconds.get());
        float next = OverchargeMath.fade(ceiling, current, normal, perTick >= Float.MAX_VALUE / EVERY ? Float.MAX_VALUE : perTick * EVERY);
        if (next > normal) FADING.put(id, new Fade(data, next));
        else FADING.remove(id);
        if (current > next) {
            data.getResources().setCurrentEnergy(next);
            StatsDeltaSync.markDirty(player);
            current = data.getResources().getCurrentEnergy();
        }
        tellHud(player, current, normal);
    }

    // dmz clamps the client's ki to its normal full, so a dmz zero client hears the real value while it's above
    private static void tellHud(ServerPlayer player, float current, float normal) {
        UUID id = player.getUUID();
        Float sent = SENT.get(id);
        if (current > normal) {
            if (sent == null || OverchargeMath.worthSending(sent, current, normal)) {
                ZeroNetwork.send(player, new KiOverchargeS2C(current, normal));
                SENT.put(id, current);
            }
        } else if (sent != null) {
            ZeroNetwork.send(player, new KiOverchargeS2C(0f, normal));
            SENT.remove(id);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        FADING.remove(id);
        CHARGED.remove(id);
        SENT.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FADING.clear();
        CHARGED.clear();
        SENT.clear();
    }

    /** For the self-test: forgets the player's fade, charge and hud state. */
    public static void forget(ServerPlayer player) {
        FADING.remove(player.getUUID());
        CHARGED.remove(player.getUUID());
        SENT.remove(player.getUUID());
    }
}
