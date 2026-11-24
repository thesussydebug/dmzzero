package com.dmzzero.animals;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.impact.Standoffs;
import com.dmzzero.scouter.Scouters;
import com.dmzzero.sense.CreaturePower;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.quest.PartyManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// animals feel a raised aura far stronger than they are and run from it: horses rear, wolves whine. your own pets and
// your party's stay put. only players with their aura up are looked around, so nothing runs while no aura is up
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class AnimalFear {
    private static final int COST = TickCost.id("world.animals", () -> AnimalFear.SCARED.size());
    private static final int EVERY = 10;
    // the nearest this many around one aura, so a big farm costs no more than a small one
    private static final int MOST = 32;
    private static final int RUN_BLOCKS = 16;
    private static final int TRIES = 6;
    // a fright lasts this long past the last look, so the next look doesn't rear or whine again
    private static final int REMEMBER = 60;
    private static final Map<UUID, Long> SCARED = new HashMap<>();
    private static int frightened;

    private AnimalFear() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % EVERY != 0) return;
        if (!ZeroConfig.SERVER.animalFear.get()) {
            SCARED.clear();
            return;
        }
        long start = TickCost.start();
        if (!SCARED.isEmpty()) SCARED.values().removeIf(until -> now >= until);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) scare(p, now);
        TickCost.add(COST, start);
    }

    /** One look around a player: the animals that run from their aura. Returns how many were scared. Public for the self-test. */
    public static int scare(ServerPlayer player, long now) {
        if (!ZeroConfig.SERVER.animalFear.get() || !Standoffs.auraUp(player) || Scouters.cloaked(player)) return 0;
        double power = CreaturePower.of(player);
        double radius = ZeroConfig.SERVER.animalFearRadius.get(), ratio = ZeroConfig.SERVER.animalFearRatio.get();
        double speed = ZeroConfig.SERVER.animalFearSpeed.get();
        ServerLevel level = player.serverLevel();
        List<Animal> near = level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(radius, 6, radius),
                a -> candidate(a) && a.distanceToSqr(player) <= radius * radius);
        if (near.isEmpty()) return 0;
        near.sort(Comparator.comparingDouble(a -> a.distanceToSqr(player)));
        int scared = 0;
        for (Animal a : near) {
            if (scared >= MOST) break;
            if (!FearMath.fears(CreaturePower.of(a), power, ratio)) continue;
            boolean fresh = FearMath.fresh(SCARED.get(a.getUUID()), now);
            SCARED.put(a.getUUID(), now + REMEMBER);
            scared++;
            if (friendly(a, player)) {
                if (fresh && a instanceof Wolf) a.playSound(SoundEvents.WOLF_WHINE, 1.0f, 1.0f);
                continue;
            }
            if (fresh) startle(a);
            if (fresh || a.getNavigation().isDone()) run(a, player, speed);
        }
        frightened += scared;
        return scared;
    }

    // left alone: anything ridden, carried, leashed, attacking, without ai, or a monster that only looks like an animal (hoglins)
    private static boolean candidate(Animal a) {
        return a.isAlive() && !a.isNoAi() && !a.isPassenger() && !a.isVehicle() && !a.isLeashed() && a.getTarget() == null && !(a instanceof Enemy);
    }

    // your own pets, and your party's, know you
    static boolean friendly(Animal a, ServerPlayer player) {
        if (!(a instanceof OwnableEntity pet) || pet.getOwnerUUID() == null) return false;
        if (pet.getOwnerUUID().equals(player.getUUID())) return true;
        return pet.getOwner() instanceof Player owner && PartyManager.areInSameParty(player, owner);
    }

    private static void startle(Animal a) {
        if (a instanceof AbstractHorse horse) horse.makeMad();
        else if (a instanceof Wolf) a.playSound(SoundEvents.WOLF_WHINE, 1.0f, 1.2f);
    }

    // vanilla's own way of picking somewhere away; boxed in (a ravine, a pen) the reachable spot farthest from the aura,
    // and only then straight away
    private static void run(Animal a, ServerPlayer player, double speed) {
        Vec3 to = DefaultRandomPos.getPosAway(a, RUN_BLOCKS, 7, player.position());
        if (to == null) to = farthest(a, player.position());
        if (to == null) {
            double[] xz = FearMath.away(player.getX(), player.getZ(), a.getX(), a.getZ(), RUN_BLOCKS);
            to = new Vec3(xz[0], a.getY(), xz[1]);
        }
        a.getNavigation().moveTo(to.x, to.y, to.z, speed);
    }

    // a few spots it can reach, the one farthest from the aura, and only if that's farther than it is now: never toward it
    private static Vec3 farthest(Animal a, Vec3 from) {
        Vec3 best = null;
        double most = a.position().distanceToSqr(from);
        for (int i = 0; i < TRIES; i++) {
            Vec3 p = DefaultRandomPos.getPos(a, RUN_BLOCKS / 2, 7);
            if (p != null && p.distanceToSqr(from) > most) {
                most = p.distanceToSqr(from);
                best = p;
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SCARED.clear();
    }

    /** For the self-test: forget who was scared, so the next look is a fresh fright. */
    public static void forget() {
        SCARED.clear();
    }

    /** Animals scared since the server started, for the status line and the self-test. */
    public static int frightened() {
        return frightened;
    }
}
