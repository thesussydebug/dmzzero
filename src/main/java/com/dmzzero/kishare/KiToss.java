package com.dmzzero.kishare;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.sense.KnownKi;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.server.events.players.KiShareService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// a ball of ki thrown to another player: flown and drawn by the server, so there's no entity to sync
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class KiToss {
    private static final int COST = TickCost.id("ki.toss", () -> KiToss.FLIGHTS.size());
    private static final double RANGE_SLACK = 2.0;
    private static final List<Flight> FLIGHTS = new ArrayList<>();
    private static final TossMath.Cooldowns COOLDOWNS = new TossMath.Cooldowns();
    private static int thrown;
    private static int caught;
    private static String lastCatch = "";
    private static int requests;
    private static String lastRequest = "";

    private static final class Flight {
        final ServerLevel level;
        final ServerPlayer thrower;
        final ServerPlayer target;
        final TossMath.Orb orb;
        final float amount;
        final DustParticleOptions dust;
        int age;

        Flight(ServerLevel level, ServerPlayer thrower, ServerPlayer target, TossMath.Orb orb, float amount, int color) {
            this.level = level;
            this.thrower = thrower;
            this.target = target;
            this.orb = orb;
            this.amount = amount;
            float[] rgb = StreamMath.rgb(color);
            this.dust = new DustParticleOptions(new Vector3f(rgb[0], rgb[1], rgb[2]), 1.8f);
        }
    }

    private KiToss() {
    }

    public static String request(ServerPlayer thrower, int targetId) {
        requests++;
        Entity e = thrower.level().getEntity(targetId);
        lastRequest = e instanceof ServerPlayer target ? route(thrower, target) : "no_target";
        return lastRequest;
    }

    // a senzu in hand is thrown instead of ki
    public static String route(ServerPlayer thrower, ServerPlayer target) {
        if (target == thrower) return "no_target";
        return SenzuToss.wantsToss(thrower) ? SenzuToss.toss(thrower, target) : toss(thrower, target);
    }

    public static String toss(ServerPlayer thrower, ServerPlayer target) {
        if (!ZeroConfig.SERVER.toss.get()) return "disabled";
        StatsData data = stats(thrower);
        StatsData targetData = stats(target);
        if (data == null || targetData == null || !targetData.getStatus().isHasCreatedCharacter() || !target.isAlive() || target.isSpectator()) return "no_target";
        if (!canThrow(thrower, data)) {
            thrower.displayClientMessage(Component.translatableWithFallback("message.dmzzero.toss.cannot", "You can't throw ki right now.")
                    .withStyle(ChatFormatting.GRAY), true);
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
            thrower.displayClientMessage(Component.translatableWithFallback("message.dmzzero.toss.cooldown", "Ki toss ready in %ss.",
                    (wait + 19) / 20).withStyle(ChatFormatting.GRAY), true);
            return "cooldown";
        }
        float current = data.getResources().getCurrentEnergy();
        float amount = TossMath.amount(data.getMaxEnergy(), current, ZeroConfig.SERVER.tossShare.get());
        if (amount <= 0) {
            thrower.displayClientMessage(Component.translatableWithFallback("message.dmzzero.toss.no_ki", "No ki left to throw.")
                    .withStyle(ChatFormatting.GRAY), true);
            return "no_ki";
        }
        if (!thrower.isCreative()) data.getResources().setCurrentEnergy(current - amount);
        sync(thrower);
        COOLDOWNS.start(thrower.getUUID(), now, ZeroConfig.SERVER.tossCooldown.get() * 20L);

        Vec3 look = thrower.getViewVector(1f);
        Vec3 hand = thrower.getEyePosition().add(look.scale(0.8)).add(0, -0.3, 0);
        TossMath.Orb orb = new TossMath.Orb(hand.x, hand.y, hand.z, look.x * 0.6, look.y * 0.6 + 0.15, look.z * 0.6);
        int color = StreamMath.hex(data.getCharacter().getActiveAuraColor(), 0x7FD4FF);
        FLIGHTS.add(new Flight(thrower.serverLevel(), thrower, target, orb, amount, color));
        thrower.serverLevel().playSound(null, hand.x, hand.y, hand.z, MainSounds.KIBLAST_ATTACK.get(), SoundSource.PLAYERS, 0.5f, 1.5f);
        thrown++;
        return "ok";
    }

    // the same things that stop dmz's ki sharing
    static boolean canThrow(ServerPlayer player, StatsData data) {
        if (!player.isAlive() || player.isSpectator() || !data.getStatus().isHasCreatedCharacter()) return false;
        if (player.hasEffect(MainEffects.STUN.get()) || KiShareService.isDonating(player)) return false;
        var s = data.getStatus();
        return !s.isKnockedDown() && !s.isStrikeLocked() && !s.isMatchFrozen() && !s.isInGodRitual();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FLIGHTS.isEmpty()) return;
        tick();
    }

    public static void tick() {
        for (Iterator<Flight> it = FLIGHTS.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            f.age++;
            if (f.target.isRemoved() || !f.target.isAlive() || f.target.level() != f.level) {
                fizzle(f);
                it.remove();
                continue;
            }
            Vec3 aim = f.target.position().add(0, f.target.getBbHeight() * 0.6, 0);
            f.orb.step(aim.x, aim.y, aim.z, TossMath.SPEED);
            f.level.sendParticles(f.dust, f.orb.x, f.orb.y, f.orb.z, 5, 0.07, 0.07, 0.07, 0);
            if (f.age % 2 == 0) f.level.sendParticles(ParticleTypes.END_ROD, f.orb.x, f.orb.y, f.orb.z, 1, 0.02, 0.02, 0.02, 0.01);
            if (TossMath.caught(f.orb.distanceTo(aim.x, aim.y, aim.z), TossMath.CATCH)) {
                land(f);
                it.remove();
            } else if (f.age >= TossMath.LIFE) {
                fizzle(f);
                it.remove();
            }
        }
    }

    private static void land(Flight f) {
        ServerPlayer target = f.target;
        StatsData data = stats(target);
        if (data == null) {
            fizzle(f);
            return;
        }
        float max = data.getMaxEnergy();
        String kind;
        KiBlastEntity bomb = KiShareService.chargingGenki(target);
        if (bomb != null && bomb.absorbGenkiDonation(f.amount / Math.max(1f, max))) {
            kind = "genki";
        } else {
            kind = Transfusion.burst(target, data, f.thrower) ? "transfusion" : "energy";
            float current = data.getResources().getCurrentEnergy();
            data.getResources().setCurrentEnergy(Math.min(max, current + f.amount));
            sync(target);
        }
        if (ZeroConfig.SERVER.teachKi.get()) {
            KnownKi.learn(target, f.thrower);
            KnownKi.learn(f.thrower, target);
        }
        f.level.sendParticles(f.dust, f.orb.x, f.orb.y, f.orb.z, 24, 0.35, 0.35, 0.35, 0.02);
        f.level.sendParticles(ParticleTypes.END_ROD, f.orb.x, f.orb.y, f.orb.z, 8, 0.2, 0.2, 0.2, 0.06);
        f.level.playSound(null, target.getX(), target.getY(), target.getZ(), MainSounds.ABSORB1.get(), SoundSource.PLAYERS, 0.6f, 1.4f);
        int shown = Math.round(f.amount);
        target.displayClientMessage(Component.translatableWithFallback("message.dmzzero.toss.caught", "Caught %s's ki (+%s)",
                f.thrower.getGameProfile().getName(), shown).withStyle(ChatFormatting.AQUA), true);
        f.thrower.displayClientMessage(Component.translatableWithFallback("message.dmzzero.toss.landed", "%s caught it.",
                target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA), true);
        caught++;
        lastCatch = kind;
    }

    private static void fizzle(Flight f) {
        f.level.sendParticles(ParticleTypes.POOF, f.orb.x, f.orb.y, f.orb.z, 6, 0.1, 0.1, 0.1, 0.02);
        f.level.playSound(null, f.orb.x, f.orb.y, f.orb.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3f, 1.8f);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FLIGHTS.clear();
        COOLDOWNS.clear();
    }

    private static StatsData stats(ServerPlayer player) {
        return StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
    }

    private static void sync(ServerPlayer player) {
        if (player.connection != null) NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
    }

    public static void resetCooldown(ServerPlayer player) {
        COOLDOWNS.start(player.getUUID(), 0, 0);
    }

    public static int inFlight() {
        return FLIGHTS.size();
    }

    public static int thrown() {
        return thrown;
    }

    public static int caught() {
        return caught;
    }

    public static int requests() {
        return requests;
    }

    public static String lastRequest() {
        return lastRequest;
    }

    public static String lastCatch() {
        return lastCatch;
    }
}
