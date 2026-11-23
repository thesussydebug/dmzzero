package com.dmzzero.kishare;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Cooldowns;
import com.dragonminez.server.events.players.combat.CombatEvent;
import com.dragonminez.server.world.worldboss.WorldBossManager;
import com.dragonminez.server.world.worldboss.WorldBossSession;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ki poured into a knocked-down player gets them back up
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Transfusion {
    private static final int BAR = 20;
    private static final Map<UUID, TransfusionMath.Progress> PROGRESS = new HashMap<>();
    private static int revives;
    private static String lastPath = "";

    public enum BossRevive {NOT_BOSS, REVIVED, NO_LIVES, FAILED}

    private Transfusion() {
    }

    static void idle(ServerPlayer player, StatsData data) {
        if (!data.getStatus().isKnockedDown()) PROGRESS.remove(player.getUUID());
    }

    static void tick(ServerPlayer receiver, StatsData data, List<ServerPlayer> donors, long tick) {
        if (!ZeroConfig.SERVER.transfusion.get() || !data.getStatus().isKnockedDown()) {
            PROGRESS.remove(receiver.getUUID());
            return;
        }
        add(receiver, data, TransfusionMath.perTick(donors.size(), seconds(), ZeroConfig.SERVER.maxDonors.get()), donors, tick % 5 == 0);
    }

    // a ki toss caught while down
    public static boolean burst(ServerPlayer receiver, StatsData data, ServerPlayer from) {
        if (!ZeroConfig.SERVER.transfusion.get() || !data.getStatus().isKnockedDown()) return false;
        add(receiver, data, TransfusionMath.tossBurst(seconds()), List.of(from), true);
        return true;
    }

    private static double seconds() {
        return ZeroConfig.SERVER.transfusionSeconds.get();
    }

    private static void add(ServerPlayer receiver, StatsData data, double amount, List<ServerPlayer> donors, boolean show) {
        TransfusionMath.Progress p = PROGRESS.computeIfAbsent(receiver.getUUID(), id -> new TransfusionMath.Progress());
        if (p.done()) return;
        boolean full = p.add(amount);
        if (show || full) showBar(receiver, donors, p);
        if (full) complete(receiver, data, donors);
    }

    private static void showBar(ServerPlayer receiver, List<ServerPlayer> donors, TransfusionMath.Progress p) {
        String bar = TransfusionMath.bar(p.value(), BAR);
        receiver.displayClientMessage(Component.translatableWithFallback("message.dmzzero.transfusion.receiving", "Transfusion %s %s%%",
                bar, p.percent()).withStyle(ChatFormatting.AQUA), true);
        for (ServerPlayer donor : donors) {
            donor.displayClientMessage(Component.translatableWithFallback("message.dmzzero.transfusion.giving", "Transfusion for %s %s %s%%",
                    receiver.getGameProfile().getName(), bar, p.percent()).withStyle(ChatFormatting.AQUA), true);
        }
    }

    private static void complete(ServerPlayer receiver, StatsData data, List<ServerPlayer> donors) {
        BossRevive boss = bossRevive(receiver);
        if (boss == BossRevive.NO_LIVES || boss == BossRevive.FAILED) {
            Component why = boss == BossRevive.NO_LIVES
                    ? Component.translatableWithFallback("message.dmzzero.transfusion.no_lives", "%s has no revives left in this fight.", receiver.getGameProfile().getName())
                    : Component.translatableWithFallback("message.dmzzero.transfusion.failed", "The transfusion couldn't reach %s.", receiver.getGameProfile().getName());
            receiver.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
            for (ServerPlayer donor : donors) donor.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        if (boss == BossRevive.REVIVED) {
            lastPath = "worldboss";
        } else {
            boolean baba = data.getCooldowns().hasCooldown(Cooldowns.BABA_KNOCKOUT);
            CombatEvent.releaseKnockdown(receiver, data);
            if (receiver.hasEffect(MainEffects.KNOCKOUT.get())) receiver.removeEffect(MainEffects.KNOCKOUT.get());
            if (baba) receiver.removeEffect(MobEffects.GLOWING);
            float health = (float) (receiver.getMaxHealth() * ZeroConfig.SERVER.transfusionHealth.get());
            if (receiver.getHealth() < health) receiver.setHealth(health);
            revives++;
            lastPath = "knockdown";
        }
        PROGRESS.remove(receiver.getUUID());

        ServerLevel level = receiver.serverLevel();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, receiver.getX(), receiver.getY() + 1, receiver.getZ(), 40, 0.4, 0.8, 0.4, 0.35);
        level.sendParticles(ParticleTypes.END_ROD, receiver.getX(), receiver.getY() + 1, receiver.getZ(), 16, 0.3, 0.6, 0.3, 0.05);
        level.playSound(null, receiver.getX(), receiver.getY(), receiver.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9f, 1.4f);
        receiver.displayClientMessage(Component.translatableWithFallback("message.dmzzero.transfusion.up", "Back on your feet.")
                .withStyle(ChatFormatting.GREEN), true);
        for (ServerPlayer donor : donors) {
            donor.displayClientMessage(Component.translatableWithFallback("message.dmzzero.transfusion.done", "%s is back on their feet.",
                    receiver.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);
        }
        DMZZero.LOG.info("{} got {} back up with a transfusion ({})", donors.isEmpty() ? "?" : donors.get(0).getGameProfile().getName(),
                receiver.getGameProfile().getName(), lastPath);
    }

    // world boss knockouts last until a teammate revives you; dmz's revive is private, so reach it the long way
    public static BossRevive bossRevive(ServerPlayer target) {
        WorldBossSession session = WorldBossManager.activeFor(target);
        if (session == null || !session.isKnockedOut(target.getUUID())) return BossRevive.NOT_BOSS;
        try {
            Hook hook = Hook.get();
            Object participant = ((Map<?, ?>) hook.participants.get(session)).get(target.getUUID());
            if (participant == null) return BossRevive.FAILED;
            if (hook.livesLeft.getInt(participant) <= 0) return BossRevive.NO_LIVES;
            hook.revive.invoke(session, target.serverLevel(), target, participant, true);
            return BossRevive.REVIVED;
        } catch (ReflectiveOperationException | RuntimeException e) {
            DMZZero.LOG.warn("World boss revive through a transfusion failed", e);
            return BossRevive.FAILED;
        }
    }

    public static boolean bossHookReady() {
        try {
            Hook.get();
            return true;
        } catch (ReflectiveOperationException e) {
            DMZZero.LOG.warn("World boss revive hook not found", e);
            return false;
        }
    }

    private record Hook(Field participants, Field livesLeft, Method revive) {
        private static Hook hook;

        static Hook get() throws ReflectiveOperationException {
            if (hook != null) return hook;
            Class<?> participant = Class.forName("com.dragonminez.server.world.worldboss.WorldBossSession$Participant");
            Field participants = WorldBossSession.class.getDeclaredField("participants");
            Field lives = participant.getDeclaredField("livesLeft");
            Method revive = WorldBossSession.class.getDeclaredMethod("revive", ServerLevel.class, ServerPlayer.class, participant, boolean.class);
            participants.setAccessible(true);
            lives.setAccessible(true);
            revive.setAccessible(true);
            hook = new Hook(participants, lives, revive);
            return hook;
        }
    }

    public static double progress(ServerPlayer player) {
        TransfusionMath.Progress p = PROGRESS.get(player.getUUID());
        return p == null ? 0 : p.value();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PROGRESS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PROGRESS.clear();
    }

    public static int revives() {
        return revives;
    }

    public static String lastPath() {
        return lastPath;
    }
}
