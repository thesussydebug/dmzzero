package com.dmzzero.duel;

import com.dmzzero.DMZZero;
import com.dmzzero.Titles;
import com.dmzzero.ZeroConfig;
import com.dmzzero.realm.Battles;
import com.dmzzero.rival.Rivals;
import com.dragonminez.server.world.tournament.Tournament;
import com.dragonminez.server.world.worldboss.WorldBossManager;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Cooldowns;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.server.events.players.combat.CombatEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.dragonminez.common.stats.StatsProvider;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// one-on-one fights in a ring: knockdowns instead of deaths, nobody else gets a hit in
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Duels {
    private static final int COST = TickCost.id("versus.duels", () -> Duels.ARENAS.size() + Duels.GET_UP.size());
    private static final int GET_UP_DELAY = 40;
    private static final DustParticleOptions EDGE = new DustParticleOptions(new Vector3f(1f, 0.55f, 0.1f), 1.6f);
    private static final DuelMath.Challenges CHALLENGES = new DuelMath.Challenges(() -> ZeroConfig.SERVER.challengeSeconds.get() * 20);
    private static final List<Arena> ARENAS = new ArrayList<>();
    private static final Map<UUID, Long> GET_UP = new HashMap<>();
    private static int finished;
    private static DuelMath.Outcome lastOutcome;

    static final class Arena {
        final DuelMath.Duel duel;
        final ServerLevel level;
        final Vec3 center;
        final double radius;
        // the players themselves: a respawn makes a new one, so the arena follows it (onClone)
        ServerPlayer a;
        ServerPlayer b;
        final boolean aFist;
        final boolean bFist;
        // a duel between party members lifts their party's friendly fire until it ends
        UUID party;

        Arena(DuelMath.Duel duel, ServerLevel level, Vec3 center, double radius, ServerPlayer a, ServerPlayer b, boolean aFist, boolean bFist) {
            this.duel = duel;
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.a = a;
            this.b = b;
            this.aFist = aFist;
            this.bFist = bFist;
        }

        ServerPlayer of(UUID id) {
            return a.getUUID().equals(id) ? a : b;
        }
    }

    private Duels() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        DuelCommand.register(event.getDispatcher());
    }

    public static String challenge(ServerPlayer from, ServerPlayer to) {
        if (!ZeroConfig.SERVER.duels.get()) return refuse(from, "disabled", "Duels are turned off on this server.");
        if (from == to) return refuse(from, "self", "You can't duel yourself.");
        if (!hasCharacter(from) || !hasCharacter(to)) return refuse(from, "no_character", "Both of you need a character first.");
        if (arena(from) != null) return refuse(from, "busy", "You're already in a duel.");
        if (arena(to) != null) return refuse(from, "target_busy", "%s is already in a duel.", to.getGameProfile().getName());
        if (elsewhere(from)) return refuse(from, "elsewhere", "You can't duel right now. Finish your other fight first.");
        if (elsewhere(to)) return refuse(from, "target_elsewhere", "%s can't duel right now.", to.getGameProfile().getName());
        if (from.level() != to.level() || from.distanceTo(to) > ZeroConfig.SERVER.challengeRange.get())
            return refuse(from, "too_far", "%s is too far away.", to.getGameProfile().getName());
        long now = from.server.getTickCount();
        // each challenge rings a bell for them, so a repeat waits until the open one runs out
        if (CHALLENGES.pending(from.getUUID(), to.getUUID(), now))
            return refuse(from, "already_sent", "You already challenged %s. Wait for their answer.", to.getGameProfile().getName());
        CHALLENGES.challenge(from.getUUID(), to.getUUID(), now);
        boolean rival = Rivals.isRival(to, from.getUUID()) || Rivals.isRival(from, to.getUUID());
        String name = from.getGameProfile().getName();
        MutableComponent msg = Component.translatableWithFallback(rival ? "message.dmzzero.duel.challenged_rival" : "message.dmzzero.duel.challenged",
                rival ? "Your rival %s challenges you to a duel." : "%s challenges you to a duel.", Component.literal(name).withStyle(ChatFormatting.BOLD))
                .withStyle(ChatFormatting.GOLD);
        msg.append(" ").append(button("[Accept]", ChatFormatting.GREEN, "/dmzzero duel accept " + name,
                Component.translatableWithFallback("message.dmzzero.duel.accept_hover", "Fight %s", name)));
        msg.append(" ").append(button("[Decline]", ChatFormatting.RED, "/dmzzero duel decline " + name,
                Component.translatableWithFallback("message.dmzzero.duel.decline_hover", "Not now")));
        to.sendSystemMessage(msg);
        to.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
        from.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.duel.sent", "You challenged %s. They have a minute to answer.",
                to.getGameProfile().getName()).withStyle(ChatFormatting.GRAY));
        return "ok";
    }

    // a fight elsewhere comes first: a duel's fresh start would refill and stand up someone in a null realm battle, a dmz
    // tournament or a world boss fight, and nobody outside the duel could hit them there
    static boolean elsewhere(ServerPlayer p) {
        StatsData data = stats(p);
        if (!p.isAlive() || data != null && data.getStatus().isKnockedDown()) return true;
        return Battles.involved(p) || Tournament.Manager.runOf(p) != null || WorldBossManager.activeFor(p) != null;
    }

    private static Component button(String text, ChatFormatting color, String command, Component hover) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    public static String accept(ServerPlayer to, ServerPlayer from) {
        long now = to.server.getTickCount();
        if (!CHALLENGES.take(from.getUUID(), to.getUUID(), now))
            return refuse(to, "no_challenge", "%s hasn't challenged you (or it ran out).", from.getGameProfile().getName());
        if (arena(from) != null || arena(to) != null) return refuse(to, "accept_busy", "One of you is already in a duel.");
        if (elsewhere(from) || elsewhere(to)) return refuse(to, "accept_elsewhere", "One of you is in another fight or down right now.");
        if (from.level() != to.level() || from.distanceTo(to) > ZeroConfig.SERVER.challengeRange.get() * 1.5)
            return refuse(to, "accept_too_far", "%s is too far away now.", from.getGameProfile().getName());
        start(from, to, now);
        return "ok";
    }

    public static String decline(ServerPlayer to, ServerPlayer from) {
        if (!CHALLENGES.take(from.getUUID(), to.getUUID(), to.server.getTickCount()))
            return refuse(to, "no_challenge", "%s hasn't challenged you (or it ran out).", from.getGameProfile().getName());
        from.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.duel.declined", "%s turned down your duel.",
                to.getGameProfile().getName()).withStyle(ChatFormatting.GRAY));
        to.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.duel.you_declined", "Duel declined.").withStyle(ChatFormatting.GRAY));
        return "declined";
    }

    private static String refuse(ServerPlayer who, String code, String text, Object... args) {
        who.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.duel." + code, text, args).withStyle(ChatFormatting.RED));
        return code;
    }

    static Arena start(ServerPlayer a, ServerPlayer b, long now) {
        ServerLevel level = a.serverLevel();
        Vec3 center = a.position().add(b.position()).scale(0.5);
        StatsData da = stats(a), db = stats(b);
        boolean aFist = da != null && da.getStatus().isFriendlyFistEnabled();
        boolean bFist = db != null && db.getStatus().isFriendlyFistEnabled();
        boolean rivals = Rivals.isRival(a, b.getUUID()) || Rivals.isRival(b, a.getUUID());
        Arena arena = new Arena(new DuelMath.Duel(a.getUUID(), b.getUUID(), now, ZeroConfig.SERVER.duelMinutes.get() * 1200,
                        ZeroConfig.SERVER.ringOutSeconds.get() * 20),
                level, center, ZeroConfig.SERVER.duelRadius.get(), a, b, aFist, bFist);
        ARENAS.add(arena);
        prepare(a, da);
        prepare(b, db);
        if (PartyManager.areInSameParty(a, b)) {
            arena.party = PartyManager.getPartyId(a);
            if (arena.party != null) PartyLift.lift(a.server, arena.party);
        }
        Component news = Component.translatableWithFallback(rivals ? "message.dmzzero.duel.begins_rivals" : "message.dmzzero.duel.begins",
                rivals ? "Rivals %s and %s square off in a duel." : "%s and %s square off in a duel.",
                a.getGameProfile().getName(), b.getGameProfile().getName()).withStyle(ChatFormatting.GOLD);
        double news2 = newsRangeSq();
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(center) <= news2) p.sendSystemMessage(news);
        DMZZero.LOG.info("Duel started: {} vs {}", a.getGameProfile().getName(), b.getGameProfile().getName());
        return arena;
    }

    // a fair start: full bars, standing, and dmz's friendly fist so a lethal hit knocks down instead
    private static void prepare(ServerPlayer p, StatsData data) {
        GET_UP.remove(p.getUUID());
        if (data != null) {
            if (data.getStatus().isKnockedDown()) CombatEvent.releaseKnockdown(p, data);
            data.getResources().setCurrentEnergy(data.getMaxEnergy());
            data.getResources().setCurrentStamina(data.getMaxStamina());
            data.getStatus().setFriendlyFistEnabled(true);
            sync(p);
        }
        p.setHealth(p.getMaxHealth());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickAll(event.getServer(), event.getServer().getTickCount());
    }

    public static void tickAll(MinecraftServer server, long now) {
        for (Iterator<Arena> it = ARENAS.iterator(); it.hasNext(); ) {
            Arena arena = it.next();
            DuelMath.Outcome o = tick(server, arena, now);
            if (o != null) {
                it.remove();
                end(arena, o, now);
            }
        }
        if (!GET_UP.isEmpty()) {
            GET_UP.entrySet().removeIf(e -> {
                if (now < e.getValue()) return false;
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                StatsData data = p != null ? stats(p) : null;
                if (data != null && data.getStatus().isKnockedDown()) {
                    CombatEvent.releaseKnockdown(p, data);
                    if (p.getHealth() < p.getMaxHealth() * 0.5f) p.setHealth(p.getMaxHealth() * 0.5f);
                }
                return true;
            });
        }
    }

    private static DuelMath.Outcome tick(MinecraftServer server, Arena arena, long now) {
        DuelMath.Duel d = arena.duel;
        long age = now - d.start;
        int say = d.announce(now);
        if (say > 0) {
            Component n = Component.literal(String.valueOf(say)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            for (ServerPlayer p : List.of(arena.a, arena.b)) {
                Titles.show(p, n, Component.empty(), 0, 18, 2);
                p.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.9f, 1.0f);
            }
        } else if (say == 0) {
            Component fight = Component.translatableWithFallback("title.dmzzero.duel.fight", "Fight!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            for (ServerPlayer p : List.of(arena.a, arena.b)) {
                Titles.show(p, fight, Component.empty(), 0, 20, 10);
                p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
            }
        }
        if (age % 20 == 0) ring(arena);
        boolean aIn = inRing(arena, arena.a), bIn = inRing(arena, arena.b);
        DuelMath.Outcome o = d.tick(now, aIn, bIn, knockedDown(arena.a), knockedDown(arena.b), online(server, arena.a), online(server, arena.b));
        if (o == null && d.phase(now) == DuelMath.Phase.FIGHTING) {
            warn(arena.a, d.outside(0, now), d.outsideLimit());
            warn(arena.b, d.outside(1, now), d.outsideLimit());
        }
        return o;
    }

    // [server.messages] newsRange, squared; 0 tells nobody but the two fighters
    private static double newsRangeSq() {
        double r = ZeroConfig.SERVER.newsRange.get();
        return r <= 0 ? -1 : r * r;
    }

    private static void warn(ServerPlayer p, long outside, int limit) {
        if (outside <= 0 || outside % 20 != 1) return;
        long left = (limit - outside + 19) / 20;
        p.displayClientMessage(Component.translatableWithFallback("message.dmzzero.duel.outside", "Back in the ring! %ss",
                left).withStyle(ChatFormatting.RED), true);
    }

    private static void ring(Arena arena) {
        int n = (int) Math.min(120, arena.radius * 3);
        double y = arena.center.y + 0.3;
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n;
            arena.level.sendParticles(EDGE, arena.center.x + Math.cos(ang) * arena.radius, y, arena.center.z + Math.sin(ang) * arena.radius,
                    1, 0, 0.15, 0, 0);
        }
    }

    static boolean inRing(Arena arena, ServerPlayer p) {
        if (p.level() != arena.level) return false;
        return DuelMath.inRing(p.getX() - arena.center.x, p.getY() - arena.center.y, p.getZ() - arena.center.z, arena.radius);
    }

    private static boolean knockedDown(ServerPlayer p) {
        StatsData data = stats(p);
        return data != null && data.getStatus().isKnockedDown();
    }

    private static boolean online(MinecraftServer server, ServerPlayer p) {
        if (p instanceof FakePlayer) return !p.isRemoved();
        return server.getPlayerList().getPlayer(p.getUUID()) == p;
    }

    static void end(Arena arena, DuelMath.Outcome o, long now) {
        finished++;
        lastOutcome = o;
        restore(arena.a, arena.aFist);
        restore(arena.b, arena.bFist);
        if (arena.party != null) {
            PartyLift.drop(arena.level.getServer(), arena.party);
            arena.party = null;
        }
        String an = arena.a.getGameProfile().getName(), bn = arena.b.getGameProfile().getName();
        Component news;
        if (o.draw()) {
            record(arena.a, DuelMath.Result.DRAW);
            record(arena.b, DuelMath.Result.DRAW);
            Component title = Component.translatableWithFallback("title.dmzzero.duel.draw", "Draw").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            Component sub = reason(o.reason(), null);
            Titles.show(arena.a, title, sub, 5, 50, 15);
            Titles.show(arena.b, title, sub, 5, 50, 15);
            news = Component.translatableWithFallback("message.dmzzero.duel.news_draw", "%s and %s fought to a draw.", an, bn);
        } else {
            ServerPlayer winner = arena.of(o.winner()), loser = arena.of(o.loser());
            record(winner, DuelMath.Result.WIN);
            record(loser, DuelMath.Result.LOSS);
            Titles.show(winner, Component.translatableWithFallback("title.dmzzero.duel.won", "Victory").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    reason(o.reason(), loser.getGameProfile().getName()), 5, 50, 15);
            Titles.show(loser, Component.translatableWithFallback("title.dmzzero.duel.lost", "Defeat").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    reason(o.reason(), loser.getGameProfile().getName()), 5, 50, 15);
            winner.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1.0f);
            news = Component.translatableWithFallback("message.dmzzero.duel.news_won", "%s beat %s in a duel.",
                    winner.getGameProfile().getName(), loser.getGameProfile().getName());
            // dmz's knockdown is the end of the fight, not thirty seconds on the floor
            if (o.reason() == DuelMath.Reason.KNOCKDOWN) GET_UP.put(loser.getUUID(), now + GET_UP_DELAY);
            if (o.reason() == DuelMath.Reason.KNOCKDOWN || o.reason() == DuelMath.Reason.LEFT_RING) {
                // a rival win pays more, so it goes first and the duel's share waits out the cooldown
                Rivals.beaten(loser, winner);
                com.dmzzero.form.FightMastery.award(winner, com.dmzzero.form.FightMastery.Win.DUEL);
            }
        }
        Component line = news.copy().withStyle(ChatFormatting.GOLD);
        double news2 = newsRangeSq();
        for (ServerPlayer p : arena.level.players()) {
            if (p == arena.a || p == arena.b || p.distanceToSqr(arena.center) > news2) continue;
            p.sendSystemMessage(line);
        }
        DMZZero.LOG.info("Duel ended: {} vs {}, {} ({})", an, bn, o.draw() ? "draw" : arena.of(o.winner()).getGameProfile().getName() + " won", o.reason());
    }

    private static Component reason(DuelMath.Reason reason, String loser) {
        return switch (reason) {
            case KNOCKDOWN -> loser == null
                    ? Component.translatableWithFallback("title.dmzzero.duel.both_down", "Both went down")
                    : Component.translatableWithFallback("title.dmzzero.duel.knockdown", "%s went down", loser);
            case LEFT_RING -> loser == null
                    ? Component.translatableWithFallback("title.dmzzero.duel.both_left", "Both left the ring")
                    : Component.translatableWithFallback("title.dmzzero.duel.left_ring", "%s left the ring", loser);
            case LOGGED_OUT -> Component.translatableWithFallback("title.dmzzero.duel.logged_out", "Forfeit");
            case TIME -> Component.translatableWithFallback("title.dmzzero.duel.time", "Time's up");
        };
    }

    private static void restore(ServerPlayer p, boolean fist) {
        StatsData data = stats(p);
        if (data == null || data.getStatus().isFriendlyFistEnabled() == fist) return;
        data.getStatus().setFriendlyFistEnabled(fist);
        sync(p);
    }

    private static void record(ServerPlayer p, DuelMath.Result result) {
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        DuelMath.record(persisted, result);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    // outsiders can't hit a duelist and duelists can't hit outsiders
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (ARENAS.isEmpty() || event.getEntity().level().isClientSide) return;
        ServerPlayer attacker = Rivals.attacker(event.getSource());
        if (attacker == null || !(event.getEntity() instanceof ServerPlayer victim) || attacker == victim) return;
        Arena va = arena(victim), aa = arena(attacker);
        if (va == null && aa == null) return;
        if (va != null && va == aa) return;
        event.setCanceled(true);
        if (aa != null) {
            attacker.displayClientMessage(Component.translatableWithFallback("message.dmzzero.duel.focus", "You're in a duel with %s.",
                    aa.of(aa.duel.other(attacker.getUUID())).getGameProfile().getName()).withStyle(ChatFormatting.GRAY), true);
        } else {
            attacker.displayClientMessage(Component.translatableWithFallback("message.dmzzero.duel.stay_out", "%s is in a duel.",
                    victim.getGameProfile().getName()).withStyle(ChatFormatting.GRAY), true);
        }
    }

    // anything that slips past friendly fist knocks down instead of killing
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        Arena arena = arena(victim);
        if (arena == null) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        StatsData data = stats(victim);
        if (data == null) return;
        event.setCanceled(true);
        victim.setHealth(1f);
        int ticks = ConfigManager.getCombatConfig().getKnockdownDurationSeconds() * 20;
        data.getStatus().setKnockedDown(true);
        data.getCooldowns().setCooldown(Cooldowns.KNOCKDOWN_DURATION, ticks);
        data.getCooldowns().setCooldown(Cooldowns.KNOCKDOWN_INVULN, ticks);
        sync(victim);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        CHALLENGES.forget(p.getUUID());
        Arena arena = arena(p);
        if (arena == null) return;
        // a forfeit, settled now: the player's data is saved right after this event, so a loss written on the next tick
        // would never be kept
        ARENAS.remove(arena);
        end(arena, new DuelMath.Outcome(arena.duel.other(p.getUUID()), p.getUUID(), DuelMath.Reason.LOGGED_OUT), p.server.getTickCount());
    }

    // a death the duel lets through (the void, /kill) respawns the player as a new ServerPlayer with dmz's data copied
    // over, friendly fist and all: the arena follows the new one, so the end puts it back on the player who's playing
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (ARENAS.isEmpty() || !(event.getEntity() instanceof ServerPlayer now) || !(event.getOriginal() instanceof ServerPlayer was)) return;
        for (Arena arena : ARENAS) {
            if (arena.a == was) arena.a = now;
            if (arena.b == was) arena.b = now;
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (Arena arena : ARENAS) {
            restore(arena.a, arena.aFist);
            restore(arena.b, arena.bFist);
        }
        ARENAS.clear();
        CHALLENGES.clear();
        GET_UP.clear();
    }

    static Arena arena(ServerPlayer p) {
        for (Arena arena : ARENAS) if (arena.duel.has(p.getUUID())) return arena;
        return null;
    }

    public static boolean inDuel(ServerPlayer p) {
        return arena(p) != null;
    }

    // who they're dueling right now, or null
    public static String opponentName(ServerPlayer p) {
        Arena arena = arena(p);
        if (arena == null) return null;
        return (arena.a == p ? arena.b : arena.a).getGameProfile().getName();
    }

    // the two sides of one duel: dmz's party rules don't make them friends while it lasts
    public static boolean opponents(ServerPlayer a, ServerPlayer b) {
        if (ARENAS.isEmpty() || a == b) return false;
        Arena arena = arena(a);
        return arena != null && arena == arena(b);
    }

    // ends a duel straight away, as if time ran out (self-test cleanup)
    public static void stop(ServerPlayer p) {
        Arena arena = arena(p);
        if (arena == null) return;
        ARENAS.remove(arena);
        end(arena, new DuelMath.Outcome(null, null, DuelMath.Reason.TIME), p.server.getTickCount());
    }

    public static int[] score(ServerPlayer p) {
        return DuelMath.score(p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG));
    }

    private static boolean hasCharacter(ServerPlayer p) {
        StatsData data = stats(p);
        return data != null && data.getStatus().isHasCreatedCharacter();
    }

    private static StatsData stats(ServerPlayer p) {
        return StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
    }

    private static void sync(ServerPlayer p) {
        if (p.connection != null) NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(p), p);
    }

    public static int finished() {
        return finished;
    }

    public static DuelMath.Outcome lastOutcome() {
        return lastOutcome;
    }
}
