package com.dmzzero.chamber;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.network.StatsDeltaSync;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.extras.FormMasteries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// what a fight with a training copy pays. dmz gives tp and form mastery for a hit and a kill inside the hurt and death
// events, so each one with a copy in it is watched from its first listener to its last: tp posted meanwhile is cut, and
// the forms' mastery is compared before and after and cut to the same share of the rise
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class TrainingPay {
    // the server tick the open fights belong to, so a close that never came can't spill into a later tick
    private static int openTick = -1;
    private static int open;
    private static final Map<UUID, Before> BEFORE = new HashMap<>();
    private static long tpCut;

    private TrainingPay() {
    }

    private record Before(Map<String, Double> forms, Map<String, Double> stacks) {
    }

    private static boolean involves(LivingEntity target, DamageSource source) {
        return Training.isCopy(target) || Training.trainer(source) != null;
    }

    // who hit a copy, and who a copy hit
    private static List<ServerPlayer> players(LivingEntity target, DamageSource source) {
        List<ServerPlayer> out = new ArrayList<>(2);
        if (Training.isCopy(target) && source.getEntity() instanceof ServerPlayer attacker) out.add(attacker);
        if (target instanceof ServerPlayer victim && Training.trainer(source) != null) out.add(victim);
        return out;
    }

    private static void open(LivingEntity target, DamageSource source) {
        if (target.level().isClientSide() || target.getServer() == null || !involves(target, source)) return;
        int now = target.getServer().getTickCount();
        if (openTick != now) {
            open = 0;
            BEFORE.clear();
            openTick = now;
        }
        open++;
        if (ZeroConfig.SERVER.trainingMasteryPercent.get() >= 100) return;
        for (ServerPlayer player : players(target, source)) {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            if (data == null || data.getCharacter() == null) continue;
            BEFORE.put(player.getUUID(), new Before(data.getCharacter().getFormMasteries().entries(),
                    data.getCharacter().getStackFormMasteries().entries()));
        }
    }

    private static void close(LivingEntity target, DamageSource source) {
        if (target.level().isClientSide() || !involves(target, source)) return;
        if (open > 0) open--;
        int percent = ZeroConfig.SERVER.trainingMasteryPercent.get();
        for (ServerPlayer player : players(target, source)) {
            Before before = BEFORE.remove(player.getUUID());
            StatsData data = before == null ? null : StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            if (data == null || data.getCharacter() == null) continue;
            boolean cut = cut(data.getCharacter().getFormMasteries(), before.forms(), percent);
            cut |= cut(data.getCharacter().getStackFormMasteries(), before.stacks(), percent);
            if (cut) StatsDeltaSync.markDirty(player);
        }
    }

    private static boolean cut(FormMasteries masteries, Map<String, Double> before, int percent) {
        Map<String, Double> cuts = TrainingMath.cuts(before, masteries.entries(), percent);
        cuts.forEach((key, amount) -> masteries.addRaw(key, -amount));
        return !cuts.isEmpty();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void openHurt(LivingHurtEvent event) {
        open(event.getEntity(), event.getSource());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void closeHurt(LivingHurtEvent event) {
        close(event.getEntity(), event.getSource());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void openDeath(LivingDeathEvent event) {
        open(event.getEntity(), event.getSource());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void closeDeath(LivingDeathEvent event) {
        close(event.getEntity(), event.getSource());
    }

    // with Potential and the chamber's depth (both LOW) and ahead of the diary (LOWEST), so the diary counts the cut amount
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTp(DMZEvent.TPGainEvent event) {
        if (open <= 0 || event.getTpGain() <= 0 || !(event.getPlayer() instanceof ServerPlayer player)
                || player.getServer() == null || player.getServer().getTickCount() != openTick) return;
        int percent = ZeroConfig.SERVER.trainingTpPercent.get();
        if (percent >= 100) return;
        int was = event.getTpGain();
        event.setTpGain(TrainingMath.tp(was, percent));
        tpCut += was - event.getTpGain();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && (open != 0 || !BEFORE.isEmpty())) {
            open = 0;
            BEFORE.clear();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        open = 0;
        openTick = -1;
        BEFORE.clear();
    }

    /** For the self-test: TP held back from training fights since the server started. */
    public static long tpCut() {
        return tpCut;
    }
}
