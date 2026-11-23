package com.dmzzero.heal;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.realm.NullRealm;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// the world heals itself: blocks broken or roughened in ki fights come back after a while, a few at a time,
// only in loaded chunks and only where the damage is still as it was left
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class WorldHealing {
    private static final int COST = TickCost.id("world.healing");
    // retry an unloaded block after this long, someone stood in the way after this; a block waits for the one below until
    // that one is due, and never less than BELOW_RETRY
    private static final int UNLOADED_RETRY = 600;
    private static final int BLOCKED_RETRY = 40;
    private static final int BELOW_RETRY = 5;
    // at most this many due blocks are looked at for every one put back
    private static final int LOOK_AHEAD = 4;
    // most blocks under one block brought back first, in one go
    private static final int COLUMN = 64;
    // server thread only, reused so a heal allocates nothing
    private static final List<HealQueue.Entry> UNDER = new java.util.ArrayList<>();
    // healing's time this server tick, across every dimension, and how many dimensions had work
    private static long spentNanos;
    private static int activeNow;
    private static int activeLast = 1;
    // what setting one block back usually takes, learned as blocks come back
    private static long blockNanos = 30_000;
    private static int recorded;
    private static int restored;
    // blocks given up on because something else is there now
    private static int dropped;
    // times a block was put back to wait on the block under it
    private static int belowWaits;
    // a falling block that will go back up: where it fell from and in which dimension, kept with the entity
    private static final String FELL_FROM = "dmzzero_fell_from";
    private static final String FELL_IN = "dmzzero_fell_in";
    private static int fell;
    private static int landed;
    // per dimension since the server started: damage not remembered because the list was full
    private static final Map<ResourceKey<Level>, Integer> REFUSED = new HashMap<>();
    // and spots given up on because something else is there now
    private static final Map<ResourceKey<Level>, Integer> GIVEN_UP = new HashMap<>();
    // dimensions healing at the wish rate, until this game time or until nothing is left
    private static final Map<ResourceKey<Level>, Long> WISHED = new HashMap<>();
    private static int wishes;

    private WorldHealing() {
    }

    /** Whether damage to this block would be remembered for healing, with room for it in this dimension's list. */
    public static boolean willRecord(ServerLevel level, BlockPos pos, BlockState before, boolean kiBlast) {
        return heals(level, before, kiBlast) && room(level, pos.asLong());
    }

    /** Whether this kind of damage to this block heals here, room or not. */
    public static boolean heals(ServerLevel level, BlockState before, boolean kiBlast) {
        if (!ZeroConfig.SERVER.healing.get()) return false;
        if (!(kiBlast ? ZeroConfig.SERVER.healKiBlasts.get() : ZeroConfig.SERVER.healImpacts.get())) return false;
        // the null realm's stage is rebuilt for every battle
        if (level.dimension().equals(NullRealm.KEY)) return false;
        return !before.isAir() && !before.hasBlockEntity();
    }

    private static boolean room(ServerLevel level, long pos) {
        return room(HealSave.get(level).queue(), pos);
    }

    private static boolean room(HealQueue q, long pos) {
        return HealMath.room(q.pending(pos), q.size(), ZeroConfig.SERVER.healMaxBlocks.get());
    }

    /** A block was changed by a ki fight: remember what it was so it can come back. */
    public static void record(ServerLevel level, BlockPos pos, BlockState before, BlockState after, boolean kiBlast) {
        if (before == after || !heals(level, before, kiBlast)) return;
        remember(level, pos, before, after);
    }

    // returns whether it's waiting to heal now
    private static boolean remember(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        long p = pos.asLong();
        // one lookup: a full carve tick records up to 8192 blocks
        HealSave save = HealSave.get(level);
        if (!room(save.queue(), p)) {
            refuse(level);
            return false;
        }
        boolean waiting = save.queue().record(p, Block.getId(before), Block.getId(after), HealMath.due(level.getGameTime(), ZeroConfig.SERVER.healMinutes.get()));
        if (waiting) recorded++;
        save.setDirty();
        return waiting;
    }

    private static void refuse(ServerLevel level) {
        if (REFUSED.merge(level.dimension(), 1, Integer::sum) == 1) {
            DMZZero.LOG.warn("World healing in {} is full ({} blocks, [world.healing] maxBlocks). New damage there won't heal, and ki attacks drop what they break.",
                    level.dimension().location(), ZeroConfig.SERVER.healMaxBlocks.get());
        }
    }

    /** Damage in this dimension not remembered since the server started, because its list was full. */
    public static int refused(ServerLevel level) {
        return REFUSED.getOrDefault(level.dimension(), 0);
    }

    // dmz's laser explosions are vanilla explosions; the blocks they'd blow up are taken out here instead, without drops, to come back later.
    // lowest priority, so a claim mod that takes protected blocks off the list has done it first
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Explosion explosion = event.getExplosion();
        if (!(explosion.getDirectSourceEntity() instanceof AbstractKiProjectile) || !explosion.interactsWithBlocks()) return;
        Iterator<BlockPos> it = event.getAffectedBlocks().iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            BlockState before = level.getBlockState(pos);
            if (before.getBlock() instanceof TntBlock || !heals(level, before, true)) continue;
            // with the list full the explosion breaks it as usual, drops and all
            if (!room(level, pos.asLong())) {
                refuse(level);
                continue;
            }
            BlockState after = level.getFluidState(pos).createLegacyBlock();
            if (!level.setBlock(pos, after, Block.UPDATE_ALL)) continue;
            record(level, pos, before, after, true);
            it.remove();
        }
    }

    // a block that falls because the block under it is waiting to heal goes back up when it heals, and drops nothing on the way
    @SubscribeEvent
    public static void onFallingJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof FallingBlockEntity block) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!ZeroConfig.SERVER.healing.get() || !ZeroConfig.SERVER.healFallen.get()) return;
        CompoundTag tag = block.getPersistentData();
        if (tag.contains(FELL_FROM)) {
            // through a portal it's an ordinary falling block again
            if (!fellIn(tag, level)) {
                block.dropItem = true;
                tag.remove(FELL_FROM);
                tag.remove(FELL_IN);
            }
            return;
        }
        if (event.loadedFromDisk() || level.dimension().equals(NullRealm.KEY)) return;
        BlockPos from = block.blockPosition();
        BlockState state = block.getBlockState();
        HealQueue q = HealSave.get(level).queue();
        // rock lift's rocks are already waiting where they came from
        if (q.pending(from.asLong()) || !q.pending(from.below().asLong()) || state.isAir() || state.hasBlockEntity()) return;
        if (!remember(level, from, state, level.getBlockState(from))) return;
        // not disableDrop(), which stops it landing as a block too
        block.dropItem = false;
        tag.putLong(FELL_FROM, from.asLong());
        tag.putString(FELL_IN, level.dimension().location().toString());
        fell++;
    }

    @SubscribeEvent
    public static void onFallingLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof FallingBlockEntity block) || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = block.getPersistentData();
        Entity.RemovalReason why = block.getRemovalReason();
        if (!tag.contains(FELL_FROM) || !fellIn(tag, level) || why == null) return;
        boolean portal = why == Entity.RemovalReason.CHANGED_DIMENSION;
        if (!why.shouldDestroy() && !portal) return;
        HealSave save = HealSave.get(level);
        int state = Block.getId(block.getBlockState());
        BlockPos at = block.blockPosition();
        // landed in a hole: that spot heals over it. landed anywhere else or went through a portal: it doesn't go back up.
        // broken on the way down, it comes back where it fell from
        boolean placed = !portal && level.getBlockState(at) == block.getBlockState();
        // concrete powder turns to concrete in water after this, so it's left where it lands
        boolean healsOver = placed && !(block.getBlockState().getBlock() instanceof ConcretePowderBlock);
        if (healsOver && save.queue().landed(at.asLong(), state)) landed++;
        else if (placed || portal) save.queue().forget(tag.getLong(FELL_FROM), state);
        save.setDirty();
    }

    private static boolean fellIn(CompoundTag tag, ServerLevel level) {
        return level.dimension().location().toString().equals(tag.getString(FELL_IN));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        long start = TickCost.start();
        levelTick(event);
        TickCost.add(COST, start);
    }

    // each server tick starts healing's budget afresh, split between the dimensions that had work last tick
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        activeLast = Math.max(1, activeNow);
        activeNow = 0;
        spentNanos = 0;
    }

    private static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (!ZeroConfig.SERVER.healing.get()) return;
        long now = level.getGameTime();
        if (HealSave.get(level).queue().nextDue() <= now) activeNow++;
        long budget = (long) (ZeroConfig.SERVER.healMillisPerTick.get() * 1_000_000);
        long start = System.nanoTime();
        tick(level, now, HealMath.allowance(budget, activeLast, spentNanos));
        spentNanos += System.nanoTime() - start;
    }

    /** Puts back what's due in this dimension, as fast as the settings allow. Returns how many blocks came back. */
    public static int tick(ServerLevel level, long now) {
        return tick(level, now, Long.MAX_VALUE);
    }

    /**
     * The same within a time allowance: once it's used up the rest wait for the next tick. At least one block is tried, so a
     * dimension is never left out. Returns how many blocks came back.
     */
    public static int tick(ServerLevel level, long now, long allowedNanos) {
        long start = System.nanoTime();
        HealSave save = HealSave.get(level);
        HealQueue q = save.queue();
        Long until = WISHED.get(level.dimension());
        if (until != null && (q.size() == 0 || now >= until)) {
            WISHED.remove(level.dimension());
            until = null;
        }
        if (q.nextDue() > now) return 0;
        int perTick = HealMath.rate(ZeroConfig.SERVER.healPerSecond.get(), ZeroConfig.SERVER.healWishPerSecond.get(), until != null);
        int before = q.size();
        int back = 0, looked = 0, most = perTick * LOOK_AHEAD;
        // one at a time, so the blocks under each one are still in the list to come back first. the first is always tried;
        // after that, none that would likely run past the time
        while (back < perTick && looked < most) {
            if (looked > 0 && HealMath.late(System.nanoTime() - start, blockNanos, allowedNanos)) break;
            HealQueue.Entry e = q.next(now);
            if (e == null) break;
            back += heal(level, q, e, now, start, allowedNanos);
            looked++;
        }
        if (until != null) {
            long keep = HealMath.wishKeeps(until, now, back);
            if (keep != until) WISHED.put(level.dimension(), keep);
        }
        // waits only move due times, which a restart redoes; blocks back or given up have to be saved
        if (q.size() != before) save.setDirty();
        restored += back;
        return back;
    }

    // returns how many blocks came back. the ground comes back from the bottom up, so nothing is left hanging or falls in:
    // the blocks due under this one come back first, bottom up, as long as there's time, and the rest keep their turn
    private static int heal(ServerLevel level, HealQueue q, HealQueue.Entry e, long now, long start, long allowedNanos) {
        List<HealQueue.Entry> column = UNDER;
        column.clear();
        long below = BlockPos.offset(e.pos(), 0, -1, 0);
        while (column.size() < COLUMN && q.dueOf(below) <= now) {
            column.add(q.take(below));
            below = BlockPos.offset(below, 0, -1, 0);
        }
        int back = 0;
        for (int i = column.size() - 1; i >= -1; i--) {
            // the bottom block always; above it, none that would likely run past the time. the rest wait their turn, bottom first
            if (i < column.size() - 1 && HealMath.late(System.nanoTime() - start, blockNanos, allowedNanos)) {
                for (int j = i; j >= 0; j--) q.postpone(column.get(j), now);
                q.postpone(e, now);
                break;
            }
            back += healOne(level, q, i >= 0 ? column.get(i) : e, now);
        }
        column.clear();
        return back;
    }

    // one block, if it's still as the damage left it; a block due later under it makes it wait for that one
    private static int healOne(ServerLevel level, HealQueue q, HealQueue.Entry e, long now) {
        BlockPos pos = BlockPos.of(e.pos());
        if (!level.isLoaded(pos)) {
            q.postpone(e, now + UNLOADED_RETRY);
            return 0;
        }
        BlockState current = level.getBlockState(pos);
        boolean loose = current.getBlock() instanceof LiquidBlock && !current.getFluidState().isSource();
        if (!HealMath.restore(Block.getId(current), e.left(), e.original(), loose)) {
            if (Block.getId(current) != e.original()) {
                dropped++;
                GIVEN_UP.merge(level.dimension(), 1, Integer::sum);
            }
            return 0;
        }
        BlockState original = Block.stateById(e.original());
        if (q.waitBelow(e, pos.below().asLong(), now, BELOW_RETRY)) {
            belowWaits++;
            return 0;
        }
        if (!original.getCollisionShape(level, pos).isEmpty() && !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) {
            q.postpone(e, now + BLOCKED_RETRY);
            return 0;
        }
        long t = System.nanoTime();
        if (!level.setBlock(pos, original, Block.UPDATE_ALL)) return 0;
        blockNanos = HealMath.blockAverage(blockNanos, System.nanoTime() - t);
        if (level.random.nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.2, 0.05, 0.2, 0.01);
        }
        return 1;
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        WISHED.clear();
        REFUSED.clear();
        GIVEN_UP.clear();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dmzzero")
                .then(Commands.literal("heal")
                        .executes(ctx -> status(ctx.getSource()))
                        .then(Commands.literal("now")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> now(ctx.getSource())))
                        .then(Commands.literal("forget")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> forget(ctx.getSource())))
                        .then(Commands.literal("wish")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> wished(ctx.getSource())))));
    }

    private static int status(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        if (!ZeroConfig.SERVER.healing.get()) {
            src.sendFailure(Component.translatableWithFallback("dmzzero.heal.off", "The world doesn't heal itself on this server."));
            return 0;
        }
        HealQueue q = HealSave.get(level).queue();
        if (q.size() == 0) {
            src.sendSuccess(() -> Component.translatableWithFallback("dmzzero.heal.none", "Nothing here is waiting to heal."), false);
        } else {
            long minutes = Math.max(0, (q.nextDue() - level.getGameTime() + 1199) / 1200);
            int n = q.size();
            src.sendSuccess(() -> Component.translatableWithFallback("dmzzero.heal.status", "%s damaged blocks here are waiting to heal. The next comes back in %s min.",
                    n, minutes), false);
        }
        int refused = refused(level);
        if (refused > 0) {
            int max = ZeroConfig.SERVER.healMaxBlocks.get();
            src.sendSuccess(() -> Component.translatableWithFallback("dmzzero.heal.full",
                    "%s damaged blocks here weren't remembered since the server started, because the list was full (maxBlocks %s). They won't heal.",
                    refused, max).withStyle(ChatFormatting.YELLOW), false);
        }
        int givenUp = givenUp(level);
        if (givenUp > 0) {
            src.sendSuccess(() -> Component.translatableWithFallback("dmzzero.heal.given_up",
                    "%s damaged spots here were left as they are since the server started, because something else is there now.", givenUp), false);
        }
        return 1;
    }

    /** Spots in this dimension given up on since the server started, because something else is there now. */
    public static int givenUp(ServerLevel level) {
        return GIVEN_UP.getOrDefault(level.dimension(), 0);
    }

    private static int now(CommandSourceStack src) {
        int n = HealSave.get(src.getLevel()).queue().hurry(src.getLevel().getGameTime());
        HealSave.get(src.getLevel()).setDirty();
        src.sendSuccess(() -> Component.translatableWithFallback("dmzzero.heal.now", "%s damaged blocks here start coming back now.", n)
                .withStyle(ChatFormatting.GREEN), true);
        return n;
    }

    /** The healing wish: everything damaged in this dimension starts coming back now, at the wish rate. Returns how many blocks. */
    public static int wish(ServerLevel level) {
        HealSave save = HealSave.get(level);
        int n = save.queue().hurry(level.getGameTime());
        save.setDirty();
        if (n > 0) WISHED.put(level.dimension(), HealMath.wishEnds(level.getGameTime(), n, HealMath.perTick(ZeroConfig.SERVER.healWishPerSecond.get())));
        wishes++;
        return n;
    }

    private static int wished(CommandSourceStack src) {
        int n = wish(src.getLevel());
        Component line = n > 0
                ? Component.translatableWithFallback("dmzzero.heal.wish", "The land heals: %s damaged blocks here are coming back.", n).withStyle(ChatFormatting.GREEN)
                : Component.translatableWithFallback("dmzzero.heal.wish_none", "The land here has nothing left to heal.").withStyle(ChatFormatting.GRAY);
        for (net.minecraft.server.level.ServerPlayer p : src.getLevel().players()) p.sendSystemMessage(line);
        if (src.getEntity() == null) src.sendSuccess(() -> line, false);
        return n;
    }

    public static boolean rushing(ServerLevel level) {
        return WISHED.containsKey(level.dimension());
    }

    public static int wishes() {
        return wishes;
    }

    private static int forget(CommandSourceStack src) {
        HealQueue q = HealSave.get(src.getLevel()).queue();
        int n = q.size();
        q.clear();
        HealSave.get(src.getLevel()).setDirty();
        src.sendSuccess(() -> Component.translatableWithFallback("dmzzero.heal.forget", "Forgot %s damaged blocks here. They stay as they are.", n), true);
        return n;
    }

    public static int recordedCount() {
        return recorded;
    }

    public static int restoredCount() {
        return restored;
    }

    public static int droppedCount() {
        return dropped;
    }

    public static int belowWaitCount() {
        return belowWaits;
    }

    public static int fellCount() {
        return fell;
    }

    public static int landedCount() {
        return landed;
    }
}
