package com.dmzzero.impact;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.stats.extras.ActionMode;
import com.dragonminez.common.util.TransformationsHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.dmzzero.impact.ImpactProfile.*;

@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class ImpactServer {
    private static final int COST = TickCost.id("fighting.impacts", () -> ImpactServer.STATES.size());
    static final int LINGER_TICKS = 100;
    // a first-time form rings out twice
    static final int ECHO_TICKS = 10;
    private static final Map<UUID, State> STATES = new HashMap<>();

    private ImpactServer() {
    }

    record Target(FormConfig.FormData form, String group, boolean stack, ImpactProfile profile) {
        String key() {
            return (stack ? "s|" : "f|") + group + "|" + form.getName();
        }
    }

    static final class Crack {
        final BlockPos pos;
        final int slot;
        final int id;
        final long born;
        int stage;

        Crack(BlockPos pos, int slot, int id, long born) {
            this.pos = pos;
            this.slot = slot;
            this.id = id;
            this.born = born;
        }
    }

    static final class State {
        final List<Crack> cracks = new ArrayList<>();
        ServerLevel crackLevel;
        long crackExpire = -1;
        int auraTicks;
        int lightningCooldown;
        long nextPulse;
        float progress;
        String chargeKey;
        long echoAt = -1;
        ImpactProfile echo;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        long start = TickCost.start();
        playerTick(event);
        TickCost.add(COST, start);
    }

    private static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        tick(player);
    }

    @SubscribeEvent
    public static void onFormChange(DMZEvent.FormChangeEvent event) {
        reached(event.getPlayer(), event.getOldGroup(), event.getOldForm(), event.getNewGroup(), event.getNewForm(), false);
    }

    @SubscribeEvent
    public static void onStackFormChange(DMZEvent.StackFormChangeEvent event) {
        reached(event.getPlayer(), event.getOldGroup(), event.getOldForm(), event.getNewGroup(), event.getNewForm(), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        State s = STATES.remove(event.getEntity().getUUID());
        if (s != null) clearCracks(s);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
        ImpactLookup.clearCache();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ImpactCommand.register(event.getDispatcher());
    }

    static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    static void tick(ServerPlayer player) {
        StatsData data = player.isAlive() && !player.isSpectator()
                ? StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null) : null;
        long now = player.server.getTickCount();
        if (data == null || !data.getStatus().isHasCreatedCharacter()) {
            State s = STATES.get(player.getUUID());
            if (s != null) settle(s, now);
            return;
        }
        State s = state(player);
        ServerLevel level = player.serverLevel();
        if (s.echoAt >= 0 && now >= s.echoAt) {
            if (s.echo != null) ImpactWorld.shockwave(level, player, s.echo);
            s.echoAt = -1;
            s.echo = null;
        }
        Target target = ZeroConfig.SERVER.transformations.get() ? target(data) : null;
        if (target != null) {
            s.auraTicks = 0;
            transforming(level, player, s, target, data.getResources().getActionCharge() / 100f, now);
        } else if (ZeroConfig.SERVER.auras.get() && charging(data.getStatus())) {
            s.chargeKey = null;
            charging(level, player, data, s, now);
        } else {
            s.chargeKey = null;
            s.auraTicks = 0;
            settle(s, now);
        }
    }

    static boolean charging(Status status) {
        return status.isChargingKi() && !status.isDescending() && !status.isActionCharging();
    }

    static Target target(StatsData data) {
        Status status = data.getStatus();
        if (!status.isActionCharging()) return null;
        Character c = data.getCharacter();
        ActionMode mode = status.getSelectedAction();
        FormConfig.FormData next;
        String group;
        boolean stack;
        if (mode == ActionMode.FORM) {
            next = TransformationsHelper.getNextAvailableForm(data);
            group = TransformationsHelper.getTransformTargetGroup(data);
            stack = false;
        } else if (mode == ActionMode.STACK) {
            next = TransformationsHelper.getNextAvailableStackForm(data);
            group = c.hasActiveStackForm() ? c.getActiveStackFormGroup() : c.getSelectedStackFormGroup();
            stack = true;
        } else {
            return null;
        }
        if (next == null || group == null) return null;
        // the form being reached decides how wild it gets, and how well the player knows it tames it
        ImpactProfile profile = ImpactLookup.forPlayer(data, group, next, stack);
        return profile == null ? null : new Target(next, group, stack, profile);
    }

    static void transforming(ServerLevel level, ServerPlayer player, State s, Target t, float rawCharge, long now) {
        float raw = Mth.clamp(rawCharge, 0f, 1f);
        String key = t.key();
        if (!key.equals(s.chargeKey)) {
            s.chargeKey = key;
            s.progress = raw;
        } else {
            // dmz fills the charge once a second, this eases between the steps
            s.progress += (Math.min(1f, raw + 0.1f) - s.progress) * 0.08f;
            if (s.progress < raw || s.progress > raw + 0.25f) s.progress = raw;
        }
        float progress = s.progress;
        ImpactProfile p = t.profile();
        s.crackExpire = -1;

        if (p.has(CRACKS) && ZeroConfig.SERVER.cracks.get()) growCracks(level, player, s, p.tier(), progress, now);
        if (p.has(LIGHTNING)) maybeStrike(level, player, s, p.tier(), ImpactMath.lightningChance(p.tier(), progress));
        if (p.has(LIGHT) && progress >= 0.75f && now >= s.nextPulse) {
            ImpactWorld.lightPulse(level, player, p.tier(), false);
            s.nextPulse = now + (long) (14 - 8 * progress);
        } else if (p.has(DARK) && progress >= 0.5f && now >= s.nextPulse) {
            ImpactWorld.darkPulse(level, player, p.tier(), false);
            s.nextPulse = now + 8;
        }
        if (p.destructive() && progress >= 0.35f && now % 8 == 0 && ImpactWorld.destructionAllowed(player)) {
            ImpactWorld.scatter(level, player, ImpactMath.breakRadius(p.tier()) * progress, 16 + 8 * p.tier(), 1 + p.tier() / 2);
        }
        if (progress >= 0.2f) water(level, player, p.tier(), progress, now);
    }

    // water answers to power, calm forms included
    static void water(ServerLevel level, ServerPlayer player, int tier, float strength, long now) {
        if (now % 3 != 0 || !ZeroConfig.SERVER.water.get()) return;
        if (player.isInWater()) ImpactWorld.boil(level, player, strength);
        else ImpactWorld.spray(level, player.position(), 1.5 + (1.5 + tier) * strength, 3 + tier, strength);
    }

    static void charging(ServerLevel level, ServerPlayer player, StatsData data, State s, long now) {
        s.auraTicks++;
        float intensity = ImpactMath.auraIntensity(s.auraTicks);
        ImpactProfile p = ImpactLookup.current(data);
        s.crackExpire = -1;

        // any form that isn't calm cracks the ground when charging, even ones whose transformation doesn't
        if (p.destructive() && ZeroConfig.SERVER.cracks.get() && intensity >= 0.4f) {
            growCracks(level, player, s, p.tier(), (intensity - 0.4f) / 0.6f, now);
        }
        if (p.has(LIGHTNING) && p.tier() >= 2) maybeStrike(level, player, s, p.tier(), 0.5 * ImpactMath.lightningChance(p.tier(), intensity));
        if (p.destructive() && intensity >= 0.5f && now % 10 == 0 && ImpactWorld.destructionAllowed(player)) {
            ImpactWorld.scatter(level, player, ImpactMath.breakRadius(p.tier()) * intensity, 12 + 6 * p.tier(), 1);
            if (p.tier() >= 3 && intensity >= 1f && now % 40 == 0 && !s.cracks.isEmpty() && s.crackLevel == level) {
                ImpactWorld.degrade(level, player, s.cracks.get(level.random.nextInt(s.cracks.size())).pos);
            }
        }
        if (intensity >= 0.3f) water(level, player, p.tier(), intensity, now);
    }

    // true when this ascent was the character's first time in the form
    static boolean reached(ServerPlayer player, String oldGroup, String oldForm, String newGroup, String newForm, boolean stack) {
        if (player == null || !player.isAlive()) return false;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (data == null) return false;
        String race = data.getCharacter().getRaceName();
        if (!ImpactLookup.isAscent(race, oldGroup, oldForm, newGroup, newForm, stack)) return false;
        boolean first = firstTime(player, data, newGroup, newForm, stack);
        com.dmzzero.sense.PowerSense.onAscent(player);
        ImpactProfile p = ImpactLookup.forPlayer(data, newGroup, newForm, stack);
        com.dmzzero.form.TransformBurst.burst(player, p == null ? 1 : p.tier());
        if (p == null) return first;
        if (first) p = new ImpactProfile(Math.min(5, p.tier() + 1), p.flags());
        State s = state(player);
        if (ZeroConfig.SERVER.transformations.get()) {
            burst(player, s, p);
            if (first) {
                s.echoAt = player.server.getTickCount() + ECHO_TICKS;
                s.echo = p;
            }
        }
        if (ZeroConfig.SERVER.water.get()) ImpactWorld.splashRing(player.serverLevel(), player.position(), p.tier());
        if (first) FirstMoment.celebrate(player, data, newGroup, newForm, stack, p);
        return first;
    }

    static boolean firstTime(ServerPlayer player, StatsData data, String group, String form, boolean stack) {
        var persisted = FirstForms.persisted(player);
        String key = FirstForms.key(group, form, stack);
        boolean first = FirstForms.isFirst(persisted, key, ImpactLookup.rawMastery(data, group, form, stack));
        FirstForms.mark(persisted, key);
        FirstForms.save(player, persisted);
        return first && ZeroConfig.SERVER.firstTime.get();
    }

    static void burst(ServerPlayer player, State s, ImpactProfile p) {
        ServerLevel level = player.serverLevel();
        long now = player.server.getTickCount();
        int tier = p.tier();
        if (p.has(SHOCKWAVE)) ImpactWorld.shockwave(level, player, p);
        if (p.has(LIGHT)) ImpactWorld.lightPulse(level, player, tier, true);
        if (p.has(DARK)) ImpactWorld.darkPulse(level, player, tier, true);
        if (p.has(LIGHTNING) && ZeroConfig.SERVER.lightning.get()) {
            for (int i = 0; i < 1 + tier / 2; i++) ImpactWorld.strikeLightning(level, player, tier);
        }
        if (p.has(CRACKS) && ZeroConfig.SERVER.cracks.get()) {
            if (s.crackLevel != level) clearCracks(s);
            s.crackLevel = level;
            int want = ImpactMath.maxCracks(tier);
            for (int i = 0; i < want * 3 && s.cracks.size() < want; i++) addCrack(level, player, s, ImpactMath.crackRadius(tier), now);
            for (Crack c : s.cracks) setStage(level, c, 9);
            s.crackExpire = now + LINGER_TICKS;
        }
        if (p.destructive() && ImpactWorld.destructionAllowed(player)) {
            ImpactWorld.shatterAll(level, player, ImpactMath.shockwaveRadius(tier), 48 + 16 * tier);
            Leaves.strip(level, player, player.position().add(0, 1.0, 0), ImpactMath.shockwaveRadius(tier), tier);
            if (p.has(CRACKS) && tier >= 3 && s.crackLevel == level) {
                float chance = tier >= 4 ? 0.75f : 0.5f;
                for (Crack c : s.cracks) if (level.random.nextFloat() < chance) ImpactWorld.degrade(level, player, c.pos);
            }
        }
    }

    private static void maybeStrike(ServerLevel level, ServerPlayer player, State s, int tier, double chance) {
        if (!ZeroConfig.SERVER.lightning.get()) return;
        if (s.lightningCooldown > 0) {
            s.lightningCooldown--;
            return;
        }
        if (level.random.nextDouble() < chance && ImpactWorld.strikeLightning(level, player, tier)) s.lightningCooldown = 12;
    }

    static void growCracks(ServerLevel level, ServerPlayer player, State s, int tier, float progress, long now) {
        if (s.crackLevel != level) {
            clearCracks(s);
            s.crackLevel = level;
        }
        int max = ImpactMath.maxCracks(tier);
        int want = Math.min(max, Math.round(max * (0.25f + 0.75f * Mth.clamp(progress, 0f, 1f))));
        if (s.cracks.size() < want && now % 2 == 0) addCrack(level, player, s, ImpactMath.crackRadius(tier), now);
        int cap = ImpactMath.crackStageCap(progress);
        for (Crack c : s.cracks) {
            if (c.stage < cap && (now - c.born) % 4 == 0) setStage(level, c, c.stage + 1);
        }
    }

    private static void addCrack(ServerLevel level, ServerPlayer player, State s, double radius, long now) {
        int slot = freeSlot(s);
        if (slot < 0) return;
        BlockPos pos = ImpactWorld.groundNear(level, player, 1.0, radius);
        if (pos == null) return;
        for (Crack c : s.cracks) if (c.pos.equals(pos)) return;
        Crack crack = new Crack(pos, slot, ImpactMath.crackId(player.getId(), slot), now);
        s.cracks.add(crack);
        level.destroyBlockProgress(crack.id, pos, 0);
    }

    private static int freeSlot(State s) {
        outer:
        for (int slot = 0; slot < ImpactMath.MAX_CRACKS; slot++) {
            for (Crack c : s.cracks) if (c.slot == slot) continue outer;
            return slot;
        }
        return -1;
    }

    private static void setStage(ServerLevel level, Crack c, int stage) {
        c.stage = Math.min(9, stage);
        level.destroyBlockProgress(c.id, c.pos, c.stage);
    }

    static void settle(State s, long now) {
        if (s.cracks.isEmpty()) return;
        if (s.crackExpire < 0) s.crackExpire = now + LINGER_TICKS;
        else if (now >= s.crackExpire) clearCracks(s);
    }

    static void clearCracks(State s) {
        if (s.crackLevel != null) {
            for (Crack c : s.cracks) s.crackLevel.destroyBlockProgress(c.id, c.pos, -1);
        }
        s.cracks.clear();
        s.crackExpire = -1;
    }

    // a crowd giving one player ki: the ground under them starts to give
    public static void groupShare(ServerPlayer player, int tier, float progress, long now) {
        if (!ZeroConfig.SERVER.cracks.get()) return;
        State s = state(player);
        s.crackExpire = -1;
        growCracks(player.serverLevel(), player, s, tier, progress, now);
    }

    public static void clearCracks(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s != null) clearCracks(s);
    }

    public static int crackCount(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s == null ? 0 : s.cracks.size();
    }
}
