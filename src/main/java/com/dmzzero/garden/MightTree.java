package com.dmzzero.garden;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.heal.HealSave;
import com.dmzzero.heal.WorldHealing;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.init.MainGameRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// turles planted the tree of might to drain a planet's life. a might tree fruit planted in grass (crouching, so eating one
// doesn't plant it) grows over a few days into a tree of dmz's own tree of might blocks; grown, it drains the grass around it
// and hangs a fruit every few days. cut at its base, the whole tree withers and the drained grass heals
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class MightTree {
    private static final ResourceLocation FRUIT = new ResourceLocation("dragonminez", "might_tree_fruit");
    private static final int MAX_HANGING = 3;
    private static final int DRAIN_DEPTH = 4;
    private static final Set<ResourceKey<Level>> GROWING = new HashSet<>();
    private static final int COST = TickCost.id("world.treeOfMight", GROWING::size);

    private MightTree() {
    }

    private static Block block(String path) {
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation("dragonminez", path));
    }

    public static Block root() {
        return block("might_tree_root");
    }

    public static Block wood() {
        return block("might_tree_wood");
    }

    public static Block leaves() {
        return block("might_tree_leaves");
    }

    public static Block fruitBlock() {
        return block("might_tree_fruit_block");
    }

    /** DMZ's Might Tree Fruit, or empty when DMZ doesn't have it. */
    public static ItemStack fruit(int count) {
        Item item = ForgeRegistries.ITEMS.getValue(FRUIT);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    public static boolean isFruit(ItemStack stack) {
        return !stack.isEmpty() && FRUIT.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    static boolean soil(BlockState state) {
        return state.is(BlockTags.DIRT);
    }

    private static boolean ours(BlockState s) {
        return s.is(root()) || s.is(wood()) || s.is(leaves()) || s.is(fruitBlock());
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        BlockPos pos = event.getPos();
        if (!event.getEntity().isShiftKeyDown() || !isFruit(event.getItemStack()) || event.getFace() != Direction.UP) return;
        if (!(event.getLevel() instanceof ServerLevel level)) {
            // a dmz zero client doesn't start eating the fruit it's planting
            if (soil(event.getLevel().getBlockState(pos)) && event.getLevel().getBlockState(pos.above()).canBeReplaced()
                    && ZeroConfig.read(ZeroConfig.SERVER.mightTree, false)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        if (!ZeroConfig.SERVER.mightTree.get() || !(event.getEntity() instanceof ServerPlayer player) || !soil(level.getBlockState(pos))) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        plant(player, level, pos, event.getFace(), event.getItemStack());
    }

    /** Puts a fruit in the ground as a root sprout. "planted", or why not: off, face, soil, blocked, too_many, full. */
    public static String plant(ServerPlayer player, ServerLevel level, BlockPos soil, Direction face, ItemStack stack) {
        if (!ZeroConfig.SERVER.mightTree.get()) return "off";
        if (face != Direction.UP) return "face";
        if (!soil(level.getBlockState(soil))) return "soil";
        BlockPos base = soil.above();
        MightTreeSave save = MightTreeSave.get(level);
        BlockState up = level.getBlockState(base);
        if (!up.canBeReplaced() || !up.getFluidState().isEmpty() || save.has(base.asLong()) || !player.mayUseItemAt(base, face, stack)) return "blocked";
        int max = ZeroConfig.SERVER.mightTrees.get();
        if (owned(level.getServer(), player.getUUID()) >= max) {
            tell(player, "too_many", "You already have %s Trees of Might.", max);
            return "too_many";
        }
        if (save.size() >= MightTreeSave.MAX) {
            tell(player, "full", "Too many Trees of Might grow here already.");
            return "full";
        }
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, base);
        level.setBlock(base, root().defaultBlockState(), Block.UPDATE_ALL);
        if (ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)) {
            snapshot.restore(true, false);
            return "blocked";
        }
        long now = level.getGameTime();
        save.add(new MightTreeSave.Tree(base.asLong(), player.getUUID(), now, 0, now, List.of()));
        GROWING.add(level.dimension());
        PlantGuard.mark(player);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, base, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1f, 0.6f);
        tell(player, "planted", "The fruit sinks into the ground. A Tree of Might grows here in %s in-game days.", days(ZeroConfig.SERVER.mightGrowDays.get()));
        return "planted";
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
        if (!GROWING.contains(level.dimension()) || !ZeroConfig.SERVER.mightTree.get()) return;
        long start = TickCost.start();
        tick(level, level.getGameTime());
        TickCost.add(COST, start);
    }

    /** Once a second for each tree in a loaded chunk: wither if cut, grow a stage when it's time, drain, bear fruit. */
    public static void tick(ServerLevel level, long now) {
        MightTreeSave save = MightTreeSave.get(level);
        if (save.size() == 0) {
            GROWING.remove(level.dimension());
            return;
        }
        long grow = SenzuMath.ripenTicks(ZeroConfig.SERVER.mightGrowDays.get());
        long fruitEvery = SenzuMath.ripenTicks(ZeroConfig.SERVER.mightFruitDays.get());
        long drainEvery = ZeroConfig.SERVER.mightDrainSeconds.get() * 20L;
        for (MightTreeSave.Tree t : save.all()) {
            BlockPos base = BlockPos.of(t.base());
            if (!level.isLoaded(base)) continue;
            if (!level.getBlockState(base).is(t.stage() == 0 ? root() : wood())) {
                // knocked out in a ki fight that the world will heal: wait for it
                if (HealSave.get(level).queue().pending(t.base())) continue;
                wither(level, t);
                save.forget(t.base());
                continue;
            }
            int target = MightTreeMath.stage(now - t.planted(), grow);
            if (target > t.stage()) {
                List<MightTreeMath.Piece> tree = MightTreeMath.blueprint(t.base());
                List<MightTreeMath.Piece> part = MightTreeMath.part(tree, target);
                // a piece in an unloaded chunk would be skipped and never come back once the stage is saved, so the tree
                // waits to grow until all of it is loaded
                if (!allLoaded(level, base, part)) continue;
                for (MightTreeMath.Piece p : part) place(level, base, p);
                t = new MightTreeSave.Tree(t.base(), t.owner(), t.planted(), target, target == MightTreeMath.GROWN ? now : t.lastFruit(), t.drained());
                save.put(t);
                level.playSound(null, base, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1f, 0.5f);
                if (target == MightTreeMath.GROWN) tellOwner(level, t, "grown", "Your Tree of Might has grown. It's draining the land around it.");
            }
            if (t.stage() != MightTreeMath.GROWN) continue;
            if (drainEvery > 0 && now % drainEvery == 0) drainOnce(level, base);
            if (SenzuMath.ripe(now, t.lastFruit(), fruitEvery)) {
                if (hanging(level, base) < MAX_HANGING) hang(level, base);
                MightTreeSave.Tree f = save.find(t.base());
                if (f != null) save.put(new MightTreeSave.Tree(f.base(), f.owner(), f.planted(), f.stage(), now, f.drained()));
            }
        }
    }

    // whose ki griefing the drain is: the owner, or with them offline a stand-in under their profile, so dmz still applies
    // its players' gamerule (it would read the mobs' one for no one) and WorldGuard still sees the owner
    public static net.minecraft.world.entity.player.Player griefer(ServerLevel level, java.util.UUID owner) {
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(owner);
        if (online != null) return online;
        var cache = level.getServer().getProfileCache();
        com.mojang.authlib.GameProfile profile = cache == null ? null : cache.get(owner).orElse(null);
        if (profile == null) profile = new com.mojang.authlib.GameProfile(owner, "[DMZZeroTree]");
        return net.minecraftforge.common.util.FakePlayerFactory.get(level, profile);
    }

    static boolean allLoaded(ServerLevel level, BlockPos base, List<MightTreeMath.Piece> part) {
        for (MightTreeMath.Piece p : part) if (!level.isLoaded(base.offset(p.dx(), p.dy(), p.dz()))) return false;
        return true;
    }

    // only into air, plants and the tree's own blocks, never over anything built
    private static void place(ServerLevel level, BlockPos base, MightTreeMath.Piece p) {
        BlockPos pos = base.offset(p.dx(), p.dy(), p.dz());
        if (!level.isLoaded(pos)) return;
        BlockState now = level.getBlockState(pos);
        if (!now.isAir() && !ours(now) && !(now.canBeReplaced() && now.getFluidState().isEmpty())) return;
        Block b = p.kind() == MightTreeMath.ROOT ? root() : p.kind() == MightTreeMath.WOOD ? wood() : leaves();
        level.setBlock(pos, b.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** One grass block near the grown tree turns to coarse dirt. True if one did. */
    public static boolean drainOnce(ServerLevel level, BlockPos base) {
        MightTreeSave save = MightTreeSave.get(level);
        MightTreeSave.Tree t = save.find(base.asLong());
        int radius = ZeroConfig.SERVER.mightDrainRadius.get();
        if (t == null || t.stage() != MightTreeMath.GROWN || t.drained().size() >= ZeroConfig.SERVER.mightMaxDrained.get() || radius <= 0) return false;
        int dx = level.getRandom().nextInt(radius * 2 + 1) - radius, dz = level.getRandom().nextInt(radius * 2 + 1) - radius;
        if (!MightTreeMath.inDrain(dx, dz, radius)) return false;
        for (int dy = 1; dy >= -DRAIN_DEPTH; dy--) {
            BlockPos pos = base.offset(dx, dy - 1, dz);
            if (!level.isLoaded(pos)) return false;
            BlockState s = level.getBlockState(pos);
            if (!s.is(Blocks.GRASS_BLOCK)) {
                if (s.isAir() || s.canBeReplaced()) continue;
                return false;
            }
            if (!MainGameRules.canKiGrief(level, pos, griefer(level, t.owner()))) return false;
            level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
            level.sendParticles(ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 6, 0.3, 0.05, 0.3, 0);
            List<Long> drained = new ArrayList<>(t.drained());
            drained.add(pos.asLong());
            save.put(new MightTreeSave.Tree(t.base(), t.owner(), t.planted(), t.stage(), t.lastFruit(), drained));
            return true;
        }
        return false;
    }

    // the canopy's underside, where fruit hangs
    private static List<BlockPos> underside(BlockPos base) {
        List<MightTreeMath.Piece> tree = MightTreeMath.blueprint(base.asLong());
        Set<String> taken = new HashSet<>();
        for (MightTreeMath.Piece p : tree) taken.add(p.dx() + "," + p.dy() + "," + p.dz());
        List<BlockPos> out = new ArrayList<>();
        for (MightTreeMath.Piece p : tree) {
            if (p.kind() == MightTreeMath.LEAVES && !taken.contains(p.dx() + "," + (p.dy() - 1) + "," + p.dz())) {
                out.add(base.offset(p.dx(), p.dy() - 1, p.dz()));
            }
        }
        return out;
    }

    public static long hanging(ServerLevel level, BlockPos base) {
        Block fruit = fruitBlock();
        return underside(base).stream().filter(p -> level.isLoaded(p) && level.getBlockState(p).is(fruit)).count();
    }

    private static void hang(ServerLevel level, BlockPos base) {
        List<BlockPos> spots = underside(base);
        Collections.shuffle(spots, new java.util.Random(level.getRandom().nextLong()));
        for (BlockPos p : spots) {
            if (!level.isLoaded(p) || !level.getBlockState(p).isAir() || !level.getBlockState(p.above()).is(leaves())) continue;
            level.setBlock(p, fruitBlock().defaultBlockState(), Block.UPDATE_ALL);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
            return;
        }
    }

    // the whole tree goes, and the grass it drained heals the way ki damage does (or at once, with healing off)
    private static void wither(ServerLevel level, MightTreeSave.Tree t) {
        BlockPos base = BlockPos.of(t.base());
        List<BlockPos> spots = new ArrayList<>(underside(base));
        for (MightTreeMath.Piece p : MightTreeMath.part(MightTreeMath.blueprint(t.base()), t.stage())) spots.add(base.offset(p.dx(), p.dy(), p.dz()));
        for (BlockPos p : spots) {
            if (level.isLoaded(p) && ours(level.getBlockState(p))) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, base.getX() + 0.5, base.getY() + 2, base.getZ() + 0.5, 30, 1.0, 2.0, 1.0, 0.02);
        level.playSound(null, base, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.2f, 0.5f);
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        for (long d : t.drained()) {
            BlockPos p = BlockPos.of(d);
            if (!level.isLoaded(p) || !level.getBlockState(p).is(Blocks.COARSE_DIRT)) continue;
            if (WorldHealing.willRecord(level, p, grass, false)) WorldHealing.record(level, p, grass, level.getBlockState(p), false);
            else level.setBlock(p, grass, Block.UPDATE_ALL);
        }
        tellOwner(level, t, "withered", "Your Tree of Might withers away, and the land starts to heal.");
    }

    static int owned(MinecraftServer server, UUID owner) {
        int n = 0;
        for (ServerLevel l : server.getAllLevels()) n += MightTreeSave.get(l).owned(owner);
        return n;
    }

    private static String days(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static void tellOwner(ServerLevel level, MightTreeSave.Tree t, String why, String english) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(t.owner());
        if (owner != null) owner.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.might." + why, english).withStyle(ChatFormatting.DARK_GREEN));
    }

    private static void tell(ServerPlayer player, String why, String english, Object... args) {
        player.displayClientMessage(Component.translatableWithFallback("message.dmzzero.might." + why, english, args), true);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        for (ServerLevel l : event.getServer().getAllLevels()) if (MightTreeSave.get(l).size() > 0) GROWING.add(l.dimension());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GROWING.clear();
    }
}
