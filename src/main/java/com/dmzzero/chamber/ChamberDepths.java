package com.dmzzero.chamber;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.network.ChamberStateS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.server.world.dimension.HTCDimension;
import com.dragonminez.server.world.structure.helper.DMZStructures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.dmzzero.chamber.ChamberMath.*;

// the farther from the house, the harsher the chamber and the more it pays
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class ChamberDepths {
    private static final int COST = TickCost.id("world.timeChamber", () -> ChamberDepths.STATES.size());
    // where dmz puts the house (read off a real world), for worlds where its structure can't be looked up
    private static final Box FALLBACK = new Box(-62, -31, 80, 49);
    private static final int RETRY_TICKS = 600;
    private static long retryAt;
    private static final int RECENT_FIGHT = 60;
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static Box house;
    private static boolean houseFound;
    private static int announced;
    private static int lastAnnounced = SILENT;

    private ChamberDepths() {
    }

    static final class State {
        int zone = -1;
        int air;
        boolean winded;
        double temperature = COMFORT;

        State(int air) {
            this.air = air;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        long start = TickCost.start();
        playerTick(event);
        TickCost.add(COST, start);
    }

    private static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (!applies(p)) {
            leave(p);
            return;
        }
        step(p, p.serverLevel().getGameTime() + testOffset);
    }

    // the dev autotest moves the chamber's clock to reach a cold spell without waiting for one
    private static long testOffset;

    public static void setTestOffset(long offset) {
        testOffset = offset;
    }

    private static boolean applies(ServerPlayer p) {
        return ZeroConfig.SERVER.chamberDepths.get() && inChamber(p) && !p.isCreative() && !p.isSpectator();
    }

    public static boolean inChamber(Player p) {
        return p.level().dimension().equals(HTCDimension.HTC_KEY);
    }

    // one tick of the chamber's weather on a player; public so the self-test can drive players that don't tick
    public static void step(ServerPlayer p, long now) {
        State s = STATES.computeIfAbsent(p.getUUID(), k -> new State(p.getAirSupply()));
        StatsData data = stats(p);
        int zone = zoneOf(p);
        if (zone != s.zone) {
            int line = descentLine(s.zone, zone);
            if (line != SILENT) announce(p, line);
            s.zone = zone;
        }
        double temp = temperature(now, ZeroConfig.SERVER.chamberCycleMinutes.get() * 1200, zone, data != null && aura(data));
        s.temperature = temp;

        if (p.canFreeze()) {
            int frozen = p.getTicksFrozen();
            int next = frostStep(frozen, frostTarget(temp));
            if (next != frozen) p.setTicksFrozen(next);
        }
        if (now % 20 == 0) {
            float hunger = exhaustion(temp);
            if (hunger > 0) p.causeFoodExhaustion(hunger);
        }

        boolean breathless = data != null && breathless(data.getCharacter().getRace());
        boolean thin = ZeroConfig.SERVER.chamberThinAir.get() && thinAir(zone) && !breathless && !p.isEyeInFluid(FluidTags.WATER);
        if (thin) {
            s.air = airStep(s.air, zone, exerting(p, data), false, now);
            // vanilla refills air out of water every tick, so the chamber's air is set back after it
            p.setAirSupply(s.air);
        } else {
            s.air = p.getAirSupply();
        }
        boolean was = s.winded;
        s.winded = thin && winded(was, s.air);
        if (s.winded) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, true, false, true));
            p.setSprinting(false);
        }

        if (now % 20 == 0) ZeroNetwork.send(p, new ChamberStateS2C(zone, (float) temp, (float) tpFor(zone), s.winded));
    }

    private static void announce(ServerPlayer p, int line) {
        announced++;
        lastAnnounced = line;
        p.displayClientMessage(descentText(line).copy().withStyle(ChatFormatting.GRAY), true);
    }

    public static Component descentText(int line) {
        return line == HEAVY
                ? Component.translatableWithFallback("message.dmzzero.chamber.heavy", "The air gets heavy.. it's hard to breathe.")
                : Component.translatableWithFallback("message.dmzzero.chamber.heavier", "heavier...");
    }

    public static String times(double multiplier) {
        String text = String.format(Locale.US, "%.2f", multiplier);
        if (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        return text;
    }

    private static boolean exerting(ServerPlayer p, StatsData data) {
        if (p.isSprinting()) return true;
        if (p.tickCount - p.getLastHurtMobTimestamp() < RECENT_FIGHT) return true;
        if (p.tickCount - p.getLastHurtByMobTimestamp() < RECENT_FIGHT) return true;
        if (data == null) return false;
        if (data.getStatus().isChargingKi()) return true;
        return data.getSkills().isSkillActive("fly") && !p.onGround();
    }

    private static boolean aura(StatsData data) {
        var s = data.getStatus();
        return s.isAuraActive() || s.isPermanentAura() || s.isForcedAura();
    }

    public static int zoneOf(Player p) {
        if (!(p.level() instanceof ServerLevel level)) return HOUSE;
        double d = house(level).distance(p.getX(), p.getZ());
        return zone(d, ZeroConfig.SERVER.chamberOutAt.get(), ZeroConfig.SERVER.chamberDeepAt.get(), ZeroConfig.SERVER.chamberFarAt.get());
    }

    private static double tpFor(int zone) {
        return tp(zone, ZeroConfig.SERVER.chamberOutTp.get(), ZeroConfig.SERVER.chamberDeepTp.get(), ZeroConfig.SERVER.chamberFarTp.get());
    }

    // what the chamber adds to a TP gain right now
    public static double tpMultiplier(Player p) {
        if (!ZeroConfig.SERVER.chamberDepths.get() || !inChamber(p)) return 1.0;
        return tpFor(zoneOf(p));
    }

    // the house is one structure fixed at the origin; its box is read once from the structure start there
    public static Box house(ServerLevel level) {
        if (house != null && (houseFound || level.getGameTime() < retryAt)) return house;
        boolean first = house == null;
        Box found = null;
        try {
            Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(DMZStructures.TIMECHAMBER);
            if (structure != null) {
                ChunkAccess chunk = level.getChunk(0, 0, ChunkStatus.STRUCTURE_STARTS);
                StructureStart start = chunk.getStartForStructure(structure);
                if (start != null && start.isValid()) {
                    BoundingBox bb = start.getBoundingBox();
                    found = new Box(bb.minX(), bb.minZ(), bb.maxX() + 1, bb.maxZ() + 1);
                }
            }
        } catch (RuntimeException e) {
            DMZZero.LOG.warn("Couldn't look up the Time Chamber house: {}", e.toString());
        }
        houseFound = found != null;
        house = houseFound ? found : FALLBACK;
        // not there yet (or structures are off): use dmz's usual spot and look again later
        if (!houseFound) retryAt = level.getGameTime() + RETRY_TICKS;
        if (first || houseFound) DMZZero.LOG.info("Time Chamber house {}: {}", houseFound ? "found" : "not found, assuming", house);
        return house;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onStaminaRegen(DMZEvent.StaminaRegenEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        if (s.winded) {
            event.setAmount(0);
            return;
        }
        double factor = staminaFactor(s.temperature);
        if (factor < 1.0) event.setAmount(event.getAmount() * factor);
    }

    private static void leave(ServerPlayer p) {
        if (STATES.remove(p.getUUID()) == null) return;
        ZeroNetwork.send(p, ChamberStateS2C.left());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
        house = null;
        houseFound = false;
        retryAt = 0;
    }

    public static void forget(ServerPlayer p) {
        STATES.remove(p.getUUID());
    }

    public static boolean houseFound() {
        return houseFound;
    }

    public static boolean isWinded(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.winded;
    }

    public static double temperatureOf(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null ? COMFORT : s.temperature;
    }

    public static int announced() {
        return announced;
    }

    public static int lastAnnounced() {
        return lastAnnounced;
    }

    private static StatsData stats(ServerPlayer p) {
        return StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
    }
}
