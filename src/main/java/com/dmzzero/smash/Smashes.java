package com.dmzzero.smash;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath.V;
import com.dmzzero.impact.ImpactWorld;
import com.dmzzero.impact.Leaves;
import com.dmzzero.network.ImpactPulseS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.init.MainParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// knocked flying into the scenery: whoever dmz's heavy knockback sends flying is watched for a moment, and where they hit, the scenery shows it
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Smashes {
    private static final int COST = TickCost.id("fighting.smash", () -> Smashes.WATCHED.size() + Smashes.FADING.size());
    private static final int MAX_WATCHED = 64;
    private static final int MAX_FADING = 256;
    private static final int LINGER_TICKS = 100;
    private static final double PULSE_RANGE = 24;
    private static final Map<UUID, Flight> WATCHED = new HashMap<>();
    private static final List<Crack> FADING = new ArrayList<>();
    private static boolean quiet;
    private static int nextCrack;
    private static int impacts;
    private static Impact last;

    private Smashes() {
    }

    static final class Flight {
        final ServerLevel level;
        final LivingEntity entity;
        SmashMath.Watch watch;
        Vec3 prev;
        Vec3 lastMove;
        int age;

        Flight(ServerLevel level, LivingEntity entity, double launchSpeed) {
            this.level = level;
            this.entity = entity;
            this.watch = new SmashMath.Watch(launchSpeed);
            this.prev = entity.position();
            this.lastMove = Vec3.ZERO;
        }
    }

    record Crack(ServerLevel level, int id, BlockPos pos, long expire) {
    }

    /** What the last smash did, for the tests. */
    public record Impact(BlockPos pos, Direction face, int power, int cracks, int broken, boolean degraded, String griefer) {
    }

    /** Runs knockback that isn't a hit (the beam recoil) without sending anyone flying into the scenery. */
    public static void quietly(Runnable knockback) {
        boolean was = quiet;
        quiet = true;
        try {
            knockback.run();
        } finally {
            quiet = was;
        }
    }

    // from KnockbackHelperMixin: dmz has just knocked target back with this motion
    public static void launched(LivingEntity target, Vec3 knock) {
        if (quiet || target == null || knock == null || !(target.level() instanceof ServerLevel level)) return;
        if (!ZeroConfig.SERVER.smash.get() || target.isSpectator() || target.isRemoved()) return;
        double min = ZeroConfig.SERVER.smashSpeed.get();
        if (!SmashMath.launches(new V(knock.x, knock.y, knock.z), min)) return;
        Flight f = WATCHED.get(target.getUUID());
        if (f == null && WATCHED.size() >= MAX_WATCHED) return;
        double speed = knock.length();
        if (f == null || f.entity != target) {
            WATCHED.put(target.getUUID(), new Flight(level, target, speed));
        } else {
            // knocked again mid-flight: watch the new flight from here
            f.age = 0;
            f.watch = new SmashMath.Watch(speed);
            f.prev = target.position();
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        step(event.getServer().getTickCount());
    }

    /** One tick of watching. The self-test steps it by hand. */
    public static void step(long now) {
        if (!WATCHED.isEmpty()) {
            double min = ZeroConfig.SERVER.smashSpeed.get();
            Iterator<Flight> it = WATCHED.values().iterator();
            while (it.hasNext()) {
                Flight f = it.next();
                LivingEntity e = f.entity;
                if (e.isRemoved() || !e.isAlive() || e.level() != f.level || f.age >= SmashMath.WATCH_TICKS) {
                    it.remove();
                    continue;
                }
                Vec3 pos = e.position();
                Vec3 move = pos.subtract(f.prev);
                int hit = f.watch.tick(move.length(), e.horizontalCollision, e.verticalCollision, min);
                if (hit != SmashMath.Watch.NONE) {
                    // the way it was going before it stopped
                    Vec3 dir = f.lastMove.lengthSqr() > 1e-6 ? f.lastMove : move.lengthSqr() > 1e-6 ? move : e.getDeltaMovement();
                    smash(f, dir, hit == SmashMath.Watch.WALL, f.watch.speed(), min, now);
                    it.remove();
                    continue;
                }
                f.prev = pos;
                f.lastMove = move;
                f.age++;
            }
        }
        if (!FADING.isEmpty()) {
            Iterator<Crack> it = FADING.iterator();
            while (it.hasNext()) {
                Crack c = it.next();
                if (now < c.expire) continue;
                c.level.destroyBlockProgress(c.id, c.pos, -1);
                it.remove();
            }
        }
    }

    private static void smash(Flight f, Vec3 move, boolean wall, double speed, double min, long now) {
        LivingEntity e = f.entity;
        ServerLevel level = f.level;
        Direction face = Direction.from3DDataValue(SmashMath.face(new V(move.x, move.y, move.z), wall));
        BlockPos hit = contact(level, e, face);
        if (hit == null) return;
        BlockState state = level.getBlockState(hit);
        int power = SmashMath.power(speed, min);
        Direction out = face.getOpposite();
        Vec3 at = Vec3.atCenterOf(hit).add(out.getStepX() * 0.55, out.getStepY() * 0.55, out.getStepZ() * 0.55);

        double spread = 0.25 + 0.2 * power;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 14 + 12 * power, spread, spread, spread, 0.15);
        level.sendParticles(MainParticles.DUST.get(), at.x, at.y, at.z, 5 + 5 * power, spread * 1.5, spread, spread * 1.5, 0.06);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 6 + 6 * power, spread, spread, spread, 0.08 + 0.04 * power);
        if (power >= 3) level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 0.9f + 0.4f * power, 0.6f);
        level.playSound(null, at.x, at.y, at.z, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 0.7f);
        if (power >= 3) level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5f, 1.4f);
        ZeroNetwork.sendAround(level, at, PULSE_RANGE, new ImpactPulseS2C(at.x, at.y, at.z, SmashMath.shake(power), (float) PULSE_RANGE, 0, 0f, 0f));

        List<BlockPos> cracked = crack(level, hit, face, power, now);
        Leaves.tear(level, at, SmashMath.crackRadius(power) + 2, Math.min(1f, power / 3f));

        ServerPlayer griefer = e.getLastHurtByMob() instanceof ServerPlayer p && p != e ? p : null;
        int broken = 0;
        boolean degraded = false;
        if (griefer != null && ZeroConfig.SERVER.smashDestruction.get() && ImpactWorld.destructionAllowed(griefer)) {
            broken = ImpactWorld.shatterAll(level, griefer, at, SmashMath.crackRadius(power), SmashMath.breakCap(power));
            if (SmashMath.degrades(power)) {
                degraded = ImpactWorld.degrade(level, griefer, hit);
                for (int i = 1; i < cracked.size() && i < power; i++) ImpactWorld.degrade(level, griefer, cracked.get(i));
            }
        }
        impacts++;
        last = new Impact(hit, face, power, cracked.size(), broken, degraded, griefer == null ? null : griefer.getGameProfile().getName());
    }

    // the solid block on the side that hit: level with the middle of the body, else at the feet or head
    static BlockPos contact(ServerLevel level, LivingEntity e, Direction face) {
        AABB box = e.getBoundingBox();
        Vec3 c = box.getCenter();
        List<Vec3> probes = new ArrayList<>();
        if (face == Direction.DOWN) {
            probes.add(new Vec3(c.x, box.minY - 0.25, c.z));
        } else if (face == Direction.UP) {
            probes.add(new Vec3(c.x, box.maxY + 0.25, c.z));
        } else {
            double reach = box.getXsize() / 2 + 0.3;
            double x = c.x + face.getStepX() * reach, z = c.z + face.getStepZ() * reach;
            probes.add(new Vec3(x, c.y, z));
            probes.add(new Vec3(x, box.minY + 0.2, z));
            probes.add(new Vec3(x, box.maxY - 0.2, z));
        }
        for (Vec3 p : probes) {
            BlockPos pos = BlockPos.containing(p);
            if (level.isLoaded(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return pos;
        }
        return null;
    }

    // a ring of cracks across the face that was hit, deepest in the middle
    private static List<BlockPos> crack(ServerLevel level, BlockPos hit, Direction face, int power, long now) {
        List<BlockPos> found = new ArrayList<>();
        if (!ZeroConfig.SERVER.cracks.get()) return found;
        double radius = SmashMath.crackRadius(power);
        int r = (int) Math.ceil(radius);
        Direction.Axis axis = face.getAxis();
        Direction out = face.getOpposite();
        for (int a = -r; a <= r; a++) {
            for (int b = -r; b <= r; b++) {
                if (a * a + b * b > radius * radius) continue;
                BlockPos pos = switch (axis) {
                    case X -> hit.offset(0, a, b);
                    case Y -> hit.offset(a, 0, b);
                    case Z -> hit.offset(a, b, 0);
                };
                if (!level.isLoaded(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.getCollisionShape(level, pos).isEmpty() || state.getDestroySpeed(level, pos) < 0) continue;
                BlockPos front = pos.relative(out);
                if (!level.getBlockState(front).getCollisionShape(level, front).isEmpty()) continue;
                found.add(pos);
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(hit) + level.random.nextDouble() * 0.8));
        int want = Math.min(found.size(), SmashMath.crackCount(power));
        List<BlockPos> cracked = new ArrayList<>(found.subList(0, want));
        for (BlockPos pos : cracked) {
            double dist = Math.sqrt(pos.distSqr(hit));
            int stage = (int) Math.max(2, Math.min(9, Math.round(9 - dist * (6.0 / (radius + 0.5)) + level.random.nextInt(2))));
            if (FADING.size() >= MAX_FADING) {
                Crack old = FADING.remove(0);
                old.level.destroyBlockProgress(old.id, old.pos, -1);
            }
            Crack crack = new Crack(level, SmashMath.crackId(nextCrack++), pos.immutable(), now + LINGER_TICKS);
            level.destroyBlockProgress(crack.id, crack.pos, stage);
            FADING.add(crack);
        }
        return cracked;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WATCHED.clear();
        FADING.clear();
        quiet = false;
    }

    public static int impacts() {
        return impacts;
    }

    public static Impact last() {
        return last;
    }

    public static boolean watching(LivingEntity e) {
        Flight f = WATCHED.get(e.getUUID());
        return f != null && f.entity == e;
    }

    public static int fadingCracks() {
        return FADING.size();
    }

    // the self-test cleans up after itself
    public static void clearAll() {
        for (Crack c : FADING) c.level.destroyBlockProgress(c.id, c.pos, -1);
        FADING.clear();
        WATCHED.clear();
    }
}
