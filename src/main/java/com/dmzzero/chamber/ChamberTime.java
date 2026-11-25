package com.dmzzero.chamber;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.status.TickCost;
import com.dragonminez.server.world.dimension.HTCDimension;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// each character gets two in-game days in the Time Chamber, as in the show; a wish to the dragon adds more.
// the time is counted while inside and kept with the player; out of time, the door won't open and you're sent back out
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class ChamberTime {
    private static final int COST = TickCost.id("world.timeChamber");
    static final String ROOT = "dmzzero";
    static final String USED = "chamberTicks";
    static final String WISHED = "chamberWishedDays";
    static final String BACK = "chamberReturn";
    private static final int STEP = 20;
    private static int sentOut;
    private static int refused;

    private ChamberTime() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        long start = TickCost.start();
        playerTick(event);
        TickCost.add(COST, start);
    }

    private static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (p.tickCount % STEP != 0 || !ChamberDepths.inChamber(p)) return;
        step(p, STEP);
    }

    // creative and spectating players come and go freely
    static boolean counts(ServerPlayer p) {
        return !p.isCreative() && !p.isSpectator();
    }

    /** Counts ticks spent inside, warns as the marks pass, and sends the player out once the time is up. */
    public static void step(ServerPlayer p, int ticks) {
        long limit = limit(p);
        if (limit == TimeLimit.NO_LIMIT || !counts(p)) return;
        CompoundTag persisted = persisted(p);
        CompoundTag root = persisted.getCompound(ROOT);
        long before = TimeLimit.left(limit, root.getLong(USED));
        long used = Math.max(0, root.getLong(USED)) + ticks;
        root.putLong(USED, used);
        persisted.put(ROOT, root);
        save(p, persisted);
        long after = TimeLimit.left(limit, used);
        if (after <= 0) {
            sendOut(p);
            return;
        }
        if (TimeLimit.warning(before, after) > 0) {
            p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.chamber.left", "%s left in the Time Chamber.", time(after))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    public static long limit(Player p) {
        return TimeLimit.limit(ZeroConfig.SERVER.chamberLimitDays.get(), persisted(p).getCompound(ROOT).getInt(WISHED));
    }

    public static long used(CompoundTag persisted) {
        return Math.max(0, persisted.getCompound(ROOT).getLong(USED));
    }

    public static int wished(CompoundTag persisted) {
        return Math.max(0, persisted.getCompound(ROOT).getInt(WISHED));
    }

    public static long left(Player p) {
        return TimeLimit.left(limit(p), used(persisted(p)));
    }

    // dmz's portal goes through forge's dimension change: refuse the way in when the time is up, and note where the player came from
    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !event.getDimension().equals(HTCDimension.HTC_KEY) || ChamberDepths.inChamber(p)) return;
        if (counts(p) && TimeLimit.out(limit(p), used(persisted(p)))) {
            event.setCanceled(true);
            refused++;
            p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.chamber.refused",
                    "The Time Chamber's door won't open for you anymore. A wish to the dragon could change that.").withStyle(ChatFormatting.RED));
            return;
        }
        CompoundTag persisted = persisted(p);
        CompoundTag root = persisted.getCompound(ROOT);
        CompoundTag back = new CompoundTag();
        back.putString("dim", p.level().dimension().location().toString());
        back.putDouble("x", p.getX());
        back.putDouble("y", p.getY());
        back.putDouble("z", p.getZ());
        root.put(BACK, back);
        persisted.put(ROOT, root);
        save(p, persisted);
    }

    @SubscribeEvent
    public static void onEnter(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !event.getTo().equals(HTCDimension.HTC_KEY) || !counts(p)) return;
        long left = left(p);
        if (left == TimeLimit.NO_LIMIT) return;
        p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.chamber.enter", "You have %s in the Time Chamber.", time(left))
                .withStyle(ChatFormatting.AQUA));
    }

    static void sendOut(ServerPlayer p) {
        CompoundTag back = persisted(p).getCompound(ROOT).getCompound(BACK);
        ServerLevel level = null;
        ResourceLocation id = ResourceLocation.tryParse(back.getString("dim"));
        if (id != null) level = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        double x, y, z;
        if (level != null && !level.dimension().equals(HTCDimension.HTC_KEY)) {
            x = back.getDouble("x");
            y = back.getDouble("y");
            z = back.getDouble("z");
        } else {
            level = p.server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
            x = top.getX() + 0.5;
            y = top.getY();
            z = top.getZ() + 0.5;
        }
        p.teleportTo(level, x, y, z, p.getYRot(), p.getXRot());
        sentOut++;
        p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.chamber.up",
                "Your time in the Time Chamber is up, and the door closes behind you. A wish to the dragon could open it again.")
                .withStyle(ChatFormatting.GOLD));
        DMZZero.LOG.info("{} used up their time in the Time Chamber", p.getGameProfile().getName());
    }

    /** The wish: more days for whoever made it. */
    public static int grant(ServerPlayer p) {
        int days = ZeroConfig.SERVER.chamberWishDays.get();
        CompoundTag persisted = persisted(p);
        CompoundTag root = persisted.getCompound(ROOT);
        root.putInt(WISHED, Math.max(0, root.getInt(WISHED)) + days);
        persisted.put(ROOT, root);
        save(p, persisted);
        p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.chamber.wished",
                "The Time Chamber's door will open for you for another %s.", time(days * TimeLimit.DAY)).withStyle(ChatFormatting.GOLD));
        return days;
    }

    public static void reset(Player p) {
        CompoundTag persisted = persisted(p);
        CompoundTag root = persisted.getCompound(ROOT);
        root.remove(USED);
        persisted.put(ROOT, root);
        save(p, persisted);
    }

    /** In-game days and hours, as the show counts Time Chamber time. */
    public static Component time(long ticks) {
        long[] dh = TimeLimit.daysHours(ticks);
        Component days = dh[0] == 1 ? Component.translatableWithFallback("time.dmzzero.day", "1 day")
                : Component.translatableWithFallback("time.dmzzero.days", "%s days", dh[0]);
        Component hours = dh[1] == 1 ? Component.translatableWithFallback("time.dmzzero.hour", "1 hour")
                : Component.translatableWithFallback("time.dmzzero.hours", "%s hours", dh[1]);
        if (dh[0] > 0 && dh[1] > 0) return Component.translatableWithFallback("time.dmzzero.days_hours", "%s %s", days, hours);
        if (dh[0] > 0) return days;
        if (dh[1] > 0) return hours;
        return Component.translatableWithFallback("time.dmzzero.under_hour", "under an hour");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("dmzzero").then(Commands.literal("chamber")
                .executes(c -> show(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.literal("wish").requires(s -> s.hasPermission(2))
                        .executes(c -> grant(c.getSource().getPlayerOrException())))
                .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                        .executes(c -> show(c.getSource(), EntityArgument.getPlayer(c, "player")))
                        .then(Commands.literal("reset").executes(c -> {
                            ServerPlayer target = EntityArgument.getPlayer(c, "player");
                            reset(target);
                            c.getSource().sendSuccess(() -> Component.translatableWithFallback("message.dmzzero.chamber.reset",
                                    "%s's Time Chamber time has been reset.", target.getGameProfile().getName()), true);
                            return 1;
                        }))
                        .then(Commands.literal("add").then(Commands.argument("days", IntegerArgumentType.integer(1, 1000)).executes(c -> {
                            ServerPlayer target = EntityArgument.getPlayer(c, "player");
                            int days = IntegerArgumentType.getInteger(c, "days");
                            CompoundTag persisted = persisted(target);
                            CompoundTag root = persisted.getCompound(ROOT);
                            root.putInt(WISHED, Math.max(0, root.getInt(WISHED)) + days);
                            persisted.put(ROOT, root);
                            save(target, persisted);
                            c.getSource().sendSuccess(() -> Component.translatableWithFallback("message.dmzzero.chamber.added",
                                    "Gave %s another %s in the Time Chamber.", target.getGameProfile().getName(), time(days * TimeLimit.DAY)), true);
                            return days;
                        }))))));
    }

    static int show(CommandSourceStack source, ServerPlayer p) {
        long limit = limit(p);
        String name = p.getGameProfile().getName();
        if (limit == TimeLimit.NO_LIMIT) {
            source.sendSuccess(() -> Component.translatableWithFallback("message.dmzzero.chamber.unlimited",
                    "%s: no limit on time in the Time Chamber.", name), false);
            return 0;
        }
        long left = TimeLimit.left(limit, used(persisted(p)));
        int wished = wished(persisted(p));
        source.sendSuccess(() -> Component.translatableWithFallback("message.dmzzero.chamber.status",
                "%s: %s left in the Time Chamber, of %s (%s from wishes).", name, time(left), time(limit), wished > 0
                        ? time(wished * TimeLimit.DAY)
                        : Component.translatableWithFallback("message.dmzzero.chamber.no_wishes", "none")), false);
        return (int) Math.min(Integer.MAX_VALUE, left / 20);
    }

    static CompoundTag persisted(Player p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    static void save(Player p, CompoundTag persisted) {
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static int sentOut() {
        return sentOut;
    }

    public static int refused() {
        return refused;
    }

    public static ResourceKey<Level> chamber() {
        return HTCDimension.HTC_KEY;
    }
}
