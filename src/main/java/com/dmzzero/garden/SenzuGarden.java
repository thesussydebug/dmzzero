package com.dmzzero.garden;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.heal.HealSave;
import com.dmzzero.status.TickCost;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// korin grows the senzu, and yajirobe ate half his harvest. dmz's senzu bean planted in farmland grows a bush that
// gives beans every few in-game days. the bush is vanilla's sweet berry bush, so no block reaches the registry and players
// without dmz zero can still join; only dmz zero's clock grows it, and picking or digging it up gives senzu, not berries
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class SenzuGarden {
    static final ResourceLocation SENZU = new ResourceLocation("dragonminez", "senzu_bean");
    static final ResourceLocation KORIN = new ResourceLocation("dragonminez", "master_karin");
    private static final Set<ResourceKey<Level>> GROWING = new HashSet<>();
    private static final int COST = TickCost.id("world.senzuGarden", GROWING::size);

    private SenzuGarden() {
    }

    /** DMZ's senzu bean, or empty when DMZ doesn't have it. */
    public static ItemStack bean(int count) {
        Item item = ForgeRegistries.ITEMS.getValue(SENZU);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    public static boolean isBean(ItemStack stack) {
        return !stack.isEmpty() && SENZU.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    // farmland only, so eating a senzu while looking at the ground doesn't plant it
    static boolean soil(BlockState state) {
        return state.is(Blocks.FARMLAND);
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        BlockPos pos = event.getPos();
        if (!(event.getLevel() instanceof ServerLevel level)) {
            // a dmz zero client doesn't start eating the bean it's planting
            if (isBean(event.getItemStack()) && event.getFace() == Direction.UP && soil(event.getLevel().getBlockState(pos))
                    && event.getLevel().getBlockState(pos.above()).canBeReplaced() && ZeroConfig.read(ZeroConfig.SERVER.senzuGarden, false)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        if (!ZeroConfig.SERVER.senzuGarden.get() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (SenzuSave.get(level).has(pos.asLong())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (event.getHand() == InteractionHand.MAIN_HAND) harvest(player, level, pos);
            return;
        }
        if (!isBean(event.getItemStack()) || event.getFace() != Direction.UP || !soil(level.getBlockState(pos))) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        plant(player, level, pos, event.getFace(), event.getItemStack());
    }

    /** Puts a senzu in the ground. Returns "planted" or why not: off, face, soil, blocked, too_many, full. */
    public static String plant(ServerPlayer player, ServerLevel level, BlockPos soil, Direction face, ItemStack stack) {
        if (!ZeroConfig.SERVER.senzuGarden.get()) return "off";
        if (face != Direction.UP) return "face";
        if (!soil(level.getBlockState(soil))) return "soil";
        BlockPos at = soil.above();
        SenzuSave save = SenzuSave.get(level);
        BlockState up = level.getBlockState(at);
        if (!up.canBeReplaced() || !up.getFluidState().isEmpty() || save.has(at.asLong()) || !player.mayUseItemAt(at, face, stack)) return "blocked";
        int max = ZeroConfig.SERVER.senzuBushes.get();
        if (owned(level.getServer(), player.getUUID()) >= max) {
            tell(player, "too_many", "You already have %s senzu bushes growing.", max);
            return "too_many";
        }
        if (save.size() >= SenzuSave.MAX) {
            tell(player, "full", "Too many senzu bushes are growing here already.");
            return "full";
        }
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, at);
        level.setBlock(at, Blocks.SWEET_BERRY_BUSH.defaultBlockState(), Block.UPDATE_ALL);
        // claim and protection mods answer the same place event a placed block gets
        if (ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)) {
            snapshot.restore(true, false);
            return "blocked";
        }
        long now = level.getGameTime();
        boolean korin = nearKorin(level, at);
        SenzuSave.Bush bush = new SenzuSave.Bush(at.asLong(), player.getUUID(), now, false, korin);
        save.add(bush);
        GROWING.add(level.dimension());
        PlantGuard.mark(player);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, at, SoundEvents.SWEET_BERRY_BUSH_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
        if (korin) tell(player, "planted_korin", "The senzu takes root near Korin. Its first bean is ready in %s in-game days.", days(bush));
        else tell(player, "planted", "The senzu takes root. Its first bean is ready in %s in-game days.", days(bush));
        return "planted";
    }

    /** Picks a ripe bush. Returns "picked" or why not: not_bush, not_ripe. */
    public static String harvest(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SenzuSave save = SenzuSave.get(level);
        SenzuSave.Bush b = save.find(pos.asLong());
        if (b == null || !level.getBlockState(pos).is(Blocks.SWEET_BERRY_BUSH)) return "not_bush";
        long now = level.getGameTime(), ripen = ripen(b);
        if (!SenzuMath.ripe(now, b.since(), ripen)) {
            tell(player, "not_ripe", "Not ripe yet: about %s more in-game hours.", SenzuMath.hoursLeft(now, b.since(), ripen));
            return "not_ripe";
        }
        Block.popResource(level, pos, bean(ZeroConfig.SERVER.senzuBeans.get()));
        save.picked(pos.asLong(), now);
        level.setBlock(pos, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1f, 0.8f);
        return "picked";
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
        if (!GROWING.contains(level.dimension()) || !ZeroConfig.SERVER.senzuGarden.get()) return;
        long start = TickCost.start();
        grow(level);
        TickCost.add(COST, start);
    }

    /** Once a second: each loaded bush shows its stage. A bush gone for good is forgotten; one healing will bring back waits. */
    public static int grow(ServerLevel level) {
        SenzuSave save = SenzuSave.get(level);
        if (save.size() == 0) {
            GROWING.remove(level.dimension());
            return 0;
        }
        long now = level.getGameTime();
        int changed = 0;
        for (SenzuSave.Bush b : save.all()) {
            long ripen = ripen(b);
            BlockPos pos = BlockPos.of(b.pos());
            if (!level.isLoaded(pos)) continue;
            BlockState s = level.getBlockState(pos);
            if (!s.is(Blocks.SWEET_BERRY_BUSH)) {
                if (!HealSave.get(level).queue().pending(b.pos())) save.forget(b.pos());
                continue;
            }
            int age = SenzuMath.age(now, b.since(), ripen, b.picked());
            if (s.getValue(SweetBerryBushBlock.AGE) == age) continue;
            level.setBlock(pos, s.setValue(SweetBerryBushBlock.AGE, age), Block.UPDATE_CLIENTS);
            if (age == SenzuMath.RIPE) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0);
            changed++;
        }
        return changed;
    }

    // random ticks and bone meal would ripen it in minutes
    @SubscribeEvent
    public static void onCropGrow(BlockEvent.CropGrowEvent.Pre event) {
        if (!event.getState().is(Blocks.SWEET_BERRY_BUSH) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (ZeroConfig.SERVER.senzuGarden.get() && SenzuSave.get(level).has(event.getPos().asLong())) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (!event.getBlock().is(Blocks.SWEET_BERRY_BUSH) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (ZeroConfig.SERVER.senzuGarden.get() && SenzuSave.get(level).has(event.getPos().asLong())) event.setCanceled(true);
    }

    // korin's bush doesn't prick
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!event.getSource().is(DamageTypes.SWEET_BERRY_BUSH)) return;
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || !ZeroConfig.SERVER.senzuGarden.get()) return;
        SenzuSave save = SenzuSave.get(level);
        if (save.size() == 0) return;
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(e.getBoundingBox().minX, e.getBoundingBox().minY, e.getBoundingBox().minZ),
                BlockPos.containing(e.getBoundingBox().maxX, e.getBoundingBox().maxY, e.getBoundingBox().maxZ))) {
            if (save.has(p.asLong())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    // dug up: the bean comes back, and a ripe bush drops no sweet berries
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.getState().is(Blocks.SWEET_BERRY_BUSH) || !(event.getLevel() instanceof ServerLevel level) || !ZeroConfig.SERVER.senzuGarden.get()) return;
        SenzuSave save = SenzuSave.get(level);
        long at = event.getPos().asLong();
        if (!save.has(at)) return;
        event.setCanceled(true);
        save.forget(at);
        level.destroyBlock(event.getPos(), false);
        Block.popResource(level, event.getPos(), bean(1));
    }

    static int owned(MinecraftServer server, UUID owner) {
        int n = 0;
        for (ServerLevel l : server.getAllLevels()) n += SenzuSave.get(l).owned(owner);
        return n;
    }

    public static long ripen(SenzuSave.Bush b) {
        return SenzuMath.ripenTicks(ZeroConfig.SERVER.senzuRipenDays.get(), b.korin() ? ZeroConfig.SERVER.korinSpeed.get() : 1.0);
    }

    private static String days(SenzuSave.Bush b) {
        double d = ripen(b) / (double) SenzuMath.DAY;
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format(java.util.Locale.ROOT, "%.1f", d);
    }

    // korin grows the senzu at his tower: dmz's own korin within korinRange when it's planted. one look, at planting
    public static boolean nearKorin(ServerLevel level, BlockPos at) {
        int range = ZeroConfig.SERVER.korinRange.get();
        if (range <= 0) return false;
        Vec3 center = Vec3.atCenterOf(at);
        return !level.getEntities((Entity) null, new AABB(at).inflate(range),
                e -> isKorin(e) && e.distanceToSqr(center) <= (double) range * range).isEmpty();
    }

    public static boolean isKorin(Entity e) {
        return KORIN.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
    }

    private static void tell(ServerPlayer player, String why, String english, Object... args) {
        player.displayClientMessage(Component.translatableWithFallback("message.dmzzero.senzu." + why, english, args), true);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        for (ServerLevel l : event.getServer().getAllLevels()) if (SenzuSave.get(l).size() > 0) GROWING.add(l.dimension());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GROWING.clear();
    }
}
