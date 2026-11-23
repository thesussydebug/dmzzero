package com.dmzzero.garden;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.TpSource;
import com.dragonminez.common.network.StatsDeltaSync;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// roshi made goku and krillin plow a field with their bare hands. crouch and punch grass or dirt with an empty hand and it
// turns to farmland, the way a hoe tills it (so claim mods can refuse it), paying a little TP for the first blocks each day
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Plowing {
    private static final String ROOT = "dmzzero";
    private static final String DAY = "plowDay";
    private static final String COUNT = "plowCount";

    private Plowing() {
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown() || !p.getMainHandItem().isEmpty()) return;
        if (p.isCreative() || p.isSpectator() || !ZeroConfig.SERVER.plowing.get()) return;
        // a held punch carries on into the new farmland; crouching with a bare hand never breaks it
        if (level.getBlockState(event.getPos()).is(Blocks.FARMLAND)) {
            event.setCanceled(true);
            return;
        }
        if ("plowed".equals(plow(p, level, event.getPos(), event.getFace()))) event.setCanceled(true);
    }

    /** Plows one block by hand. "plowed", or why not: not_soil, blocked. */
    public static String plow(ServerPlayer p, ServerLevel level, BlockPos pos, Direction face) {
        if (face == null || face == Direction.DOWN || !level.getBlockState(pos.above()).isAir()) return "blocked";
        if (!p.mayUseItemAt(pos, face, ItemStack.EMPTY)) return "blocked";
        BlockState state = level.getBlockState(pos);
        // forge's tilling asks whether the item in hand can till; a bare hand counts as a wooden hoe
        UseOnContext ctx = new UseOnContext(level, p, InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_HOE),
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), face, pos, false)) {
        };
        BlockState tilled = state.getToolModifiedState(ctx, ToolActions.HOE_TILL, false);
        if (tilled == null || !tilled.is(Blocks.FARMLAND)) return "not_soil";
        level.setBlock(pos, tilled, Block.UPDATE_ALL_IMMEDIATE);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(p, tilled));
        level.playSound(null, pos, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 0.9f, 0.8f);
        pay(p, level);
        return "plowed";
    }

    private static void pay(ServerPlayer p, ServerLevel level) {
        long day = PlowMath.day(level.getGameTime());
        CompoundTag root = root(p);
        int today = PlowMath.countToday(day, root.getLong(DAY), root.getInt(COUNT));
        boolean pays = PlowMath.pays(today, ZeroConfig.SERVER.plowBlocksPerDay.get());
        save(p, day, today + 1);
        if (!pays) return;
        Integer perHit = ConfigManager.getServerConfig().getGameplay().getTpPerHit();
        int tp = PlowMath.tp(perHit == null ? 0 : perHit, ZeroConfig.SERVER.plowTpShare.get());
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
        if (tp <= 0 || data == null || !data.getStatus().isHasCreatedCharacter()) return;
        double before = data.getResources().getTrainingPointsExact();
        data.getResources().addBoostedTrainingPoints(data.applyTpBoosts(TpSource.TRAINING, tp));
        StatsDeltaSync.markDirty(p);
        int gained = (int) Math.round(data.getResources().getTrainingPointsExact() - before);
        p.displayClientMessage(Component.translatableWithFallback("message.dmzzero.plow", "Plowed by hand. +%s TP", gained)
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static CompoundTag root(Player p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ROOT);
    }

    private static void save(Player p, long day, int count) {
        CompoundTag data = p.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        root.putLong(DAY, day);
        root.putInt(COUNT, count);
        persisted.put(ROOT, root);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Self-test only: how many blocks the player has plowed today. */
    public static void setToday(ServerPlayer p, int count) {
        save(p, PlowMath.day(p.serverLevel().getGameTime()), count);
    }
}
