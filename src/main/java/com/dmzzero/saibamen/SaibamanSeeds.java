package com.dmzzero.saibamen;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.status.TickCost;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// nappa planted six seeds and saibamen climbed out of the ground. dmz's saibamen drop a seed now and then; plant it and one
// of the six climbs out a minute later. the seed is wheat seeds with a tag, so no new item reaches the registry and a
// server-only install still lets players without dmz zero join; dmz zero clients draw it with its own texture
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class SaibamanSeeds {
    public static final int MODEL = 8401001;
    private static final String SEED = "saibamanSeed";
    private static final String SPROUTED = "dmzzeroSprouted";
    private static final double WATCHER_RANGE = 48;
    private static final double STIR_RANGE = 32;
    private static final Set<ResourceKey<Level>> GROWING = new HashSet<>();
    private static final int COST = TickCost.id("world.saibamen", GROWING::size);
    private static Double testRoll;
    private static boolean testNoWatcher;

    private SaibamanSeeds() {
    }

    public static ItemStack seed(int count) {
        ItemStack s = new ItemStack(Items.WHEAT_SEEDS, count);
        CompoundTag root = new CompoundTag();
        root.putBoolean(SEED, true);
        s.getOrCreateTag().put(DMZZero.MOD_ID, root);
        s.getOrCreateTag().putInt("CustomModelData", MODEL);
        s.setHoverName(Component.translatableWithFallback("item.dmzzero.saibaman_seed", "Saibaman Seed")
                .withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GREEN)));
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatableWithFallback("item.dmzzero.saibaman_seed.lore",
                "Plant it in farmland or dirt.").withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY)))));
        s.getOrCreateTagElement("display").put("Lore", lore);
        return s;
    }

    public static boolean isSeed(ItemStack stack) {
        return stack.is(Items.WHEAT_SEEDS) && stack.hasTag() && stack.getTag().getCompound(DMZZero.MOD_ID).getBoolean(SEED);
    }

    // both sides cancel, so a dmz zero client never shows wheat sprouting from it; the server sends the real blocks back anyway
    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isSeed(event.getItemStack())) return;
        event.setCanceled(true);
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ServerPlayer player) {
            String r = plant(player, level, event.getPos(), event.getFace(), event.getItemStack());
            event.setCancellationResult("planted".equals(r) ? InteractionResult.SUCCESS : InteractionResult.FAIL);
        } else {
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    // chickens and parrots would eat it
    @SubscribeEvent
    public static void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        if (!isSeed(event.getItemStack()) || !(event.getTarget() instanceof Chicken || event.getTarget() instanceof Parrot)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    /** Puts a seed in the ground. Returns "planted" or why not: off, face, soil, blocked, too_many, full. */
    public static String plant(ServerPlayer player, ServerLevel level, BlockPos soil, Direction face, ItemStack stack) {
        if (!ZeroConfig.SERVER.saibamanSeeds.get()) return "off";
        if (face != Direction.UP) return "face";
        if (!soil(level.getBlockState(soil))) return "soil";
        BlockPos above = soil.above();
        BlockState up = level.getBlockState(above);
        SproutSave save = SproutSave.get(level);
        if (!up.canBeReplaced() || !up.getFluidState().isEmpty() || save.has(soil.asLong()) || !player.mayUseItemAt(above, face, stack)) {
            return "blocked";
        }
        int max = ZeroConfig.SERVER.sproutsPerPlayer.get();
        if (!SproutMath.canPlant(owned(level.getServer(), player.getUUID()), max)) {
            tell(player, "message.dmzzero.saibaman.too_many", "You already have %s Saibaman seeds growing.", max);
            return "too_many";
        }
        int seconds = ZeroConfig.SERVER.sproutSeconds.get();
        if (!save.add(new SproutSave.Sprout(soil.asLong(), player.getUUID(), SproutMath.readyAt(level.getGameTime(), seconds)))) {
            tell(player, "message.dmzzero.saibaman.full", "Too many Saibaman seeds are growing here already.");
            return "full";
        }
        GROWING.add(level.dimension());
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, soil, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 0.7f);
        crumbs(level, soil, 8);
        tell(player, "message.dmzzero.saibaman.planted", "Something stirs under the soil. It climbs out in %s seconds.", seconds);
        return "planted";
    }

    static boolean soil(BlockState state) {
        return state.is(Blocks.FARMLAND) || state.is(BlockTags.DIRT);
    }

    static int owned(MinecraftServer server, UUID owner) {
        int n = 0;
        for (ServerLevel l : server.getAllLevels()) n += SproutSave.get(l).owned(owner);
        return n;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
        if (!GROWING.contains(level.dimension())) return;
        long start = TickCost.start();
        grow(level);
        TickCost.add(COST, start);
    }

    /** Once a second: ripe seeds with someone nearby climb out. Seeds in unloaded chunks wait. */
    public static int grow(ServerLevel level) {
        SproutSave save = SproutSave.get(level);
        if (save.size() == 0) {
            GROWING.remove(level.dimension());
            return 0;
        }
        long now = level.getGameTime();
        int out = 0;
        for (SproutSave.Sprout s : save.all()) {
            BlockPos soil = BlockPos.of(s.soil());
            if (!level.isLoaded(soil)) continue;
            if (!SproutMath.ready(s.readyAt(), now)) {
                stir(level, soil, s.readyAt() - now, now);
                continue;
            }
            if (!soil(level.getBlockState(soil))) {
                // dug up before it grew
                Block.popResource(level, soil.above(), seed(1));
                save.remove(s);
                continue;
            }
            if (level.getDifficulty() == Difficulty.PEACEFUL) continue;
            if (!testNoWatcher && level.getNearestPlayer(soil.getX() + 0.5, soil.getY() + 1, soil.getZ() + 0.5, WATCHER_RANGE, false) == null) {
                continue;
            }
            if (sprout(level, soil, s.owner()) != null) out++;
            else Block.popResource(level, soil.above(), seed(1));
            save.remove(s);
        }
        return out;
    }

    /** A Saibaman climbs out above the soil. Null if DMZ's entity is missing or wouldn't spawn. */
    public static Entity sprout(ServerLevel level, BlockPos soil, UUID owner) {
        int kind = SproutMath.kind(level.random.nextDouble());
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dragonminez", SproutMath.entityPath(kind)));
        if (type == null) return null;
        Entity e = type.spawn(level, soil.above(), MobSpawnType.MOB_SUMMONED);
        if (e == null) return null;
        // spawn returns the entity even when a join handler (a claim or arena guard) refused it; then the seed comes back
        if (e.isRemoved() || level.getEntity(e.getUUID()) != e) {
            DMZZero.LOG.info("A Saibaman from a seed at {} wasn't let into the world ({}); the seed pops back out", soil.above(), e.getRemovalReason());
            return null;
        }
        e.getPersistentData().putBoolean(SPROUTED, true);
        crumbs(level, soil, 30);
        level.playSound(null, soil, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.2f, 0.6f);
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
        if (p != null && p.level() == level && p.distanceToSqr(e) < WATCHER_RANGE * WATCHER_RANGE) {
            tell(p, "message.dmzzero.saibaman.sprouted", "A Saibaman climbs out of the ground!");
        }
        return e;
    }

    // the soil shifts every few seconds, more often near the end
    private static void stir(ServerLevel level, BlockPos soil, long left, long now) {
        if (now % (left < 200 ? 20 : 60) != 0) return;
        if (level.getNearestPlayer(soil.getX() + 0.5, soil.getY() + 1, soil.getZ() + 0.5, STIR_RANGE, false) == null) return;
        crumbs(level, soil, left < 200 ? 6 : 3);
    }

    private static void crumbs(ServerLevel level, BlockPos soil, int count) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(soil)),
                soil.getX() + 0.5, soil.getY() + 1.05, soil.getZ() + 0.5, count, 0.2, 0.05, 0.2, 0.05);
    }

    public static boolean isSprouted(Entity e) {
        return e.getPersistentData().getBoolean(SPROUTED);
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || !ZeroConfig.SERVER.saibamanSeeds.get()) return;
        if (!(event.getSource().getEntity() instanceof Player)) return;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(dead.getType());
        if (id == null || !SproutMath.isSaibaman(id.getNamespace(), id.getPath())) return;
        double chance = isSprouted(dead) ? ZeroConfig.SERVER.sproutedSeedChance.get() : ZeroConfig.SERVER.seedChance.get();
        double roll = testRoll != null ? testRoll : dead.getRandom().nextDouble();
        if (!SproutMath.drops(roll, chance)) return;
        event.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(), seed(1)));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        for (ServerLevel l : event.getServer().getAllLevels()) if (SproutSave.get(l).size() > 0) GROWING.add(l.dimension());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GROWING.clear();
        testRoll = null;
        testNoWatcher = false;
    }

    private static void tell(ServerPlayer player, String key, String english, Object... args) {
        player.displayClientMessage(Component.translatableWithFallback(key, english, args), true);
    }

    /** Self-test only: a fixed drop roll, or null for random. */
    public static void setTestRoll(Double roll) {
        testRoll = roll;
    }

    /** Self-test only: grow without a real player nearby (fake players aren't in the level's player list). */
    public static void setTestNoWatcher(boolean on) {
        testNoWatcher = on;
    }
}
