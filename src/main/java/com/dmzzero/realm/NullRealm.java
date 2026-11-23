package com.dmzzero.realm;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;

import static com.dmzzero.realm.BattleMath.inDisc;
import static com.dmzzero.realm.BattleMath.rim;
import static com.dmzzero.realm.BattleMath.slotX;

// a white void with a stage for each battle; nobody stays here without one, and nobody flies
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class NullRealm {
    private static final int COST = TickCost.id("versus.battles");
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(DMZZero.MOD_ID, "null_realm"));
    public static final int FLOOR_Y = 96;
    public static final int STAND_Y = FLOOR_Y + 1;
    private static final int THICKNESS = 4;
    private static final int HEADROOM = 6;
    private static final int STANDS_GAP = 12;
    private static final int STANDS_RADIUS = 6;
    private static final int STANDS_FLOOR = FLOOR_Y + 10;
    private static final int FALL_LIMIT = FLOOR_Y - 24;
    private static final String ROOT = "dmzzero";
    private static final String RETURN = "realmReturn";
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState RIM = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState BODY = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState STANDS = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState RAIL = Blocks.GLASS.defaultBlockState();
    // chunk tickets that load a stage, or a fighter's way home, in the background before anyone goes there: a stage or a
    // home spot loaded on the spot holds the server thread for a few hundred ms. a stage's ticket is let go when its lobby
    // is called off or its battle is over; both also run out on their own, the stage's after the longest join time
    // ([versus.battles] joinSeconds is 600 at most), by when the fighters' own tickets hold it, and the way home's after the trip
    private static final TicketType<ChunkPos> STAGE_TICKET = TicketType.create("dmzzero_stage", Comparator.comparingLong(ChunkPos::toLong), 20 * 610);
    private static final TicketType<ChunkPos> HOME_TICKET = TicketType.create("dmzzero_home", Comparator.comparingLong(ChunkPos::toLong), 20 * 15);
    private static final int HOME_REACH = 2;
    private static int sentHome;
    private static int lastBuilt;

    private NullRealm() {
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(KEY);
    }

    public static boolean in(Entity e) {
        return e.level().dimension().equals(KEY);
    }

    public static Vec3 center(int slot) {
        return new Vec3(slotX(slot) + 0.5, STAND_Y, 0.5);
    }

    public static Vec3 stands(int slot, int radius) {
        return new Vec3(slotX(slot) + 0.5, STANDS_FLOOR + 1, -(radius + STANDS_GAP) + 0.5);
    }

    /** Starts loading a stage's chunks in the background, for a battle that may start there. */
    public static void preload(ServerLevel level, int slot, int radius) {
        ChunkPos at = new ChunkPos(slotX(slot) >> 4, 0);
        level.getChunkSource().addRegionTicket(STAGE_TICKET, at, BattleMath.chunkReach(slotX(slot), reach(radius)), at);
    }

    /** Lets a preloaded stage go again, once its lobby is called off or its battle is over. */
    public static void release(ServerLevel level, int slot, int radius) {
        ChunkPos at = new ChunkPos(slotX(slot) >> 4, 0);
        level.getChunkSource().removeRegionTicket(STAGE_TICKET, at, BattleMath.chunkReach(slotX(slot), reach(radius)), at);
    }

    /** Starts loading the spot a fighter goes home to, in the background, while the battle's last seconds run. */
    public static void preloadHome(ServerPlayer p) {
        CompoundTag t = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ROOT).getCompound(RETURN);
        if (t.isEmpty()) return;
        ResourceLocation id = ResourceLocation.tryParse(t.getString("dim"));
        ServerLevel to = id == null ? null : p.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (to == null || to.dimension().equals(KEY)) return;
        ChunkPos at = new ChunkPos(BlockPos.containing(t.getDouble("x"), t.getDouble("y"), t.getDouble("z")));
        to.getChunkSource().addRegionTicket(HOME_TICKET, at, HOME_REACH, at);
    }

    // how far from a slot's middle build() lays blocks: the stage, the gap and the stands
    private static int reach(int radius) {
        return radius + STANDS_GAP + STANDS_RADIUS + 2;
    }

    // lays the stage fresh, filling every crater, and the stands beside it; returns how many blocks changed
    public static int build(ServerLevel level, int slot, int radius) {
        int cx = slotX(slot);
        int reach = reach(radius);
        for (int x = (cx - reach) >> 4; x <= (cx + reach) >> 4; x++) {
            for (int z = (-reach) >> 4; z <= reach >> 4; z++) level.getChunk(x, z);
        }
        int changed = 0;
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                boolean disc = inDisc(dx, dz, radius);
                boolean edge = rim(dx, dz, radius);
                for (int y = FLOOR_Y - THICKNESS + 1; y <= FLOOR_Y + HEADROOM; y++) {
                    BlockState want = !disc || y > FLOOR_Y ? AIR : y == FLOOR_Y ? (edge ? RIM : FLOOR) : BODY;
                    changed += set(level, new BlockPos(cx + dx, y, dz), want);
                }
            }
        }
        int sz = -(radius + STANDS_GAP);
        for (int dx = -STANDS_RADIUS - 1; dx <= STANDS_RADIUS + 1; dx++) {
            for (int dz = -STANDS_RADIUS - 1; dz <= STANDS_RADIUS + 1; dz++) {
                if (!inDisc(dx, dz, STANDS_RADIUS)) continue;
                boolean edge = rim(dx, dz, STANDS_RADIUS);
                changed += set(level, new BlockPos(cx + dx, STANDS_FLOOR, sz + dz), STANDS);
                changed += set(level, new BlockPos(cx + dx, STANDS_FLOOR + 1, sz + dz), edge ? RAIL : AIR);
                for (int y = STANDS_FLOOR + 2; y <= STANDS_FLOOR + 4; y++) changed += set(level, new BlockPos(cx + dx, y, sz + dz), AIR);
            }
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(cx - reach, FLOOR_Y - 40, -reach, cx + reach, FLOOR_Y + 40, reach))) {
            item.discard();
        }
        lastBuilt = changed;
        return changed;
    }

    private static int set(ServerLevel level, BlockPos pos, BlockState want) {
        if (level.getBlockState(pos) == want) return 0;
        level.setBlock(pos, want, Block.UPDATE_CLIENTS);
        return 1;
    }

    // only the stage itself can be broken by ki; the stands stay put
    public static boolean griefable(BlockPos pos) {
        int radius = ZeroConfig.SERVER.stageRadius.get();
        int slot = (int) Math.round(pos.getX() / (double) BattleMath.SLOT_SPACING);
        int dx = pos.getX() - slotX(slot);
        return pos.getY() <= FLOOR_Y + 2 && pos.getY() >= FLOOR_Y - THICKNESS - 1 && inDisc(dx, pos.getZ(), radius + 2);
    }

    public static boolean onStageBlock(ServerLevel level, int slot, int dx, int dz) {
        return level.getBlockState(new BlockPos(slotX(slot) + dx, FLOOR_Y, dz)).is(Blocks.SMOOTH_STONE);
    }

    // ---- getting people in and back out

    public static void saveReturn(ServerPlayer p, boolean flying) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", p.level().dimension().location().toString());
        t.putDouble("x", p.getX());
        t.putDouble("y", p.getY());
        t.putDouble("z", p.getZ());
        t.putFloat("yaw", p.getYRot());
        t.putFloat("pitch", p.getXRot());
        t.putBoolean("fly", flying);
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        root.put(RETURN, t);
        persisted.put(ROOT, root);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static boolean hasReturn(ServerPlayer p) {
        return hasReturn(p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG));
    }

    public static boolean hasReturn(CompoundTag persisted) {
        return persisted.getCompound(ROOT).contains(RETURN);
    }

    private static CompoundTag takeReturn(ServerPlayer p) {
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        if (!root.contains(RETURN)) return null;
        CompoundTag t = root.getCompound(RETURN);
        root.remove(RETURN);
        persisted.put(ROOT, root);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        return t;
    }

    public static void clearReturn(ServerPlayer p) {
        takeReturn(p);
    }

    // back to where they were when they joined, or the world spawn if that's gone
    public static void sendHome(ServerPlayer p) {
        CompoundTag t = takeReturn(p);
        MinecraftServer server = p.server;
        ServerLevel to = null;
        if (t != null) {
            ResourceLocation id = ResourceLocation.tryParse(t.getString("dim"));
            if (id != null) to = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        }
        // fake players can't change worlds, so the self-test's ones come from the realm and go back there
        boolean usable = to != null && (!to.dimension().equals(KEY) || p instanceof FakePlayer);
        if (usable) {
            place(p, to, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yaw"), t.getFloat("pitch"));
            if (t.getBoolean("fly")) setFlying(p, true);
        } else {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
            place(p, overworld, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, p.getYRot(), 0f);
        }
        p.fallDistance = 0;
        sentHome++;
    }

    public static void place(ServerPlayer p, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        if (p instanceof FakePlayer) {
            if (p.level() == level) p.moveTo(x, y, z, yaw, pitch);
            return;
        }
        p.teleportTo(level, x, y, z, yaw, pitch);
    }

    static void setFlying(ServerPlayer p, boolean flying) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
        if (data == null || data.getSkills().isSkillActive("fly") == flying) return;
        data.getSkills().setSkillActive("fly", flying);
        if (p.connection != null) NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(p), p);
    }

    // ---- the realm's rules

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        long start = TickCost.start();
        playerTick(event);
        TickCost.add(COST, start);
    }

    private static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || !in(p)) return;
        if (p.isCreative() || p.isSpectator()) return;
        setFlying(p, false);
        if (p.tickCount % 20 == 0) guard(p);
    }

    // anyone here without a battle goes home; anyone watching who falls goes back to the stands
    public static void guard(ServerPlayer p) {
        if (!Battles.involved(p)) {
            p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.battle.realm_closed",
                    "The Null Realm only opens for a battle.").withStyle(ChatFormatting.GRAY));
            sendHome(p);
            return;
        }
        if (p.getY() < FALL_LIMIT) Battles.catchFall(p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !in(p)) return;
        Battles.loggedBackIn(p);
        if (!Battles.involved(p) && !p.isCreative() && !p.isSpectator()) sendHome(p);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null || event.getPlayer().isCreative() || !(event.getLevel() instanceof Level level)) return;
        if (level.dimension().equals(KEY)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player p) || p.isCreative() || !(event.getLevel() instanceof Level level)) return;
        if (level.dimension().equals(KEY)) event.setCanceled(true);
    }

    public static int sentHome() {
        return sentHome;
    }

    public static int lastBuilt() {
        return lastBuilt;
    }
}
