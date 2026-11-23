package com.dmzzero.underdog;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.sense.CreaturePower;
import com.dmzzero.sense.PowerSense;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.TpSource;
import com.dragonminez.common.network.StatsDeltaSync;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// goku lives for stronger opponents: beating something with more battle power than you pays extra TP on top of dmz's own
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Underdog {
    // killer and victim, for beating the same player again
    private static final Map<String, Long> LAST = new HashMap<>();
    private static int paid;
    private static int lastTp;

    private Underdog() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !ZeroConfig.SERVER.underdog.get()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer == victim) return;
        reward(killer, victim);
    }

    /** Pays the bonus for beating victim, and returns the TP paid. */
    public static int reward(ServerPlayer killer, LivingEntity victim) {
        double theirs = CreaturePower.of(victim);
        if (theirs <= 0) return 0;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, killer).orElse(null);
        if (data == null || !data.getStatus().isHasCreatedCharacter()) return 0;
        // your full power, so lowering your power release can't make everything look stronger
        double ratio = UnderdogMath.ratio(theirs, PowerSense.fullPower(data));
        double share = UnderdogMath.share(ratio, ZeroConfig.SERVER.underdogMinRatio.get(), ZeroConfig.SERVER.underdogPerRatio.get(),
                ZeroConfig.SERVER.underdogMax.get());
        if (share <= 0) return 0;
        long now = killer.server.getTickCount();
        String key = null;
        if (victim instanceof ServerPlayer) {
            key = killer.getUUID() + "|" + victim.getUUID();
            Long last = LAST.get(key);
            if (last != null && now - last < ZeroConfig.SERVER.underdogCooldown.get() * 1200L) return 0;
        }
        var gameplay = ConfigManager.getServerConfig().getGameplay();
        int kill = (int) Math.round(gameplay.getTpPerHit() + victim.getMaxHealth() * gameplay.getTpHealthRatio());
        int tp = UnderdogMath.tp(kill, share);
        if (tp <= 0) return 0;
        double before = data.getResources().getTrainingPointsExact();
        data.getResources().addBoostedTrainingPoints(data.applyTpBoosts(TpSource.KILL, tp));
        StatsDeltaSync.markDirty(killer);
        int gained = (int) Math.round(data.getResources().getTrainingPointsExact() - before);
        if (key != null) remember(key, now);
        paid++;
        lastTp = gained;
        killer.displayClientMessage(Component.translatableWithFallback("message.dmzzero.underdog",
                "You beat a stronger foe (x%s your power)! +%s TP", String.format(Locale.ROOT, "%.1f", ratio), gained)
                .withStyle(ChatFormatting.GOLD), true);
        return gained;
    }

    // kept across logouts, or relogging would skip the wait; old pairs are dropped as new ones come in
    private static void remember(String key, long now) {
        long keep = ZeroConfig.SERVER.underdogCooldown.get() * 1200L;
        LAST.values().removeIf(t -> now - t >= keep);
        LAST.put(key, now);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST.clear();
    }

    public static int paid() {
        return paid;
    }

    public static int lastTp() {
        return lastTp;
    }
}
