package com.dmzzero.waterfall;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.flight.Touchdowns;
import com.dmzzero.garden.PlowMath;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.TpSource;
import com.dragonminez.common.network.StatsDeltaSync;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// goku, gohan and piccolo trained standing under waterfalls. stand still on the ground with falling water from your head
// up, and every few seconds it pays a little TP, up to a count each in-game day
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class WaterfallTraining {
    private static final String ROOT = "dmzzero";
    private static final String DAY = "waterfallDay";
    private static final String COUNT = "waterfallCount";
    private static final int BRACE_SECONDS = 3;
    private static final int COST = TickCost.id("world.waterfall", () -> WaterfallTraining.UNDER.size());
    private static final Map<UUID, Stint> UNDER = new HashMap<>();

    private static final class Stint {
        double x;
        double z;
        int seconds;
    }

    private WaterfallTraining() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        // once a second, on each player's own count, so players don't all land on the same tick
        if (p.tickCount % 20 != 0 || !ZeroConfig.SERVER.waterfall.get()) return;
        long start = TickCost.start();
        second(p);
        TickCost.add(COST, start);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UNDER.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        UNDER.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        UNDER.clear();
    }

    /** One second of a player's training; the seconds they've now stood still under the falls. The self-test calls it by hand. */
    public static int second(ServerPlayer p) {
        UUID id = p.getUUID();
        if (!stands(p) || !underFalls(p.serverLevel(), p.blockPosition(), ZeroConfig.SERVER.waterfallHeight.get())) {
            UNDER.remove(id);
            return 0;
        }
        // the falls pour over your head, but you breathe while you train under them
        p.setAirSupply(p.getMaxAirSupply());
        Stint s = UNDER.get(id);
        if (s == null) {
            s = new Stint();
            s.x = p.getX();
            s.z = p.getZ();
            UNDER.put(id, s);
        }
        boolean still = WaterfallMath.still(p.getX() - s.x, p.getZ() - s.z, WaterfallMath.DRIFT);
        s.x = p.getX();
        s.z = p.getZ();
        s.seconds = WaterfallMath.next(s.seconds, true, still);
        int every = ZeroConfig.SERVER.waterfallSeconds.get();
        if (s.seconds == BRACE_SECONDS && every > BRACE_SECONDS) {
            p.displayClientMessage(Component.translatableWithFallback("message.dmzzero.waterfall.brace", "You brace yourself under the falls.")
                    .withStyle(ChatFormatting.AQUA), true);
        }
        if (WaterfallMath.due(s.seconds, every)) pay(p, s.seconds == every);
        return s.seconds;
    }

    private static boolean stands(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative() && !p.isPassenger() && !p.getAbilities().flying && Touchdowns.grounded(p);
    }

    /** Falling water in every block from the head up, this many blocks high. Source water, a pool or a waterlogged block, isn't a waterfall. */
    public static boolean underFalls(ServerLevel level, BlockPos feet, int height) {
        BlockPos.MutableBlockPos pos = feet.mutable();
        for (int i = 0; i < height; i++) {
            pos.move(0, 1, 0);
            if (!level.isLoaded(pos) || !falling(level.getFluidState(pos))) return false;
        }
        return true;
    }

    private static boolean falling(FluidState fluid) {
        return fluid.is(FluidTags.WATER) && !fluid.isSource() && fluid.getValue(FlowingFluid.FALLING);
    }

    private static void pay(ServerPlayer p, boolean firstOfStint) {
        long day = PlowMath.day(p.serverLevel().getGameTime());
        CompoundTag root = root(p);
        int today = PlowMath.countToday(day, root.getLong(DAY), root.getInt(COUNT));
        int cap = ZeroConfig.SERVER.waterfallPaysPerDay.get();
        if (!PlowMath.pays(today, cap)) {
            if (firstOfStint) {
                p.displayClientMessage(Component.translatableWithFallback("message.dmzzero.waterfall.done",
                        "The waterfall has taught you all it can today.").withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        Integer perHit = ConfigManager.getServerConfig().getGameplay().getTpPerHit();
        int tp = PlowMath.tp(perHit == null ? 0 : perHit, ZeroConfig.SERVER.waterfallTpShare.get());
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
        if (tp <= 0 || data == null || !data.getStatus().isHasCreatedCharacter()) return;
        save(p, day, today + 1);
        double before = data.getResources().getTrainingPointsExact();
        data.getResources().addBoostedTrainingPoints(data.applyTpBoosts(TpSource.TRAINING, tp));
        StatsDeltaSync.markDirty(p);
        int gained = (int) Math.round(data.getResources().getTrainingPointsExact() - before);
        MutableComponent line = today + 1 >= cap
                ? Component.translatableWithFallback("message.dmzzero.waterfall.last", "Training under the waterfall. +%s TP, and that's all for today.", gained)
                : Component.translatableWithFallback("message.dmzzero.waterfall.pay", "Training under the waterfall. +%s TP", gained);
        p.displayClientMessage(line.withStyle(ChatFormatting.GOLD), true);
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

    /** Self-test only: how many payments the player has had today. */
    public static void setToday(ServerPlayer p, int count) {
        save(p, PlowMath.day(p.serverLevel().getGameTime()), count);
    }

    public static void forget(UUID id) {
        UNDER.remove(id);
    }
}
