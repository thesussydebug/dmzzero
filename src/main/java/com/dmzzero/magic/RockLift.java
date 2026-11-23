package com.dmzzero.magic;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath.V;
import com.dmzzero.heal.WorldHealing;
import com.dmzzero.impact.ImpactLookup;
import com.dmzzero.impact.ImpactProfile;
import com.dmzzero.impact.ImpactProfiles;
import com.dmzzero.impact.ImpactWorld;
import com.dmzzero.kishare.TossMath;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.init.MainGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.dmzzero.magic.SpellKit.pay;
import static com.dmzzero.magic.SpellKit.sparkle;
import static com.dmzzero.magic.SpellKit.stats;
import static com.dmzzero.magic.SpellKit.v;

// a glind spell: tear a block out of the ground, hold it in front of you, throw it. keep the key down and more blocks
// tear out around the first and join it as a ball. each rock is a vanilla falling block with its gravity off, steered at
// the start of each tick so it never lands and places itself; it crumbles instead
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class RockLift implements Spell {
    public static final RockLift SPELL = new RockLift();
    private static final int COST = TickCost.id("magic.rockLift", RockLift::rocks);
    private static final int MAX_FLYING = 256;
    private static final int FLY_TICKS = 100;
    private static final double GRAVITY = 0.04;
    private static final double REACH_SLACK = 2;
    private static final Map<UUID, Cluster> HOLDS = new HashMap<>();
    private static final List<Rock> FLYING = new ArrayList<>();
    private static final TossMath.Cooldowns COOLDOWNS = new TossMath.Cooldowns();
    private static int pulled;
    private static int thrown;
    private static int hits;
    private static float lastHit;
    private static double lastShare;
    private static double charged;
    private static String lastEnd = "";
    // the self-test's fake players aren't entities in the world, so it names who stands where
    private static volatile List<Player> testStanding;

    private RockLift() {
    }

    static final class Rock {
        final FallingBlockEntity block;
        final BlockState state;
        final int slot;
        long age;
        Throw from;

        Rock(FallingBlockEntity block, BlockState state, int slot) {
            this.block = block;
            this.state = state;
            this.slot = slot;
        }
    }

    // one lift: the first rock and every block that joined it while the key was down
    static final class Cluster {
        final ServerPlayer caster;
        final ServerLevel level;
        final BlockPos origin;
        final List<RockMath.Off> order;
        final List<Rock> rocks = new ArrayList<>();
        boolean growing = true;
        boolean done;
        long age;

        Cluster(ServerPlayer caster, ServerLevel level, BlockPos origin) {
            this.caster = caster;
            this.level = level;
            this.origin = origin;
            this.order = RockMath.pullOrder(ZeroConfig.SERVER.rockClusterRadius.get());
        }
    }

    // one throw: each mob is hit once, harder the more rocks flew together
    static final class Throw {
        final ServerPlayer caster;
        final ServerLevel level;
        final double share;
        final Set<UUID> hit = new HashSet<>();
        long soundTick = -1;

        Throw(ServerPlayer caster, ServerLevel level, double share) {
            this.caster = caster;
            this.level = level;
            this.share = share;
        }
    }

    @Override
    public String id() {
        return "rockLift";
    }

    @Override
    public String english() {
        return "Rock lift";
    }

    @Override
    public ForgeConfigSpec.BooleanValue toggle() {
        return ZeroConfig.SERVER.rockLift;
    }

    @Override
    public String cast(ServerPlayer caster, StatsData data, long now) {
        Cluster held = HOLDS.remove(caster.getUUID());
        if (held != null) return toss(held, now);
        long wait = COOLDOWNS.remaining(caster.getUUID(), now);
        if (wait > 0) return tell(caster, "cooldown", "Rock lift ready in %ss.", false, (wait + 19) / 20);
        return lift(caster, data, aim(caster));
    }

    @Override
    public boolean busy(ServerPlayer caster) {
        return HOLDS.containsKey(caster.getUUID());
    }

    /** Letting go of the key stops more blocks joining. The self-test calls it straight. */
    @Override
    public void release(ServerPlayer caster) {
        Cluster c = HOLDS.get(caster.getUUID());
        if (c == null || !c.growing) return;
        c.growing = false;
        if (c.rocks.size() == 1) tell(caster, "holding", "Holding a rock. Cast again to throw it.", true);
    }

    @Override
    public void end(ServerPlayer caster, long now) {
        Cluster held = HOLDS.remove(caster.getUUID());
        if (held != null) {
            crumble(held);
            tell(caster, "crumbled", "The rock crumbles.", false);
        }
    }

    static BlockPos aim(ServerPlayer caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getViewVector(1f).scale(ZeroConfig.SERVER.tkRange.get()));
        BlockHitResult hit = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    /** "ok" when this block can be torn out by the caster, or why not. */
    public static String check(ServerPlayer caster, BlockPos pos) {
        if (!Spells.on(SPELL.id())) return "off";
        ServerLevel level = caster.serverLevel();
        if (pos == null || !level.isLoaded(pos)) return "no_block";
        if (caster.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > ZeroConfig.SERVER.tkRange.get() + REACH_SLACK) return "no_block";
        if (!ImpactWorld.destructionAllowed(caster)) return "no_destruction";
        return tearable(level, pos, caster) ? "ok" : "stuck";
    }

    // the block rules every rock follows, the first one and the ones that join it
    private static boolean tearable(ServerLevel level, BlockPos pos, ServerPlayer caster) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.above())) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getCollisionShape(level, pos).isEmpty() || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) return false;
        boolean open = level.getBlockState(pos.above()).isAir();
        if (!RockMath.liftable(state.getDestroySpeed(level, pos), ZeroConfig.SERVER.rockHardness.get(), state.hasBlockEntity(),
                false, !state.getFluidState().isEmpty(), open)) return false;
        return MainGameRules.canKiGrief(level, pos, caster);
    }

    /** Tears the block at pos out and holds it, the first rock of a cluster. The self-test calls it straight. */
    public static String lift(ServerPlayer caster, StatsData data, BlockPos pos) {
        String why = check(caster, pos);
        if ("off".equals(why)) return why;
        if (!"ok".equals(why)) return tell(caster, why, english(why), false);
        if (!charge(caster, data, ZeroConfig.SERVER.rockKiCost.get())) return tell(caster, "no_ki", "Not enough ki.", false);
        ServerLevel level = caster.serverLevel();
        Cluster c = new Cluster(caster, level, pos);
        c.rocks.add(tear(level, pos, 0, true));
        HOLDS.put(caster.getUUID(), c);
        if (most(data) > 1) tell(caster, "gathering", "Holding a rock. Keep the key down to pull more.", true);
        else tell(caster, "holding", "Holding a rock. Cast again to throw it.", true);
        return "ok";
    }

    private static Rock tear(ServerLevel level, BlockPos pos, int slot, boolean first) {
        BlockState state = level.getBlockState(pos);
        WorldHealing.record(level, pos, state, Blocks.AIR.defaultBlockState(), false);
        FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
        block.setNoGravity(true);
        block.disableDrop();
        block.time = 1;
        block.setDeltaMovement(0, 0.25, 0);
        Vec3 at = Vec3.atCenterOf(pos);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, first ? 20 : 10, 0.4, 0.4, 0.4, 0.1);
        level.playSound(null, at.x, at.y, at.z, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, first ? 1f : 0.7f, 0.7f);
        if (first) level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.1f);
        return new Rock(block, state, slot);
    }

    // while the key is down, one more block every clusterTicks: the nearest one to the first hole that can come out
    private static void grow(Cluster c, StatsData data) {
        int max = most(data);
        // full for this form; a stronger one makes room for more while the key is held
        if (c.rocks.size() >= max || c.rocks.size() >= RockMath.wanted(c.age, max, ZeroConfig.SERVER.rockClusterTicks.get())) return;
        BlockPos pos = next(c);
        if (pos == null) {
            c.done = true;
            tell(c.caster, "all_out", "Holding %s rocks. Nothing more comes out here.", true, c.rocks.size());
            return;
        }
        if (!charge(c.caster, data, ZeroConfig.SERVER.rockClusterKiCost.get())) {
            c.growing = false;
            tell(c.caster, "no_ki_more", "Not enough ki to pull more.", false);
            return;
        }
        c.rocks.add(tear(c.level, pos, freeSlot(c), false));
        pulled++;
        if (c.rocks.size() >= max) {
            tell(c.caster, "full", "Holding %s rocks, as many as you can.", true, c.rocks.size());
        } else {
            tell(c.caster, "growing", "Holding %s rocks. Cast again to throw them.", true, c.rocks.size());
        }
    }

    /** Most rocks this caster's cluster holds right now: more for each tier of the form they're in. */
    public static int most(StatsData data) {
        ImpactProfile form = data == null ? ImpactProfiles.BASE : ImpactLookup.current(data);
        int tier = form == ImpactProfiles.BASE ? 0 : form.tier();
        int n = RockMath.most(ZeroConfig.SERVER.rockClusterBlocks.get(), tier, ZeroConfig.SERVER.rockClusterPerTier.get(), ZeroConfig.SERVER.rockClusterMost.get());
        return Math.min(n, RockMath.slots(Integer.MAX_VALUE).size());
    }

    private static BlockPos next(Cluster c) {
        if (!ImpactWorld.destructionAllowed(c.caster)) return null;
        // never the ground someone stands on
        List<Player> standing = new ArrayList<>(c.level.getEntitiesOfClass(Player.class, new AABB(c.origin).inflate(RockMath.DEPTH + 6), p -> !p.isSpectator()));
        List<Player> test = testStanding;
        if (test != null) standing.addAll(test);
        for (RockMath.Off o : c.order) {
            BlockPos pos = c.origin.offset(o.x(), o.y(), o.z());
            if (!tearable(c.level, pos, c.caster)) continue;
            AABB top = new AABB(pos.getX(), pos.getY() + 0.9, pos.getZ(), pos.getX() + 1, pos.getY() + 1.2, pos.getZ() + 1);
            if (standing.stream().noneMatch(p -> p.getBoundingBox().intersects(top))) return pos;
        }
        return null;
    }

    private static int freeSlot(Cluster c) {
        Set<Integer> used = new HashSet<>();
        for (Rock r : c.rocks) used.add(r.slot);
        int slot = 0;
        while (used.contains(slot)) slot++;
        return slot;
    }

    private static String toss(Cluster c, long now) {
        V v = TelekinesisMath.toss(v(c.caster.getViewVector(1f)), ZeroConfig.SERVER.rockThrowSpeed.get());
        int n = c.rocks.size();
        Throw t = new Throw(c.caster, c.level, RockMath.share(n, ZeroConfig.SERVER.rockClusterBlocks.get(), ZeroConfig.SERVER.rockClusterDamage.get()));
        for (Rock r : c.rocks) {
            r.block.setDeltaMovement(v.x(), v.y(), v.z());
            r.block.hurtMarked = true;
            r.age = 0;
            r.from = t;
            if (FLYING.size() < MAX_FLYING) FLYING.add(r);
            else crumble(c.level, r, true);
        }
        thrown++;
        COOLDOWNS.start(c.caster.getUUID(), now, ZeroConfig.SERVER.rockCooldown.get() * 20L);
        FallingBlockEntity first = c.rocks.get(0).block;
        c.level.playSound(null, first.getX(), first.getY(), first.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1f, n > 1 ? 0.8f : 1.0f);
        if (n > 1) tell(c.caster, "thrown_many", "Thrown %s rocks.", true, n);
        else tell(c.caster, "thrown", "Thrown.", true);
        return "thrown";
    }

    // before the level ticks, so a rock that would touch something this tick crumbles first and never places itself
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || HOLDS.isEmpty() && FLYING.isEmpty()) return;
        long start = TickCost.start();
        step();
        TickCost.add(COST, start);
    }

    /** One tick of every held and thrown rock. The self-test steps it by hand. */
    public static void step() {
        if (!HOLDS.isEmpty()) {
            Iterator<Cluster> it = HOLDS.values().iterator();
            while (it.hasNext()) {
                Cluster c = it.next();
                String end = keep(c);
                if (end == null) continue;
                it.remove();
                crumble(c);
                lastEnd = end;
                if ("out_of_ki".equals(end)) tell(c.caster, end, "Out of ki. The rock crumbles.", false);
                else if (!"lost".equals(end) && !"gone".equals(end)) tell(c.caster, "crumbled", "The rock crumbles.", false);
            }
        }
        if (!FLYING.isEmpty()) FLYING.removeIf(RockLift::fly);
    }

    // null to keep holding, else why it ends
    private static String keep(Cluster c) {
        ServerPlayer caster = c.caster;
        StatsData data = stats(caster);
        if (!Magic.on() || !Spells.on(SPELL.id())) return "dropped";
        if (caster.isRemoved() || caster.level() != c.level || !Magic.canCast(caster, data)) return "lost";
        c.rocks.removeIf(r -> {
            if (r.block.isRemoved()) return true;
            if (!TelekinesisMath.slipped(caster.distanceTo(r.block), ZeroConfig.SERVER.tkRange.get())) return false;
            crumble(c.level, r, false);
            return true;
        });
        if (c.rocks.isEmpty()) return "gone";
        c.age++;
        if (TelekinesisMath.expired(c.age, ZeroConfig.SERVER.tkMaxSeconds.get())) return "tired";
        if (c.age % 20 == 0 && !charge(caster, data, ZeroConfig.SERVER.tkKiPerSecond.get())) return "out_of_ki";
        if (c.growing && !c.done) grow(c, data);
        int span = 0;
        for (Rock r : c.rocks) span = Math.max(span, r.slot + 1);
        List<RockMath.Off> slots = RockMath.slots(span);
        // the ball floats further out as it grows, so its near side stays where a single rock would
        V middle = TelekinesisMath.holdPoint(v(caster.getEyePosition()), v(caster.getViewVector(1f)),
                ZeroConfig.SERVER.tkHoldDistance.get() + RockMath.radius(span), 0.5);
        int sparkles = c.rocks.size() > 4 ? 1 : 4;
        for (Rock r : c.rocks) {
            RockMath.Off o = slots.get(r.slot);
            steer(c.level, r, middle.add(new V(o.x(), o.y(), o.z())));
            if (c.age % 3 == 0) sparkle(c.level, r.block, sparkles);
        }
        return null;
    }

    // a share of max ki, counted so the self-test can check the charges; dmz works max ki out fresh on every call
    private static boolean charge(ServerPlayer caster, StatsData data, double share) {
        if (!pay(caster, data, SpellMath.cost(data.getMaxEnergy(), share))) return false;
        charged += share;
        return true;
    }

    private static void steer(ServerLevel level, Rock r, V point) {
        r.age++;
        V pull = TelekinesisMath.pull(v(r.block.position()), point, 0.35, 1.2);
        Vec3 move = new Vec3(pull.x(), pull.y(), pull.z());
        // never into a block: try the vertical part, then the flat part, and a fresh rock still in its hole rises out
        if (!free(level, r, move)) {
            Vec3 vertical = new Vec3(0, move.y, 0), flat = new Vec3(move.x, 0, move.z), rise = new Vec3(0, 0.15, 0);
            move = free(level, r, vertical) ? vertical : free(level, r, flat) ? flat : r.age < 20 && free(level, r, rise) ? rise : Vec3.ZERO;
        }
        r.block.setDeltaMovement(move);
        r.block.time = 1;
        r.block.hurtMarked = true;
    }

    private static boolean free(ServerLevel level, Rock r, Vec3 move) {
        return level.noCollision(r.block, r.block.getBoundingBox().move(move));
    }

    // true when this thrown rock is done
    private static boolean fly(Rock r) {
        FallingBlockEntity b = r.block;
        Throw t = r.from;
        if (b.isRemoved()) return true;
        r.age++;
        if (r.age > FLY_TICKS || b.level() != t.level) {
            crumble(t.level, r, quiet(t));
            return true;
        }
        Vec3 v = b.getDeltaMovement().add(0, -GRAVITY, 0);
        AABB sweep = b.getBoundingBox().expandTowards(v).inflate(0.3);
        LivingEntity hit = t.level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e != t.caster && e.isAlive() && !e.isSpectator()
                        && !t.hit.contains(e.getUUID()) && !(e instanceof Player p && !SpellKit.mayHurt(t.caster, p)))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(b))).orElse(null);
        if (hit != null) {
            t.hit.add(hit.getUUID());
            strike(t, hit, v.length());
            hit.setDeltaMovement(hit.getDeltaMovement().add(v.scale(0.5 * Math.min(2, t.share))));
            hit.hurtMarked = true;
            crumble(t.level, r, quiet(t));
            return true;
        }
        if (!t.level.noCollision(b, b.getBoundingBox().move(v))) {
            crumble(t.level, r, quiet(t));
            return true;
        }
        b.setDeltaMovement(v);
        b.time = 1;
        b.hurtMarked = true;
        return false;
    }

    // twenty rocks landing in one tick make one crash, not twenty
    private static boolean quiet(Throw t) {
        long tick = t.level.getGameTime();
        if (t.soundTick == tick) return true;
        t.soundTick = tick;
        return false;
    }

    private static void strike(Throw t, LivingEntity target, double speed) {
        StatsData data = stats(t.caster);
        float damage = (float) (t.share * TelekinesisMath.impact(data == null ? 0 : data.getMeleeDamage(), ZeroConfig.SERVER.rockDamage.get(),
                speed, ZeroConfig.SERVER.rockThrowSpeed.get()));
        if (damage > 0) {
            DamageSource source = new DamageSource(t.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(DamageTypes.FALLING_BLOCK), t.caster);
            target.hurt(source, damage);
        }
        hits++;
        lastHit = damage;
        lastShare = t.share;
    }

    private static void crumble(Cluster c) {
        boolean quiet = false;
        for (Rock r : c.rocks) {
            crumble(c.level, r, quiet);
            quiet = true;
        }
        c.rocks.clear();
    }

    private static void crumble(ServerLevel level, Rock r, boolean quiet) {
        FallingBlockEntity b = r.block;
        if (!b.isRemoved()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, r.state), b.getX(), b.getY() + 0.5, b.getZ(), quiet ? 10 : 24, 0.3, 0.3, 0.3, 0.1);
            if (!quiet) level.playSound(null, b.getX(), b.getY(), b.getZ(), r.state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1f, 0.8f);
            b.discard();
        }
    }

    private static String english(String why) {
        return switch (why) {
            case "no_block" -> "No block to lift there.";
            case "no_destruction" -> "Destruction is off for you here.";
            default -> "That block won't come out.";
        };
    }

    private static String tell(ServerPlayer caster, String why, String english, boolean bright, Object... args) {
        return SpellKit.tell(caster, "rock", why, english, bright, args);
    }

    private static int rocks() {
        int n = FLYING.size();
        for (Cluster c : HOLDS.values()) n += c.rocks.size();
        return n;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        Cluster held = HOLDS.remove(id);
        if (held != null) crumble(held);
        FLYING.removeIf(r -> {
            if (!r.from.caster.getUUID().equals(id)) return false;
            crumble(r.from.level, r, true);
            return true;
        });
    }

    // rocks still in the air would be saved with the world, gravity off
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clearRocks();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HOLDS.clear();
        FLYING.clear();
        COOLDOWNS.clear();
        testStanding = null;
    }

    private static void clearRocks() {
        HOLDS.values().forEach(RockLift::crumble);
        FLYING.forEach(r -> crumble(r.from.level, r, true));
        HOLDS.clear();
        FLYING.clear();
    }

    /** Self-test only: crumble every rock and forget the cooldowns. */
    public static void clearAll() {
        clearRocks();
        COOLDOWNS.clear();
        testStanding = null;
    }

    /** Self-test only: fake players to count as standing on the ground. Null clears it. */
    public static void setTestStanding(List<Player> players) {
        testStanding = players;
    }

    /** The first rock of the caster's cluster, or null. */
    public static FallingBlockEntity held(ServerPlayer caster) {
        Cluster c = HOLDS.get(caster.getUUID());
        if (c == null) return null;
        return c.rocks.stream().min(Comparator.comparingInt(r -> r.slot)).map(r -> r.block).orElse(null);
    }

    /** Every rock the caster holds. */
    public static List<FallingBlockEntity> heldRocks(ServerPlayer caster) {
        Cluster c = HOLDS.get(caster.getUUID());
        return c == null ? List.of() : c.rocks.stream().map(r -> r.block).toList();
    }

    public static boolean growing(ServerPlayer caster) {
        Cluster c = HOLDS.get(caster.getUUID());
        return c != null && c.growing && !c.done && c.rocks.size() < most(SpellKit.stats(caster));
    }

    public static boolean flying(FallingBlockEntity block) {
        return FLYING.stream().anyMatch(r -> r.block == block);
    }

    public static int pulled() {
        return pulled;
    }

    public static int throwsMade() {
        return thrown;
    }

    public static int hits() {
        return hits;
    }

    public static float lastHit() {
        return lastHit;
    }

    /** Every share of max ki rock lift has charged. */
    public static double charged() {
        return charged;
    }

    /** How many times one rock's hit the last hit was. */
    public static double lastShare() {
        return lastShare;
    }

    public static String lastEnd() {
        return lastEnd;
    }
}
