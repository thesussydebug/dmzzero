package com.dmzzero.rival;

import com.dmzzero.DMZZero;
import com.dmzzero.Titles;
import com.dmzzero.ZeroConfig;
import com.dmzzero.sense.KnownKi;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.TpSource;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.network.StatsDeltaSync;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import com.dmzzero.kishare.StreamMath;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// whoever last beat you is your rival; beating them back is worth a title and a kill's worth of tp again
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Rivals {
    private static final int COST = TickCost.id("growth.rivals", () -> Rivals.DOWN.size() + Rivals.HITTERS.size() + Rivals.SNAPSHOT_AT.size());
    private static final long SNAPSHOT_EVERY = 600;
    private static final KnockdownWatch WATCH = new KnockdownWatch();
    private static final Set<UUID> DOWN = new HashSet<>();
    private static final Map<UUID, ServerPlayer> HITTERS = new HashMap<>();
    private static final Map<UUID, Long> SNAPSHOT_AT = new HashMap<>();
    private static int bonuses;
    private static int knockdowns;

    public record Result(boolean newRival, RivalData.Win win, int tp) {
    }

    private Rivals() {
    }

    // canceled deaths too: that's dmz knocking a player out instead of killing them
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        ServerPlayer winner = attacker(event.getSource());
        if (winner != null && winner != victim) beaten(victim, winner);
    }

    // fighting someone is how you come to know their ki
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || event.getAmount() <= 0) return;
        ServerPlayer attacker = attacker(event.getSource());
        if (attacker == null || attacker == victim) return;
        KnownKi.learn(victim, attacker);
        KnownKi.learn(attacker, victim);
        long now = victim.server.getTickCount();
        WATCH.hit(victim.getUUID(), attacker.getUUID(), now);
        HITTERS.put(victim.getUUID(), attacker);
        snapshotIfRival(victim, attacker, now);
        snapshotIfRival(attacker, victim, now);
    }

    // friendly fist and party knockdowns fire no death event: watch for the knockdown itself
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().getTickCount();
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) knockdownCheck(p, now);
    }

    public static void knockdownCheck(ServerPlayer p, long now) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
        boolean down = data != null && data.getStatus().isKnockedDown();
        boolean was = DOWN.contains(p.getUUID());
        if (down == was) return;
        if (!down) {
            DOWN.remove(p.getUUID());
            return;
        }
        DOWN.add(p.getUUID());
        UUID by = WATCH.knockedDown(p.getUUID(), now);
        ServerPlayer winner = by == null ? null : HITTERS.get(p.getUUID());
        HITTERS.remove(p.getUUID());
        if (winner != null && winner.getUUID().equals(by) && !winner.isRemoved() && winner != p) {
            knockdowns++;
            beaten(p, winner);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        WATCH.forget(id);
        DOWN.remove(id);
        HITTERS.remove(id);
        HITTERS.values().removeIf(p -> p.getUUID().equals(id));
        SNAPSHOT_AT.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WATCH.clear();
        DOWN.clear();
        HITTERS.clear();
        SNAPSHOT_AT.clear();
    }

    // what the shadow of your rival will be built from, refreshed every so often while you trade blows
    static void snapshotIfRival(ServerPlayer owner, ServerPlayer rival, long now) {
        if (!ZeroConfig.SERVER.rivals.get() || !rival.getUUID().equals(rivalOf(owner))) return;
        Long last = SNAPSHOT_AT.get(owner.getUUID());
        if (last != null && now - last < SNAPSHOT_EVERY) return;
        if (snapshot(owner, rival)) SNAPSHOT_AT.put(owner.getUUID(), now);
    }

    public static boolean snapshot(ServerPlayer owner, ServerPlayer rival) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, rival).orElse(null);
        if (data == null) return false;
        int release = data.getResources().getPowerRelease();
        if (release <= 0) return false;
        double full = 100.0 / release;
        RivalData.Snapshot s = new RivalData.Snapshot(rival.getMaxHealth(), data.getDefenseLegacyUnits(),
                Math.round(data.getBattlePowerExact() * full), data.getMeleeDamage() * full, data.getKiDamage() * full,
                StreamMath.hex(data.getCharacter().getActiveAuraColor(), 0x9F9F9F), System.currentTimeMillis());
        CompoundTag persisted = RivalData.persisted(owner);
        if (!RivalData.snapshot(persisted, rival.getUUID(), s)) return false;
        RivalData.save(owner, persisted);
        return true;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RivalCommand.register(event.getDispatcher());
    }

    public static ServerPlayer attacker(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer p) return p;
        if (source.getDirectEntity() instanceof AbstractKiProjectile ki && ki.getOwner() instanceof ServerPlayer p) return p;
        return null;
    }

    public static UUID rivalOf(ServerPlayer player) {
        return RivalData.rival(RivalData.persisted(player));
    }

    public static boolean isRival(ServerPlayer player, UUID other) {
        return ZeroConfig.SERVER.rivals.get() && other.equals(rivalOf(player));
    }

    public static Result beaten(ServerPlayer victim, ServerPlayer winner) {
        if (!ZeroConfig.SERVER.rivals.get() || !hasCharacter(victim) || !hasCharacter(winner)) return null;
        // a knockdown, the canceled death behind it and a duel's end are all the same beating
        if (!WATCH.firstBeating(victim.getUUID(), victim.server.getTickCount())) return null;
        long now = System.currentTimeMillis();
        KnownKi.learn(victim, winner);
        KnownKi.learn(winner, victim);

        CompoundTag lost = RivalData.persisted(victim);
        boolean fresh = RivalData.beatenBy(lost, winner.getUUID(), winner.getGameProfile().getName(), now);
        RivalData.save(victim, lost);
        if (fresh) {
            victim.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.rival.new", "%s is now your rival.",
                    name(winner)).withStyle(ChatFormatting.RED));
        } else {
            victim.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.rival.lost_again", "%s beat you again. (%s)",
                    name(winner), score(lost)).withStyle(ChatFormatting.RED));
        }

        CompoundTag won = RivalData.persisted(winner);
        RivalData.Win win = RivalData.won(won, victim.getUUID(), now, ZeroConfig.SERVER.rivalCooldown.get() * 60_000L);
        RivalData.save(winner, won);
        snapshot(victim, winner);
        if (win != RivalData.Win.NOT_RIVAL) snapshot(winner, victim);
        int tp = 0;
        if (win == RivalData.Win.BONUS) {
            bonuses++;
            tp = reward(winner, victim);
            com.dmzzero.form.FightMastery.award(winner, com.dmzzero.form.FightMastery.Win.RIVAL);
            Titles.show(winner,
                    Component.translatableWithFallback("title.dmzzero.rival.defeated", "Rival Defeated").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.translatableWithFallback("title.dmzzero.rival.defeated.sub", "%s  +%s TP", victim.getGameProfile().getName(), tp),
                    10, 60, 20);
            winner.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.rival.won", "You beat your rival %s! +%s TP (%s)",
                    name(victim), tp, score(won)).withStyle(ChatFormatting.GOLD));
            winner.serverLevel().playSound(null, winner.getX(), winner.getY(), winner.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS, 0.8f, 1.0f);
            DMZZero.LOG.info("{} beat their rival {} for {} TP", winner.getGameProfile().getName(), victim.getGameProfile().getName(), tp);
        } else if (win != RivalData.Win.NOT_RIVAL) {
            winner.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.rival.won_again", "You beat your rival %s again. (%s)",
                    name(victim), score(won)).withStyle(ChatFormatting.GOLD));
        }
        return new Result(fresh, win, tp);
    }

    // what dmz pays for the kill, boosted the way dmz boosts it, then potential on top through dmz's own tp event
    static int reward(ServerPlayer winner, ServerPlayer victim) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, winner).orElse(null);
        double share = ZeroConfig.SERVER.rivalBonus.get();
        if (data == null || share <= 0) return 0;
        var gameplay = ConfigManager.getServerConfig().getGameplay();
        int kill = (int) Math.round((gameplay.getTpPerHit() + victim.getMaxHealth() * gameplay.getTpHealthRatio()) * share);
        if (kill <= 0) return 0;
        double before = data.getResources().getTrainingPointsExact();
        data.getResources().addBoostedTrainingPoints(data.applyTpBoosts(TpSource.KILL, kill));
        StatsDeltaSync.markDirty(winner);
        return (int) Math.round(data.getResources().getTrainingPointsExact() - before);
    }

    static Component score(CompoundTag persisted) {
        return Component.translatableWithFallback("message.dmzzero.rival.score", "you %s - %s them",
                RivalData.wins(persisted), RivalData.losses(persisted));
    }

    private static Component name(ServerPlayer player) {
        return Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.BOLD);
    }

    private static boolean hasCharacter(ServerPlayer player) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        return data != null && data.getStatus().isHasCreatedCharacter();
    }

    public static int bonuses() {
        return bonuses;
    }

    // tests beat the same player several times in one tick
    public static void freshFight(ServerPlayer victim) {
        WATCH.forget(victim.getUUID());
    }

    public static int knockdowns() {
        return knockdowns;
    }
}
