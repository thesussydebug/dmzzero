package com.dmzzero.transmission;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.duel.Duels;
import com.dmzzero.magic.SpellMath;
import com.dmzzero.realm.Battles;
import com.dmzzero.realm.NullRealm;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.util.ITTeleportHelper;
import com.dragonminez.common.network.StatsDeltaSync;
import com.dragonminez.server.world.dimension.OtherworldDimension;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// a hand on the shoulder: party members crouching right next to someone using dmz's instant transmission go along.
// a friend riding on their back already comes along within a dimension; across one, vanilla throws them off, so they're seated again.
// the mixins on dmz's two travel packets call depart just before dmz's departure effect and arrive just before its arrival one
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Shoulders {
    // who holds on to each traveller, from departure to arrival inside one packet handler
    private static final Map<UUID, Trip> PENDING = new HashMap<>();
    // self-test only: fake players aren't level entities, so they're handed in here
    private static List<ServerPlayer> testNear;
    private static int brought;
    private static float lastCharged;

    private Shoulders() {
    }

    // riders: whoever rides on this one, seated again after a change of dimension
    private record Hold(ServerPlayer who, double dx, double dy, double dz, List<ServerPlayer> riders) {
    }

    // each: the ki paid for every one of them, given back for anyone who can't arrive. riders: on the traveller's own back
    private record Trip(List<Hold> holds, float each, List<ServerPlayer> riders) {
    }

    /** The traveller is about to leave: picks who holds on and charges the traveller for them. */
    public static void depart(ServerPlayer traveller) {
        PENDING.remove(traveller.getUUID());
        lastCharged = 0;
        List<ServerPlayer> riders = ridersOf(traveller);
        if (!ZeroConfig.SERVER.shoulder.get()) {
            if (!riders.isEmpty()) PENDING.put(traveller.getUUID(), new Trip(List.of(), 0, riders));
            return;
        }
        double reach = ZeroConfig.SERVER.shoulderReach.get();
        List<ServerPlayer> near = new ArrayList<>(traveller.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                traveller.getBoundingBox().inflate(reach + 1), p -> p != traveller));
        List<ServerPlayer> test = testNear;
        if (test != null) for (ServerPlayer p : test) if (!near.contains(p)) near.add(p);
        List<ShoulderMath.Near> seen = new ArrayList<>();
        for (int i = 0; i < near.size(); i++) {
            ServerPlayer p = near.get(i);
            boolean party = !ZeroConfig.SERVER.shoulderPartyOnly.get() || PartyManager.areInSameParty(traveller, p);
            boolean crouching = ShoulderMath.holdsOn(p.isShiftKeyDown(), p.getAbilities().flying, p.isFallFlying());
            seen.add(new ShoulderMath.Near(i, traveller.distanceTo(p), crouching, party, canGo(traveller, p)));
        }
        List<Integer> picked = ShoulderMath.pick(seen, reach, ZeroConfig.SERVER.shoulderMost.get());
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, traveller).orElse(null);
        float each = data == null ? 0 : SpellMath.cost(data.getMaxEnergy(), ZeroConfig.SERVER.shoulderKiCost.get());
        int going = data == null ? 0 : traveller.isCreative() ? picked.size()
                : ShoulderMath.affordable(picked.size(), data.getResources().getCurrentEnergy(), each);
        if (going < picked.size()) tell(traveller, "no_ki", "Not enough ki to take everyone along.", false);
        if (going <= 0) {
            if (!riders.isEmpty()) PENDING.put(traveller.getUUID(), new Trip(List.of(), 0, riders));
            return;
        }
        if (!traveller.isCreative() && each > 0) {
            data.getResources().removeEnergy(each * going);
            lastCharged = each * going;
            StatsDeltaSync.markDirty(traveller);
        }
        List<Hold> holds = new ArrayList<>();
        for (int i = 0; i < going; i++) {
            ServerPlayer p = near.get(picked.get(i));
            holds.add(new Hold(p, p.getX() - traveller.getX(), p.getY() - traveller.getY(), p.getZ() - traveller.getZ(), ridersOf(p)));
            ITTeleportHelper.departVfx(p, MainSounds.TP.get());
        }
        PENDING.put(traveller.getUUID(), new Trip(holds, traveller.isCreative() ? 0 : each, riders));
    }

    /** The traveller has arrived: brings those holding on to land beside them. */
    public static void arrive(ServerPlayer traveller) {
        Trip trip = PENDING.remove(traveller.getUUID());
        if (trip == null) return;
        ServerLevel to = traveller.serverLevel();
        double reach = ZeroConfig.SERVER.shoulderReach.get();
        int came = 0;
        ServerPlayer last = null;
        for (Hold h : trip.holds()) {
            ServerPlayer p = h.who();
            if (p.isRemoved() || !p.isAlive()) continue;
            // dmz would only send them back to its Otherworld
            if (keptInOtherworld(p, to)) {
                refund(traveller, trip.each());
                tell(p, "kept", "The dead can't leave the Otherworld that way.", false);
                continue;
            }
            Vec3 at = traveller.position();
            ShoulderMath.Spot s = ShoulderMath.beside(h.dx(), h.dy(), h.dz(), reach);
            Vec3 spot = at.add(s.x(), 0, s.z());
            if (free(to, p, spot)) at = spot;
            move(p, to, at, traveller.getYRot());
            reseat(h.riders(), p, to);
            tell(p, "brought", "%s took you along.", true, traveller.getDisplayName());
            last = p;
            came++;
            brought++;
        }
        reseat(trip.riders(), traveller, to);
        if (came == 1) tell(traveller, "took_one", "You took %s along.", true, last.getDisplayName());
        else if (came > 1) tell(traveller, "took", "You took %s others along.", true, came);
    }

    private static List<ServerPlayer> ridersOf(ServerPlayer carrier) {
        return carrier.getPassengers().stream().filter(e -> e instanceof ServerPlayer).map(e -> (ServerPlayer) e).toList();
    }

    // a change of dimension throws riders off, so they follow their carrier and get back on
    private static void reseat(List<ServerPlayer> riders, ServerPlayer carrier, ServerLevel to) {
        for (ServerPlayer r : riders) {
            if (r.isRemoved() || !r.isAlive() || r.getVehicle() == carrier) continue;
            if (keptInOtherworld(r, to)) {
                tell(r, "kept", "The dead can't leave the Otherworld that way.", false);
                continue;
            }
            move(r, to, carrier.position(), carrier.getYRot());
            seat(r, carrier);
            tell(r, "brought", "%s took you along.", true, carrier.getDisplayName());
        }
    }

    private static boolean keptInOtherworld(ServerPlayer p, ServerLevel to) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
        if (data == null || p.level() == to) return false;
        Boolean on = ConfigManager.getServerConfig().getWorldGen().getOtherworldActive();
        return ShoulderMath.keptInOtherworld(on != null && on, data.getStatus().isAlive(), data.getStatus().getTempReturnTimer(),
                p.isCreative() || p.isSpectator(), to.dimension() == OtherworldDimension.OTHERWORLD_KEY);
    }

    private static void refund(ServerPlayer traveller, float each) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, traveller).orElse(null);
        if (data == null || each <= 0) return;
        data.getResources().addEnergy(each);
        lastCharged = Math.max(0, lastCharged - each);
        StatsDeltaSync.markDirty(traveller);
    }

    private static void move(ServerPlayer p, ServerLevel to, Vec3 at, float yaw) {
        if (p.isPassenger()) p.stopRiding();
        // a fake player's connection ignores teleports
        if (p instanceof FakePlayer) p.moveTo(at.x, at.y, at.z, yaw, p.getXRot());
        else p.teleportTo(to, at.x, at.y, at.z, yaw, p.getXRot());
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
        ITTeleportHelper.arriveVfx(p, MainSounds.TP.get());
    }

    private static void seat(ServerPlayer rider, ServerPlayer carrier) {
        if (!rider.startRiding(carrier, true)) return;
        // vanilla tells everyone watching the carrier, but not the carrier themself
        if (carrier.connection != null) carrier.connection.send(new ClientboundSetPassengersPacket(carrier));
    }

    // who may hold on: alive, standing on their own, and in no duel, battle or Null Realm
    private static boolean canGo(ServerPlayer traveller, ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || p.isSleeping() || p.isPassenger() || p.level() != traveller.level()) return false;
        if (Duels.inDuel(p) || Battles.involved(p) || NullRealm.in(p)) return false;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
        return data == null || !data.getStatus().isStunned();
    }

    private static boolean free(ServerLevel level, ServerPlayer p, Vec3 at) {
        return level.isLoaded(net.minecraft.core.BlockPos.containing(at))
                && level.noCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(at));
    }

    private static void tell(ServerPlayer player, String why, String english, boolean bright, Object... args) {
        player.displayClientMessage(Component.translatableWithFallback("message.dmzzero.shoulder." + why, english, args)
                .withStyle(bright ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        testNear = null;
    }

    /** Self-test only: fake players to count as standing near a traveller, or null. */
    public static void setTestNear(List<ServerPlayer> near) {
        testNear = near;
    }

    public static int brought() {
        return brought;
    }

    /** Ki the last departure charged the traveller for those holding on. */
    public static float lastCharged() {
        return lastCharged;
    }
}
