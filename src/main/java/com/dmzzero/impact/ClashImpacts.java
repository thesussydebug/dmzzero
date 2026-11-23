package com.dmzzero.impact;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.kishare.StreamMath;
import com.dmzzero.network.ImpactPulseS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// beam and strike clashes, from dmz and overhaul alike, tear up the ground under the clash point
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class ClashImpacts {
    private static final int COST = TickCost.id("fighting.impacts", () -> ClashImpacts.ACTIVE.size() + ClashImpacts.FADING.size());
    // the clash point can hang this far above the ground and still crack it
    private static final int GROUND_REACH = 8;
    private static final int STALE_TICKS = 5;
    private static final double PULSE_RANGE = 40;
    private static final Map<Object, Clash> ACTIVE = new IdentityHashMap<>();
    private static final List<Clash> FADING = new ArrayList<>();
    private static int nextId;
    private static int ended;
    private static int colored;
    private static int standoffsEnded;
    // a standoff only starts breaking things once it has dragged on this long
    private static final int STANDOFF_BREAK_AGE = 80;
    private static final double STANDOFF_PULSE_RANGE = 24;

    private ClashImpacts() {
    }

    static final class Clash {
        final ServerLevel level;
        final int tier;
        final ServerPlayer griefer;
        // two auras facing off before anyone swings: gentler, slower, and it fades instead of blowing up
        final boolean standoff;
        final List<ImpactServer.Crack> cracks = new ArrayList<>();
        // the two sides and their colors: each beam's, or each fighter's aura
        List<LivingEntity> fighters = List.of();
        int[] colors = new int[0];
        Vec3 point;
        BlockPos ground;
        int age;
        long lastTick;
        long crackExpire = -1;

        Clash(ServerLevel level, int tier, ServerPlayer griefer, boolean standoff) {
            this.level = level;
            this.tier = tier;
            this.griefer = griefer;
            this.standoff = standoff;
        }
    }

    public static void tick(Object key, ServerLevel level, Vec3 point, Supplier<List<LivingEntity>> sides) {
        tick(key, level, point, sides, level.getServer().getTickCount());
    }

    static void tick(Object key, ServerLevel level, Vec3 point, Supplier<List<LivingEntity>> sides, long now) {
        tick(key, level, point, sides, now, false);
    }

    public static void standoff(Object key, ServerLevel level, Vec3 point, Supplier<List<LivingEntity>> sides) {
        tick(key, level, point, sides, level.getServer().getTickCount(), true);
    }

    public static void tick(Object key, ServerLevel level, Vec3 point, Supplier<List<LivingEntity>> sides, long now, boolean standoff) {
        tick(key, level, point, sides, null, now, standoff);
    }

    /** A beam clash: colors are the two beams' own, in the order of the sides. */
    public static void tick(Object key, ServerLevel level, Vec3 point, Supplier<List<LivingEntity>> sides, Supplier<int[]> colors) {
        tick(key, level, point, sides, colors, level.getServer().getTickCount(), false);
    }

    static void tick(Object key, ServerLevel level, Vec3 point, Supplier<List<LivingEntity>> sides, Supplier<int[]> colors, long now, boolean standoff) {
        if (point == null || !(standoff ? ZeroConfig.SERVER.standoffs.get() : ZeroConfig.SERVER.clashes.get())) return;
        Clash c = ACTIVE.get(key);
        if (c == null || c.level != level) {
            List<LivingEntity> fighters = sides.get();
            c = new Clash(level, tier(fighters), griefer(fighters), standoff);
            c.fighters = fighters;
            c.colors = colors != null ? colors.get() : auraColors(fighters);
            ACTIVE.put(key, c);
        }
        c.point = point;
        c.age++;
        c.lastTick = now;
        c.ground = ImpactWorld.groundAt(level, Mth.floor(point.x), Mth.floor(point.y), Mth.floor(point.z), GROUND_REACH);
        float push = c.standoff ? StandoffMath.push(c.age) : Math.min(1f, c.age / 60f);

        if (c.ground != null && ZeroConfig.SERVER.cracks.get()) grow(c, now, push);
        if (c.age % 4 == 0) {
            if (c.standoff) {
                int n = 1 + Math.round((2 + c.tier) * push);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, n, 0.25, 0.4, 0.25, 0.25);
                level.sendParticles(ParticleTypes.FIREWORK, point.x, point.y, point.z, Math.max(1, n / 2), 0.15, 0.25, 0.15, 0.03);
            } else {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 4 + c.tier, 0.3, 0.3, 0.3, 0.4);
            }
            colorSparks(c, c.standoff ? 1 + Math.round((1 + c.tier) * push) : 3 + c.tier);
            if (c.ground != null) {
                double r = ImpactMath.clashRadius(c.tier, c.age) * 0.4;
                int dust = c.standoff ? 1 + Math.round((2 + c.tier) * push) : 3 + c.tier;
                level.sendParticles(MainParticles.DUST.get(), point.x, c.ground.getY() + 1.1, point.z, dust, r, 0.1, r, 0.08);
            }
        }
        if (c.age % 10 == 0) {
            if (c.standoff) {
                ZeroNetwork.sendAround(level, point, STANDOFF_PULSE_RANGE, new ImpactPulseS2C(point.x, point.y, point.z,
                        (0.15f + 0.08f * c.tier) * push, (float) STANDOFF_PULSE_RANGE, 0, 0f, 0f));
            } else {
                ZeroNetwork.sendAround(level, point, PULSE_RANGE, new ImpactPulseS2C(point.x, point.y, point.z,
                        (0.3f + 0.15f * c.tier) * (0.5f + 0.5f * push), (float) PULSE_RANGE, 0, 0f, 0f));
            }
        }
        boolean breaks = !c.standoff || c.age >= STANDOFF_BREAK_AGE;
        if (breaks && c.griefer != null && c.age % 8 == 0 && ImpactWorld.destructionAllowed(c.griefer)) {
            ImpactWorld.scatter(level, c.griefer, point, ImpactMath.clashRadius(c.tier, c.age), 10 + 4 * c.tier, c.standoff ? 1 : 2);
        }
        float bolt = c.standoff ? (c.age >= STANDOFF_BREAK_AGE ? 0.004f : 0f) : 0.01f;
        if (c.tier >= 4 && ZeroConfig.SERVER.lightning.get() && level.random.nextFloat() < bolt * c.tier) {
            ImpactWorld.strikeLightning(level, point, c.tier - 2);
        }
        if (ZeroConfig.SERVER.water.get() && c.age % 3 == 0) {
            ImpactWorld.spray(level, point, ImpactMath.clashRadius(c.tier, c.age), 2 + c.tier, push);
        }
    }

    public static boolean isActive(Object key) {
        return ACTIVE.containsKey(key);
    }

    public static int standoffsEnded() {
        return standoffsEnded;
    }

    // the standoff broke up: no blast, the cracks stay a while and fade
    private static void settle(Clash c, long now) {
        standoffsEnded++;
        if (c.ground != null) {
            c.level.sendParticles(MainParticles.DUST.get(), c.point.x, c.ground.getY() + 1.1, c.point.z, 6 + 2 * c.tier, 0.8, 0.1, 0.8, 0.05);
        }
        if (!c.cracks.isEmpty()) {
            c.crackExpire = now + ImpactServer.LINGER_TICKS;
            FADING.add(c);
        }
    }

    // decisive: someone won, otherwise the beams just fizzled out
    public static void end(Object key, boolean decisive) {
        Clash c = ACTIVE.get(key);
        if (c != null) end(key, decisive, c.level.getServer().getTickCount());
    }

    public static void end(Object key, boolean decisive, long now) {
        Clash c = ACTIVE.remove(key);
        if (c == null || c.point == null) return;
        if (c.standoff) {
            settle(c, now);
            return;
        }
        ServerLevel level = c.level;
        Vec3 at = c.point;
        int tier = decisive ? c.tier : Math.max(1, c.tier - 1);
        ended++;
        ImpactWorld.shockwave(level, c.ground != null ? Vec3.atCenterOf(c.ground).add(0, 0.65, 0) : at,
                new ImpactProfile(tier, ImpactProfile.SHOCKWAVE));
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, decisive ? 1 : 0, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        colorBurst(c, 10 + 4 * tier);
        ZeroNetwork.sendAround(level, at, PULSE_RANGE + 16, new ImpactPulseS2C(at.x, at.y, at.z,
                1.0f + 0.4f * tier, (float) PULSE_RANGE + 16, 0xFFFFFF, decisive ? 0.35f : 0.15f, decisive ? 0.08f : 0f));
        if (c.griefer != null && ImpactWorld.destructionAllowed(c.griefer)) {
            ImpactWorld.shatterAll(level, c.griefer, at, ImpactMath.shockwaveRadius(tier), 32 + 12 * tier);
            if (tier >= 3) {
                for (ImpactServer.Crack crack : c.cracks) {
                    if (level.random.nextFloat() < 0.5f) ImpactWorld.degrade(level, c.griefer, crack.pos);
                }
            }
        }
        if (ZeroConfig.SERVER.water.get()) ImpactWorld.splashRing(level, at, tier);
        for (ImpactServer.Crack crack : c.cracks) stage(level, crack, 9);
        if (!c.cracks.isEmpty()) {
            c.crackExpire = now + ImpactServer.LINGER_TICKS;
            FADING.add(c);
        }
    }

    private static void grow(Clash c, long now, float push) {
        int max = ImpactMath.maxCracks(c.tier);
        int want = Math.min(max, Math.round(max * (0.2f + 0.8f * push)));
        if (c.cracks.size() < want && now % 3 == 0) {
            BlockPos pos = ImpactWorld.groundNear(c.level, Vec3.atCenterOf(c.ground).add(0, 0.5, 0), 0.0, ImpactMath.clashRadius(c.tier, c.age), 3);
            boolean taken = pos == null;
            for (ImpactServer.Crack existing : c.cracks) if (existing.pos.equals(pos)) taken = true;
            if (!taken) {
                ImpactServer.Crack crack = new ImpactServer.Crack(pos, c.cracks.size(), id(), now);
                c.cracks.add(crack);
                c.level.destroyBlockProgress(crack.id, pos, 0);
            }
        }
        int cap = ImpactMath.crackStageCap(push);
        for (ImpactServer.Crack crack : c.cracks) {
            if (crack.stage < cap && (now - crack.born) % 4 == 0) stage(c.level, crack, crack.stage + 1);
        }
    }

    private static void stage(ServerLevel level, ImpactServer.Crack crack, int stage) {
        crack.stage = Math.min(9, stage);
        level.destroyBlockProgress(crack.id, crack.pos, crack.stage);
    }

    // breaker ids clear of the ones player cracks use
    private static int id() {
        nextId = (nextId + 1) % 200_000_000;
        return -(1_930_000_000 + nextId);
    }

    static int tier(List<LivingEntity> fighters) {
        int[] tiers = new int[fighters.size()];
        for (int i = 0; i < fighters.size(); i++) {
            StatsData data = fighters.get(i) instanceof ServerPlayer p ? StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null) : null;
            tiers[i] = data != null ? ImpactLookup.current(data).tier() : 3;
        }
        return ImpactMath.clashTier(tiers);
    }

    static int[] auraColors(List<LivingEntity> fighters) {
        int[] out = new int[fighters.size()];
        for (int i = 0; i < out.length; i++) {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, fighters.get(i)).orElse(null);
            out[i] = data == null ? 0xFFFFFF : StreamMath.hex(data.getCharacter().getActiveAuraColor(), 0xFFFFFF);
        }
        return out;
    }

    // each side's sparks on its own side of the clash point, both colors blending in the middle
    private static void colorSparks(Clash c, int perSide) {
        if (!ZeroConfig.SERVER.clashColors.get() || c.colors.length < 2 || c.fighters.size() < 2 || perSide <= 0) return;
        float size = ClashColors.dustSize(c.tier);
        double[] at = {c.point.x, c.point.y, c.point.z};
        for (int i = 0; i < 2; i++) {
            LivingEntity side = c.fighters.get(i);
            if (side == null) continue;
            double[] p = ClashColors.toward(at, new double[]{side.getX(), side.getY() + side.getBbHeight() * 0.6, side.getZ()}, 0.7);
            c.level.sendParticles(dust(c.colors[i], size), p[0], p[1], p[2], perSide, 0.25, 0.25, 0.25, 0.0);
        }
        c.level.sendParticles(new DustColorTransitionOptions(rgb(c.colors[0]), rgb(c.colors[1]), size), at[0], at[1], at[2],
                Math.max(1, perSide / 2), 0.15, 0.15, 0.15, 0.0);
        colored++;
    }

    // when it ends, both colors go up together
    private static void colorBurst(Clash c, int perSide) {
        if (!ZeroConfig.SERVER.clashColors.get() || c.colors.length < 2 || c.point == null) return;
        float size = ClashColors.dustSize(c.tier) + 0.5f;
        for (int i = 0; i < 2; i++) {
            c.level.sendParticles(dust(c.colors[i], size), c.point.x, c.point.y, c.point.z, perSide, 0.9, 0.9, 0.9, 0.0);
        }
        colored++;
    }

    private static DustParticleOptions dust(int color, float size) {
        return new DustParticleOptions(rgb(color), size);
    }

    private static Vector3f rgb(int color) {
        float[] c = StreamMath.rgb(color);
        return new Vector3f(c[0], c[1], c[2]);
    }

    public static int coloredCount() {
        return colored;
    }

    /** The colors a clash going on shows, or null. */
    public static int[] colorsOf(Object key) {
        Clash c = ACTIVE.get(key);
        return c == null ? null : c.colors.clone();
    }

    // the grief rules are checked for the first player in the clash who lets things break
    static ServerPlayer griefer(List<LivingEntity> fighters) {
        for (LivingEntity e : fighters) {
            if (e instanceof ServerPlayer p && ImpactWorld.destructionAllowed(p)) return p;
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().getTickCount();
        if (!ACTIVE.isEmpty()) {
            List<Object> stale = new ArrayList<>();
            for (Map.Entry<Object, Clash> e : ACTIVE.entrySet()) if (now - e.getValue().lastTick > STALE_TICKS) stale.add(e.getKey());
            for (Object key : stale) end(key, false);
        }
        Iterator<Clash> it = FADING.iterator();
        while (it.hasNext()) {
            Clash c = it.next();
            if (now < c.crackExpire) continue;
            for (ImpactServer.Crack crack : c.cracks) c.level.destroyBlockProgress(crack.id, crack.pos, -1);
            it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        FADING.clear();
    }

    public static int cracksOf(Object key) {
        Clash c = ACTIVE.get(key);
        return c == null ? -1 : c.cracks.size();
    }

    public static int endedCount() {
        return ended;
    }

    public static int fadingCracks() {
        int n = 0;
        for (Clash c : FADING) n += c.cracks.size();
        return n;
    }

    // the self-test cleans up after its pretend clash
    public static void clearFading() {
        for (Clash c : FADING) {
            for (ImpactServer.Crack crack : c.cracks) c.level.destroyBlockProgress(crack.id, crack.pos, -1);
        }
        FADING.clear();
    }
}
