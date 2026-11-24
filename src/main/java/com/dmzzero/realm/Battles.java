package com.dmzzero.realm;

import com.dmzzero.DMZZero;
import com.dmzzero.Titles;
import com.dmzzero.ZeroConfig;
import com.dmzzero.duel.Duels;
import com.dmzzero.realm.BattleMath.Fight;
import com.dmzzero.realm.BattleMath.Join;
import com.dmzzero.realm.BattleMath.Lobby;
import com.dmzzero.realm.BattleMath.Out;
import com.dmzzero.realm.BattleMath.Result;
import com.dmzzero.realm.BattleMath.Spot;
import com.dmzzero.rival.Rivals;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Cooldowns;
import com.dragonminez.server.events.players.combat.CombatEvent;
import com.dragonminez.server.world.tournament.Tournament;
import com.dragonminez.server.world.worldboss.WorldBossManager;
import net.minecraft.ChatFormatting;
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
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// free-for-alls in the null realm: open one, people join, the last one standing on the stage wins
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Battles {
    private static final int COST = TickCost.id("versus.battles", () -> Battles.BATTLES.size() + Battles.LAST_HIT.size());
    private static final int TO_STANDS_DELAY = 20;
    private static final int RETURN_DELAY = 100;
    private static final int RING_OUT_CREDIT = 100;
    private static final List<Battle> BATTLES = new ArrayList<>();
    private static final Map<UUID, Hit> LAST_HIT = new HashMap<>();
    // fake players aren't in the player list, so the self-test registers them here
    private static final Map<UUID, ServerPlayer> TEST_PLAYERS = new HashMap<>();
    // whoever calls a lobby off waits out its join time before opening another, so its news can't be sent over and over.
    // kept through a logout, and dropped once the wait is over
    private static final Map<UUID, Long> CALLED_OFF = new HashMap<>();
    private static Lobby lobby;
    // the stage the open lobby would take, loading in the background while people join; -1 when none is
    private static int preloadedSlot = -1;
    private static int preloadedRadius;
    private static Result lastResult;
    private static String lastElimination = "";

    private record Hit(UUID by, long at) {
    }

    static final class Battle {
        final Fight fight;
        final int slot;
        final int radius;
        final ServerLevel level;
        final Map<UUID, ServerPlayer> players = new LinkedHashMap<>();
        final Map<UUID, Boolean> fist = new HashMap<>();
        final Set<UUID> lifted = new HashSet<>();
        final Map<UUID, Long> toStands = new HashMap<>();
        final Set<UUID> home = new HashSet<>();
        long endedAt = -1;

        Battle(Fight fight, int slot, int radius, ServerLevel level) {
            this.fight = fight;
            this.slot = slot;
            this.radius = radius;
            this.level = level;
        }

        boolean holds(UUID id) {
            return fight.has(id) && !home.contains(id);
        }

        String name(UUID id) {
            ServerPlayer p = players.get(id);
            return p == null ? "?" : p.getGameProfile().getName();
        }
    }

    private Battles() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BattleCommand.register(event.getDispatcher());
    }

    // ---- the lobby

    public static String open(ServerPlayer p) {
        if (!ZeroConfig.SERVER.battles.get()) return refuse(p, "disabled", "Battles are turned off on this server.");
        if (NullRealm.level(p.server) == null) return refuse(p, "no_realm", "The Null Realm isn't loaded on this server.");
        long now = p.server.getTickCount();
        if (lobby != null && !lobby.expired(now)) {
            return lobby.has(p.getUUID()) ? refuse(p, "already_in", "You're already in the open battle.")
                    : refuse(p, "already_open", "A battle is already open. Join it with /dmzzero battle join.");
        }
        int wait = ZeroConfig.SERVER.battleJoinSeconds.get() * 20;
        CALLED_OFF.values().removeIf(t -> now - t >= wait);
        Long off = CALLED_OFF.get(p.getUUID());
        if (off != null) return refuse(p, "wait", "You called your last battle off. You can open another in %s seconds.", (wait - (now - off) + 19) / 20);
        String why = eligible(p);
        if (why != null) return refuse(p, why, reasonText(why));
        lobby = new Lobby(p.getUUID(), now, ZeroConfig.SERVER.battleJoinSeconds.get() * 20, ZeroConfig.SERVER.battleMaxFighters.get());
        releaseStage(p.server);
        preloadedSlot = freeSlot();
        preloadedRadius = ZeroConfig.SERVER.stageRadius.get();
        NullRealm.preload(NullRealm.level(p.server), preloadedSlot, preloadedRadius);
        String name = p.getGameProfile().getName();
        MutableComponent news = Component.translatableWithFallback("message.dmzzero.battle.opened",
                "%s opened a free-for-all in the Null Realm.", Component.literal(name).withStyle(ChatFormatting.BOLD)).withStyle(ChatFormatting.GOLD);
        news.append(" ").append(button("[Join]", ChatFormatting.GREEN, "/dmzzero battle join",
                Component.translatableWithFallback("message.dmzzero.battle.join_hover", "Fight in %s's battle", name)));
        for (ServerPlayer o : audience(p)) {
            if (o == p) continue;
            o.sendSystemMessage(news);
            o.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.battle.you_opened",
                "You opened a battle. It starts in %s seconds, or sooner if you start it once someone joins.",
                ZeroConfig.SERVER.battleJoinSeconds.get()).withStyle(ChatFormatting.GRAY));
        DMZZero.LOG.info("{} opened a Null Realm battle", name);
        return "ok";
    }

    public static String join(ServerPlayer p) {
        long now = p.server.getTickCount();
        if (lobby == null || lobby.expired(now)) return refuse(p, "no_battle", "There's no open battle to join.");
        if (lobby.has(p.getUUID())) return refuse(p, "already_in", "You're already in the open battle.");
        String why = eligible(p);
        if (why != null) return refuse(p, why, reasonText(why));
        Join r = lobby.join(p.getUUID(), now);
        if (r == Join.FULL) return refuse(p, "full", "That battle is full.");
        if (r != Join.OK) return refuse(p, "no_battle", "There's no open battle to join.");
        Component line = Component.translatableWithFallback("message.dmzzero.battle.joined", "%s joined the battle (%s/%s).",
                p.getGameProfile().getName(), lobby.size(), ZeroConfig.SERVER.battleMaxFighters.get()).withStyle(ChatFormatting.GOLD);
        for (UUID id : lobby.fighters()) {
            ServerPlayer o = find(p.server, id);
            if (o == null) continue;
            if (id.equals(lobby.opener)) {
                o.sendSystemMessage(line.copy().append(" ").append(button("[Start now]", ChatFormatting.GREEN, "/dmzzero battle start",
                        Component.translatableWithFallback("message.dmzzero.battle.start_hover", "Start with who's here"))));
            } else {
                o.sendSystemMessage(line);
            }
        }
        return "ok";
    }

    public static String startNow(ServerPlayer p) {
        long now = p.server.getTickCount();
        if (lobby == null || lobby.expired(now) || !lobby.opener.equals(p.getUUID()))
            return refuse(p, "not_opener", "Only whoever opened the battle can start it.");
        if (!lobby.canStart()) return refuse(p, "not_enough", "You need at least one other fighter.");
        return begin(p.server, now) != null ? "ok" : "cancelled";
    }

    public static String leave(ServerPlayer p) {
        UUID id = p.getUUID();
        long now = p.server.getTickCount();
        if (lobby != null && lobby.has(id)) {
            if (id.equals(lobby.opener)) {
                CALLED_OFF.put(id, now);
                cancelLobby(p.server, Component.translatableWithFallback("message.dmzzero.battle.called_off", "%s called the battle off.",
                        p.getGameProfile().getName()));
            } else {
                lobby.leave(id);
                p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.battle.you_left", "You left the battle.").withStyle(ChatFormatting.GRAY));
            }
            return "ok";
        }
        Battle b = battleOf(p);
        if (b == null) return refuse(p, "not_in", "You're not in a battle.");
        if (b.fight.standing(id) && b.endedAt < 0) eliminate(b, id, p, Out.FORFEIT, null, now);
        goHome(b, p);
        return "ok";
    }

    private static void cancelLobby(MinecraftServer server, Component why) {
        Lobby l = lobby;
        lobby = null;
        releaseStage(server);
        if (l == null) return;
        for (UUID id : l.fighters()) {
            ServerPlayer o = find(server, id);
            if (o != null) o.sendSystemMessage(why.copy().withStyle(ChatFormatting.GRAY));
        }
    }

    // ---- starting

    // the battle keeps its stage loaded until everyone has gone home: the players' own chunk tickets come a few at a time,
    // and a stage let go before they land unloads under the fighters and is read back from disk on the spot
    static Battle begin(MinecraftServer server, long now) {
        Battle b = start(server, now);
        boolean taken = b != null && b.slot == preloadedSlot && b.radius == preloadedRadius;
        if (!taken) releaseStage(server);
        preloadedSlot = -1;
        // for a stage taken over this only restarts its ticket's lifespan
        if (b != null) NullRealm.preload(b.level, b.slot, b.radius);
        return b;
    }

    private static void releaseStage(MinecraftServer server) {
        ServerLevel realm = NullRealm.level(server);
        if (preloadedSlot >= 0 && realm != null) NullRealm.release(realm, preloadedSlot, preloadedRadius);
        preloadedSlot = -1;
    }

    private static int freeSlot() {
        Set<Integer> used = new HashSet<>();
        for (Battle b : BATTLES) used.add(b.slot);
        return BattleMath.slot(used);
    }

    private static Battle start(MinecraftServer server, long now) {
        Lobby l = lobby;
        lobby = null;
        if (l == null) return null;
        List<ServerPlayer> fighters = new ArrayList<>();
        for (UUID id : l.fighters()) {
            ServerPlayer p = find(server, id);
            if (p == null) continue;
            String why = eligible(p);
            if (why != null) {
                refuse(p, why, reasonText(why));
                continue;
            }
            fighters.add(p);
        }
        ServerLevel realm = NullRealm.level(server);
        if (fighters.size() < BattleMath.MIN_FIGHTERS || realm == null) {
            for (ServerPlayer p : fighters) p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.battle.too_few",
                    "Not enough fighters made it, so the battle is off.").withStyle(ChatFormatting.GRAY));
            return null;
        }
        int slot = freeSlot();
        int radius = ZeroConfig.SERVER.stageRadius.get();
        NullRealm.build(realm, slot, radius);
        List<UUID> ids = new ArrayList<>();
        for (ServerPlayer p : fighters) ids.add(p.getUUID());
        Battle b = new Battle(new Fight(ids, now, ZeroConfig.SERVER.battleMinutes.get() * 1200), slot, radius, realm);
        List<Spot> spots = BattleMath.spread(fighters.size(), radius);
        Vec3 c = NullRealm.center(slot);
        Map<UUID, Integer> parties = new HashMap<>();
        for (int i = 0; i < fighters.size(); i++) {
            ServerPlayer p = fighters.get(i);
            StatsData d = stats(p);
            NullRealm.saveReturn(p, d != null && d.getSkills().isSkillActive("fly"));
            b.fist.put(p.getUUID(), d != null && d.getStatus().isFriendlyFistEnabled());
            b.players.put(p.getUUID(), p);
            LAST_HIT.remove(p.getUUID());
            prepare(p, d);
            Spot s = spots.get(i);
            NullRealm.place(p, realm, c.x + s.x(), c.y, c.z + s.z(), s.yaw(), 0f);
            p.fallDistance = 0;
            UUID party = PartyManager.getPartyId(p);
            if (party != null) parties.merge(party, 1, Integer::sum);
        }
        // party members fight each other here, so their party's friendly fire is lifted for the battle
        for (Map.Entry<UUID, Integer> e : parties.entrySet()) {
            if (e.getValue() < 2) continue;
            com.dmzzero.duel.PartyLift.lift(server, e.getKey());
            b.lifted.add(e.getKey());
        }
        BATTLES.add(b);
        Component rules = Component.translatableWithFallback("message.dmzzero.battle.rules",
                "Free-for-all, %s fighters. Last one standing wins. No flying, and don't fall off the stage.", fighters.size()).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : fighters) p.sendSystemMessage(rules);
        DMZZero.LOG.info("Null Realm battle started in slot {} with {} fighters", slot, fighters.size());
        return b;
    }

    private static void prepare(ServerPlayer p, StatsData data) {
        if (data != null) {
            if (data.getStatus().isKnockedDown()) CombatEvent.releaseKnockdown(p, data);
            data.getResources().setCurrentEnergy(data.getMaxEnergy());
            data.getResources().setCurrentStamina(data.getMaxStamina());
            data.getStatus().setFriendlyFistEnabled(true);
            data.getSkills().setSkillActive("fly", false);
            sync(p);
        }
        p.setHealth(p.getMaxHealth());
    }

    // ---- the fight

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
        if (lobby != null && lobby.expired(now)) {
            if (lobby.canStart()) begin(server, now);
            else cancelLobby(server, Component.translatableWithFallback("message.dmzzero.battle.nobody_came",
                    "Nobody else joined, so the battle is off."));
        }
        for (Iterator<Battle> it = BATTLES.iterator(); it.hasNext(); ) {
            Battle b = it.next();
            if (b.endedAt >= 0) {
                // whoever went out in the last moment still gets caught and seated before everyone goes home
                settle(server, b, now);
                if (now - b.endedAt >= RETURN_DELAY) {
                    finish(server, b);
                    it.remove();
                }
                continue;
            }
            tick(server, b, now);
        }
    }

    private static void tick(MinecraftServer server, Battle b, long now) {
        int say = b.fight.countdown().announce(now);
        if (say >= 0) {
            Component title = say > 0 ? Component.literal(String.valueOf(say)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    : Component.translatableWithFallback("title.dmzzero.duel.fight", "Fight!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            for (ServerPlayer p : online(server, b, b.fight.standing())) {
                Titles.show(p, title, Component.empty(), 0, say > 0 ? 18 : 20, say > 0 ? 2 : 10);
                p.playNotifySound(say > 0 ? SoundEvents.NOTE_BLOCK_HAT.value() : SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, say > 0 ? 1.0f : 0.7f);
            }
        }
        for (UUID id : b.fight.standing()) {
            ServerPlayer p = find(server, id);
            if (p == null) {
                eliminate(b, id, null, Out.LOGGED_OUT, null, now);
                continue;
            }
            if (p.level() != b.level) {
                eliminate(b, id, p, Out.LEFT, null, now);
                continue;
            }
            if (BattleMath.ringOut(p.getY(), NullRealm.STAND_Y)) {
                eliminate(b, id, p, Out.RING_OUT, credit(id, now, RING_OUT_CREDIT), now);
                continue;
            }
            if (b.fight.fighting(now) && knockedDown(p)) eliminate(b, id, p, Out.KNOCKDOWN, credit(id, now, Long.MAX_VALUE), now);
        }
        settle(server, b, now);
        if (b.fight.fighting(now) && now % 20 == 0) {
            int left = b.fight.standing().size();
            Component bar = Component.translatableWithFallback("message.dmzzero.battle.left", "%s left", left).withStyle(ChatFormatting.GOLD);
            for (ServerPlayer p : online(server, b, b.fight.standing())) p.displayClientMessage(bar, true);
        }
        Result r = b.fight.result(now);
        if (r != null) end(server, b, r, now);
    }

    // the knocked out and the ring-outs to the stands once their moment has passed
    private static void settle(MinecraftServer server, Battle b, long now) {
        for (Iterator<Map.Entry<UUID, Long>> it = b.toStands.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (now < e.getValue()) continue;
            it.remove();
            ServerPlayer p = find(server, e.getKey());
            if (p != null && p.level() == b.level) toStands(b, p);
        }
    }

    private static UUID credit(UUID victim, long now, long window) {
        Hit h = LAST_HIT.get(victim);
        return h != null && now - h.at <= window ? h.by : null;
    }

    private static void eliminate(Battle b, UUID id, ServerPlayer p, Out reason, UUID by, long now) {
        if (!b.fight.eliminate(id, reason, by)) return;
        String name = b.name(id);
        String byName = b.fight.by(id) != null ? b.name(b.fight.by(id)) : null;
        Component line = outLine(reason, name, byName);
        lastElimination = line.getString();
        for (ServerPlayer o : online(b.level.getServer(), b, b.fight.fighters())) {
            if (o.level() == b.level) o.sendSystemMessage(line);
        }
        if (p == null) return;
        switch (reason) {
            case KNOCKDOWN, RING_OUT -> {
                b.toStands.put(id, now + TO_STANDS_DELAY);
                Titles.show(p, Component.translatableWithFallback("title.dmzzero.battle.out", "Out!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        Component.translatableWithFallback("title.dmzzero.battle.watch", "Watch from the stands"), 0, 30, 10);
            }
            case LEFT -> {
                restore(b, p);
                NullRealm.clearReturn(p);
                b.home.add(id);
            }
            default -> {
            }
        }
        if (reason == Out.RING_OUT && b.fight.by(id) != null) {
            ServerPlayer winner = find(b.level.getServer(), b.fight.by(id));
            if (winner != null) Rivals.beaten(p, winner);
        }
    }

    private static Component outLine(Out reason, String name, String by) {
        MutableComponent line = switch (reason) {
            case KNOCKDOWN -> by != null
                    ? Component.translatableWithFallback("message.dmzzero.battle.out_knockdown_by", "%s is out: knocked down by %s.", name, by)
                    : Component.translatableWithFallback("message.dmzzero.battle.out_knockdown", "%s is out: knocked down.", name);
            case RING_OUT -> by != null
                    ? Component.translatableWithFallback("message.dmzzero.battle.out_ring_by", "%s is out: ring-out by %s.", name, by)
                    : Component.translatableWithFallback("message.dmzzero.battle.out_fell", "%s is out: fell off the stage.", name);
            case LEFT -> Component.translatableWithFallback("message.dmzzero.battle.out_left", "%s is out: left the Null Realm.", name);
            case LOGGED_OUT -> Component.translatableWithFallback("message.dmzzero.battle.out_logged", "%s is out: logged out.", name);
            case FORFEIT -> Component.translatableWithFallback("message.dmzzero.battle.out_forfeit", "%s is out: forfeited.", name);
        };
        return line.withStyle(ChatFormatting.YELLOW);
    }

    private static void toStands(Battle b, ServerPlayer p) {
        StatsData d = stats(p);
        if (d != null && d.getStatus().isKnockedDown()) {
            CombatEvent.releaseKnockdown(p, d);
            sync(p);
        }
        if (p.getHealth() < p.getMaxHealth() * 0.5f) p.setHealth(p.getMaxHealth() * 0.5f);
        Vec3 s = NullRealm.stands(b.slot, b.radius);
        double ox = (p.getRandom().nextDouble() - 0.5) * 4, oz = (p.getRandom().nextDouble() - 0.5) * 4;
        NullRealm.place(p, b.level, s.x + ox, s.y, s.z + oz, 0f, 10f);
        p.fallDistance = 0;
    }

    // someone off the stage falling into the void: back up to the stands
    static void catchFall(ServerPlayer p) {
        Battle b = battleOf(p);
        if (b == null || b.fight.standing(p.getUUID()) && b.endedAt < 0) return;
        toStands(b, p);
    }

    private static void end(MinecraftServer server, Battle b, Result r, long now) {
        b.endedAt = now;
        lastResult = r;
        int n = b.fight.fighters().size();
        for (UUID id : b.fight.fighters()) {
            ServerPlayer p = find(server, id);
            if (p == null) continue;
            // everyone goes home after RETURN_DELAY; their home spots load in the meantime
            NullRealm.preloadHome(p);
            boolean won = id.equals(r.winner());
            record(p, won, b.fight.kos(id));
            if (won) {
                // a win by walkover (everyone else left before going down) pays no mastery, as a duel forfeit doesn't
                if (n >= 2 && b.fight.kos(id) > 0) com.dmzzero.form.FightMastery.award(p, com.dmzzero.form.FightMastery.Win.BATTLE);
                Titles.show(p, Component.translatableWithFallback("title.dmzzero.battle.won", "Last One Standing").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        Component.translatableWithFallback("title.dmzzero.battle.kos", "%s knockouts", b.fight.kos(id)), 5, 60, 15);
                p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1.0f);
            } else if (r.timeUp() && r.survivors().contains(id)) {
                Titles.show(p, Component.translatableWithFallback("title.dmzzero.battle.time", "Time's Up").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                        Component.translatableWithFallback("title.dmzzero.battle.draw_standing", "Draw · %s still standing", r.survivors().size()), 5, 60, 15);
            } else if (r.draw() && r.survivors().isEmpty()) {
                Titles.show(p, Component.translatableWithFallback("title.dmzzero.duel.draw", "Draw").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                        Component.translatableWithFallback("title.dmzzero.battle.all_down", "Everyone went down"), 5, 60, 15);
            } else {
                Titles.show(p, Component.translatableWithFallback("title.dmzzero.battle.over", "Battle Over").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD),
                        Component.translatableWithFallback("title.dmzzero.battle.place", "%s of %s", BattleMath.ordinal(b.fight.place(id)), n), 5, 60, 15);
            }
        }
        MutableComponent summary;
        if (r.winner() != null) {
            summary = Component.translatableWithFallback("message.dmzzero.battle.summary_won", "%s is the last one standing, with %s knockouts.",
                    b.name(r.winner()), b.fight.kos(r.winner()));
        } else if (r.timeUp()) {
            List<String> names = new ArrayList<>();
            for (UUID id : r.survivors()) names.add(b.name(id));
            summary = Component.translatableWithFallback("message.dmzzero.battle.summary_time", "Time's up. Still standing: %s.", String.join(", ", names));
        } else {
            summary = Component.translatableWithFallback("message.dmzzero.battle.summary_all_down", "Everyone went down at once. It's a draw.");
        }
        summary.withStyle(ChatFormatting.GOLD).append(" ").append(Component.translatableWithFallback("message.dmzzero.battle.heading_home",
                "Everyone heads home in %s seconds.", RETURN_DELAY / 20).withStyle(ChatFormatting.GRAY));
        for (ServerPlayer p : online(server, b, b.fight.fighters())) p.sendSystemMessage(summary);
        DMZZero.LOG.info("Null Realm battle in slot {} ended: {}", b.slot, summary.getString());
    }

    private static void finish(MinecraftServer server, Battle b) {
        for (UUID id : b.fight.fighters()) {
            LAST_HIT.remove(id);
            if (b.home.contains(id)) continue;
            ServerPlayer p = find(server, id);
            if (p != null) goHome(b, p);
        }
        for (UUID party : b.lifted) com.dmzzero.duel.PartyLift.drop(server, party);
        b.lifted.clear();
        NullRealm.release(b.level, b.slot, b.radius);
    }

    private static void goHome(Battle b, ServerPlayer p) {
        restore(b, p);
        if (NullRealm.in(p)) NullRealm.sendHome(p);
        else NullRealm.clearReturn(p);
        b.home.add(p.getUUID());
    }

    private static void restore(Battle b, ServerPlayer p) {
        StatsData d = stats(p);
        if (d != null) {
            if (d.getStatus().isKnockedDown()) CombatEvent.releaseKnockdown(p, d);
            Boolean fist = b.fist.get(p.getUUID());
            if (fist != null) d.getStatus().setFriendlyFistEnabled(fist);
            sync(p);
        }
        if (p.getHealth() < p.getMaxHealth() * 0.5f) p.setHealth(p.getMaxHealth() * 0.5f);
    }

    private static void record(ServerPlayer p, boolean won, int kos) {
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        BattleMath.record(persisted, won, kos);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static int[] score(ServerPlayer p) {
        return BattleMath.score(p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG));
    }

    // ---- who can hit whom

    // two fighters still standing in the same battle: dmz's party rules don't make them friends here
    public static boolean foes(ServerPlayer a, ServerPlayer b) {
        if (BATTLES.isEmpty()) return false;
        Battle ba = battleOf(a);
        return ba != null && ba.endedAt < 0 && ba == battleOf(b) && ba.fight.standing(a.getUUID()) && ba.fight.standing(b.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (BATTLES.isEmpty() || event.getEntity().level().isClientSide) return;
        ServerPlayer attacker = Rivals.attacker(event.getSource());
        if (attacker == null || !(event.getEntity() instanceof ServerPlayer victim) || attacker == victim) return;
        Battle va = battleOf(victim), aa = battleOf(attacker);
        if (va == null && aa == null) return;
        long now = victim.server.getTickCount();
        if (va != null && va == aa && va.endedAt < 0 && va.fight.standing(victim.getUUID()) && va.fight.standing(attacker.getUUID())) {
            if (va.fight.fighting(now)) return;
            event.setCanceled(true);
            attacker.displayClientMessage(Component.translatableWithFallback("message.dmzzero.battle.wait", "Wait for the countdown.")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        event.setCanceled(true);
        Component why = aa != null && aa.fight.standing(attacker.getUUID())
                ? Component.translatableWithFallback("message.dmzzero.battle.focus", "Stick to the fighters on the stage.")
                : Component.translatableWithFallback("message.dmzzero.battle.stay_out", "%s is in a battle.", victim.getGameProfile().getName());
        attacker.displayClientMessage(why.copy().withStyle(ChatFormatting.GRAY), true);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (BATTLES.isEmpty() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        ServerPlayer attacker = Rivals.attacker(event.getSource());
        if (attacker == null || attacker == victim || !foes(attacker, victim)) return;
        LAST_HIT.put(victim.getUUID(), new Hit(attacker.getUUID(), victim.server.getTickCount()));
    }

    // anything that slips past friendly fist knocks down instead of killing
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (BATTLES.isEmpty() || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        Battle b = battleOf(victim);
        if (b == null || b.endedAt >= 0 || !b.fight.standing(victim.getUUID())) return;
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

    // ---- comings and goings

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        UUID id = p.getUUID();
        if (lobby != null && lobby.has(id)) {
            if (id.equals(lobby.opener)) {
                CALLED_OFF.put(id, (long) p.server.getTickCount());
                cancelLobby(p.server, Component.translatableWithFallback("message.dmzzero.battle.opener_left",
                        "%s logged out, so the battle is off.", p.getGameProfile().getName()));
            }
            else lobby.leave(id);
        }
        Battle b = battleOf(p);
        if (b != null) restore(b, p);
        LAST_HIT.remove(id);
    }

    // back mid-battle after logging out: you're out, so straight home
    static void loggedBackIn(ServerPlayer p) {
        Battle b = battleOf(p);
        if (b == null) return;
        if (b.endedAt < 0 && b.fight.standing(p.getUUID())) b.fight.eliminate(p.getUUID(), Out.LOGGED_OUT, null);
        goHome(b, p);
    }

    // the party team's friendly fire is saved with the world, which is written before ServerStoppedEvent
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Battle b : BATTLES) {
            for (UUID party : b.lifted) com.dmzzero.duel.PartyLift.drop(event.getServer(), party);
            b.lifted.clear();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // players left in the realm are sent home the next time they log in
        for (Battle b : BATTLES) {
            for (UUID id : b.fight.fighters()) {
                ServerPlayer p = b.players.get(id);
                if (p != null && !b.home.contains(id)) restore(b, p);
            }
        }
        BATTLES.clear();
        LAST_HIT.clear();
        TEST_PLAYERS.clear();
        CALLED_OFF.clear();
        lobby = null;
        // the tickets go with the server
        preloadedSlot = -1;
    }

    // ---- lookups

    static Battle battleOf(ServerPlayer p) {
        for (Battle b : BATTLES) if (b.holds(p.getUUID())) return b;
        return null;
    }

    // part of a battle that hasn't sent everyone home yet, fighting or watching
    public static boolean involved(ServerPlayer p) {
        return battleOf(p) != null;
    }

    public static boolean inLobby(ServerPlayer p) {
        return lobby != null && lobby.has(p.getUUID());
    }

    private static String eligible(ServerPlayer p) {
        StatsData d = stats(p);
        if (d == null || !d.getStatus().isHasCreatedCharacter()) return "no_character";
        if (!d.getStatus().isAlive()) return "dead";
        if (d.getStatus().isKnockedDown()) return "knocked_down";
        if (Duels.inDuel(p)) return "in_duel";
        if (battleOf(p) != null) return "in_battle";
        if (Tournament.Manager.runOf(p) != null) return "in_tournament";
        if (WorldBossManager.activeFor(p) != null) return "in_world_boss";
        return null;
    }

    private static String reasonText(String code) {
        return switch (code) {
            case "no_character" -> "You need a character first.";
            case "dead" -> "You can't fight while you're dead.";
            case "knocked_down" -> "Get back on your feet first.";
            case "in_duel" -> "Finish your duel first.";
            case "in_battle" -> "You're already in a battle.";
            case "in_tournament" -> "Finish your tournament first.";
            case "in_world_boss" -> "Finish the World Boss fight first.";
            default -> "You can't join right now.";
        };
    }

    private static List<ServerPlayer> audience(ServerPlayer opener) {
        int range = ZeroConfig.SERVER.battleAnnounceRange.get();
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : opener.server.getPlayerList().getPlayers()) {
            if (range <= 0 || (p.level() == opener.level() && p.distanceTo(opener) <= range)) out.add(p);
        }
        return out;
    }

    private static List<ServerPlayer> online(MinecraftServer server, Battle b, List<UUID> ids) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : ids) {
            if (b.home.contains(id)) continue;
            ServerPlayer p = find(server, id);
            if (p != null) out.add(p);
        }
        return out;
    }

    static ServerPlayer find(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) return p;
        ServerPlayer fake = TEST_PLAYERS.get(id);
        return fake != null && !fake.isRemoved() ? fake : null;
    }

    private static boolean knockedDown(ServerPlayer p) {
        StatsData d = stats(p);
        return d != null && d.getStatus().isKnockedDown();
    }

    private static Component button(String text, ChatFormatting color, String command, Component hover) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static String refuse(ServerPlayer who, String code, String text, Object... args) {
        who.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.battle." + code, text, args).withStyle(ChatFormatting.RED));
        return code;
    }

    private static StatsData stats(ServerPlayer p) {
        return StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
    }

    private static void sync(ServerPlayer p) {
        if (p.connection != null && !(p instanceof FakePlayer)) NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(p), p);
    }

    // ---- self-test hooks

    public static void addTestPlayer(ServerPlayer p) {
        TEST_PLAYERS.put(p.getUUID(), p);
    }

    public static void clearTest() {
        TEST_PLAYERS.clear();
        CALLED_OFF.clear();
    }

    // starts the open battle as of a given tick, so a test can run the clock itself
    public static boolean beginAt(MinecraftServer server, long now) {
        return begin(server, now) != null;
    }

    public static int slotOf(ServerPlayer p) {
        Battle b = battleOf(p);
        return b == null ? -1 : b.slot;
    }

    public static int kosOf(ServerPlayer p) {
        Battle b = battleOf(p);
        return b == null ? 0 : b.fight.kos(p.getUUID());
    }

    public static Out outReason(ServerPlayer p) {
        Battle b = battleOf(p);
        return b == null ? null : b.fight.reason(p.getUUID());
    }

    // ends everything at once and sends everyone home (self-test cleanup)
    public static void stopAll(MinecraftServer server) {
        long now = server.getTickCount();
        for (Battle b : BATTLES) {
            if (b.endedAt < 0) {
                b.endedAt = now;
            }
            finish(server, b);
        }
        BATTLES.clear();
        lobby = null;
        releaseStage(server);
    }

    public static int active() {
        return BATTLES.size();
    }

    public static Result lastResult() {
        return lastResult;
    }

    public static String lastElimination() {
        return lastElimination;
    }
}
