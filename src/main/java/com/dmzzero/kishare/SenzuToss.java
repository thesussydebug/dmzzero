package com.dmzzero.kishare;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath.V;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.item.consumables.SenzuBeanItem;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// goku tossed cell a senzu. the ki toss chord with a senzu in hand lobs the bean: a real item entity, so players without
// dmz zero see it fly, steered by the server and caught by the player it was thrown to
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class SenzuToss {
    private static final double RANGE_SLACK = 2.0;
    // refreshed every tick in flight, so nobody grabs it on the way and a bean we stop steering is free again soon after
    private static final int HOLD_TICKS = 10;
    private static final int LATE_TICKS = 20;
    private static final List<Flight> FLIGHTS = new ArrayList<>();
    private static final TossMath.Cooldowns COOLDOWNS = new TossMath.Cooldowns();
    private static final int COST = TickCost.id("ki.senzuToss", FLIGHTS::size);
    private static int caught;
    private static String lastCatch = "";

    private static final class Flight {
        final ServerLevel level;
        final ServerPlayer thrower;
        final ServerPlayer target;
        final ItemEntity bean;
        final V from;
        final int ticks;
        final double lift;
        int age;

        Flight(ServerLevel level, ServerPlayer thrower, ServerPlayer target, ItemEntity bean, V from, int ticks, double lift) {
            this.level = level;
            this.thrower = thrower;
            this.target = target;
            this.bean = bean;
            this.from = from;
            this.ticks = ticks;
            this.lift = lift;
        }
    }

    private SenzuToss() {
    }

    public static boolean isBean(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof SenzuBeanItem;
    }

    // what KiToss.request asks before throwing ki
    public static boolean wantsToss(ServerPlayer thrower) {
        return ZeroConfig.SERVER.senzuToss.get() && isBean(thrower.getMainHandItem());
    }

    /** Throws the bean in the main hand. Returns "ok" or why not: disabled, no_bean, no_target, cannot, too_far, cooldown. */
    public static String toss(ServerPlayer thrower, ServerPlayer target) {
        if (!ZeroConfig.SERVER.senzuToss.get()) return "disabled";
        ItemStack hand = thrower.getMainHandItem();
        if (!isBean(hand)) return "no_bean";
        if (target == thrower || !target.isAlive() || target.isSpectator()) return "no_target";
        StatsData data = stats(thrower);
        if (data == null || !KiToss.canThrow(thrower, data)) {
            tell(thrower, "cannot", "You can't throw right now.");
            return "cannot";
        }
        if (thrower.level() != target.level() || thrower.distanceTo(target) > ZeroConfig.SERVER.tossRange.get() + RANGE_SLACK
                || !thrower.hasLineOfSight(target)) {
            thrower.displayClientMessage(Component.translatableWithFallback("message.dmzzero.toss.too_far", "%s is too far to catch it.",
                    target.getGameProfile().getName()).withStyle(ChatFormatting.GRAY), true);
            return "too_far";
        }
        long now = thrower.server.getTickCount();
        long wait = COOLDOWNS.remaining(thrower.getUUID(), now);
        if (wait > 0) {
            tell(thrower, "cooldown", "Senzu toss ready in %ss.", (wait + 19) / 20);
            return "cooldown";
        }
        COOLDOWNS.start(thrower.getUUID(), now, ZeroConfig.SERVER.senzuTossCooldown.get() * 20L);

        ItemStack one = hand.copyWithCount(1);
        if (!thrower.getAbilities().instabuild) hand.shrink(1);
        ServerLevel level = thrower.serverLevel();
        Vec3 look = thrower.getViewVector(1f);
        Vec3 from = thrower.getEyePosition().add(look.scale(0.6)).add(0, -0.35, 0);
        ItemEntity bean = new ItemEntity(level, from.x, from.y, from.z, one, 0, 0, 0);
        bean.setPickUpDelay(HOLD_TICKS);
        level.addFreshEntity(bean);
        double distance = from.distanceTo(aim(target));
        FLIGHTS.add(new Flight(level, thrower, target, bean, v(from), BeanTossMath.flightTicks(distance), BeanTossMath.lift(distance)));
        thrower.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 0.7f);
        return "ok";
    }

    // set before the beans tick, so each moves itself along the arc and its motion reaches every client
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || FLIGHTS.isEmpty()) return;
        long start = TickCost.start();
        step();
        TickCost.add(COST, start);
    }

    public static void step() {
        for (Iterator<Flight> it = FLIGHTS.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            f.age++;
            // a hopper or a merge with another dropped senzu took it; the bean isn't lost
            if (f.bean.isRemoved()) {
                it.remove();
                continue;
            }
            if (f.target.isRemoved() || !f.target.isAlive() || f.target.isSpectator() || f.target.level() != f.level) {
                drop(f);
                it.remove();
                continue;
            }
            Vec3 aim = aim(f.target);
            if (f.bean.position().distanceTo(aim) <= BeanTossMath.CATCH) {
                land(f);
                it.remove();
                continue;
            }
            if (f.age > f.ticks + LATE_TICKS) {
                drop(f);
                it.remove();
                continue;
            }
            V next = BeanTossMath.at(f.from, v(aim), (double) f.age / f.ticks, f.lift);
            Vec3 move = new Vec3(next.x(), next.y(), next.z()).subtract(f.bean.position());
            if (!f.level.noCollision(f.bean, f.bean.getBoundingBox().move(move))) {
                drop(f);
                it.remove();
                continue;
            }
            // item entities fall 0.04 before they move. vanilla sends an item's position only every 20 ticks, and clients
            // drift off the arc in between; an impulse sends position and motion this tick
            f.bean.setDeltaMovement(move.x, move.y + 0.04, move.z);
            f.bean.hasImpulse = true;
            f.bean.setPickUpDelay(HOLD_TICKS);
        }
    }

    private static void land(Flight f) {
        ServerPlayer target = f.target;
        ItemStack bean = f.bean.getItem().copy();
        f.bean.discard();
        String from = f.thrower.getGameProfile().getName();
        if (ZeroConfig.SERVER.senzuEatOnCatch.get() && needs(target, bean)) {
            // said first, so the senzu's own "won't be hungry" line is the one left showing
            target.displayClientMessage(Component.translatableWithFallback("message.dmzzero.senzu_toss.ate",
                    "%s tossed you a senzu bean.", from).withStyle(ChatFormatting.GREEN), true);
            eat(target, bean);
            lastCatch = "ate";
        } else {
            ItemHandlerHelper.giveItemToPlayer(target, bean);
            lastCatch = "kept";
            target.displayClientMessage(Component.translatableWithFallback("message.dmzzero.senzu_toss.caught",
                    "Caught a senzu bean from %s.", from).withStyle(ChatFormatting.GREEN), true);
        }
        if (!f.thrower.isRemoved()) f.thrower.displayClientMessage(Component.translatableWithFallback("message.dmzzero.toss.landed",
                "%s caught it.", target.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);
        caught++;
    }

    // dmz won't start a senzu that's cooling down or let a stunned player eat; then it's kept for later
    static boolean needs(ServerPlayer p, ItemStack bean) {
        if (!(bean.getItem() instanceof SenzuBeanItem item)) return false;
        if (p.getCooldowns().isOnCooldown(item) || p.hasEffect(MainEffects.STUN.get())) return false;
        StatsData d = stats(p);
        SenzuBeanItem.SenzuType type = item.getSenzuType();
        float ki = d == null ? 0 : d.getResources().getCurrentEnergy(), kiMax = d == null ? 0 : d.getMaxEnergy();
        float st = d == null ? 0 : d.getResources().getCurrentStamina(), stMax = d == null ? 0 : d.getMaxStamina();
        return BeanTossMath.needs(type.restoresHealth(), type.restoresKi(), type.restoresStamina(), p.getHealth(), p.getMaxHealth(),
                ki, kiMax, st, stMax);
    }

    // eaten the way a held senzu is: vanilla's food, then forge's finish event, where dmz restores health, ki and stamina
    private static void eat(ServerPlayer p, ItemStack bean) {
        ItemStack copy = bean.copy();
        ForgeEventFactory.onItemUseFinish(p, copy, 0, bean.finishUsingItem(p.level(), p));
    }

    private static void drop(Flight f) {
        f.bean.setPickUpDelay(HOLD_TICKS);
        f.bean.setDeltaMovement(f.bean.getDeltaMovement().scale(0.3));
        f.bean.hasImpulse = true;
    }

    private static Vec3 aim(ServerPlayer target) {
        return target.position().add(0, target.getBbHeight() * 0.6, 0);
    }

    private static V v(Vec3 p) {
        return new V(p.x, p.y, p.z);
    }

    private static StatsData stats(ServerPlayer player) {
        return StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
    }

    private static void tell(ServerPlayer player, String why, String english, Object... args) {
        player.displayClientMessage(Component.translatableWithFallback("message.dmzzero.senzu_toss." + why, english, args)
                .withStyle(ChatFormatting.GRAY), true);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FLIGHTS.clear();
        COOLDOWNS.clear();
    }

    public static void resetCooldown(ServerPlayer player) {
        COOLDOWNS.start(player.getUUID(), 0, 0);
    }

    /** The beans in the air, for the self-test to tick. */
    public static List<ItemEntity> inFlight() {
        List<ItemEntity> beans = new ArrayList<>();
        for (Flight f : FLIGHTS) beans.add(f.bean);
        return beans;
    }

    public static int caught() {
        return caught;
    }

    public static String lastCatch() {
        return lastCatch;
    }
}
