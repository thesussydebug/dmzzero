package com.dmzzero.lift;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// give a friend a lift: they ride on your back while you fly. Crouch-click a party member with an empty hand, stand up, and you're on
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Lifts {
    private static final int COST = TickCost.id("travel.lifts", () -> Lifts.PENDING.size() + Lifts.CROUCHING.size());
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    // carriers crouching on the ground, and for how long
    private static final Map<UUID, Integer> CROUCHING = new HashMap<>();

    private Lifts() {
    }

    record Pending(UUID carrier, long askedAt) {
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer clicker) || !(event.getTarget() instanceof ServerPlayer carrier)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND || !clicker.isShiftKeyDown() || !clicker.getMainHandItem().isEmpty()) return;
        String why = ask(clicker, carrier);
        if ("ok".equals(why)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        } else if (!"off".equals(why) && !"not_party".equals(why)) {
            clicker.displayClientMessage(Component.translatableWithFallback("message.dmzzero.lift.cant_" + why, cantText(why)).withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static String cantText(String why) {
        return switch (why) {
            case "riding" -> "You're already riding something.";
            case "full" -> "They're already carrying someone, or riding something.";
            case "too_far" -> "Get closer to climb on.";
            case "down" -> "Not while one of you is knocked down.";
            default -> "You can't climb on right now.";
        };
    }

    /** The crouch-click: "ok" when you'll climb on once you stand up, or why not. */
    public static String ask(ServerPlayer clicker, ServerPlayer carrier) {
        String why = LiftMath.reason(new LiftMath.Ask(ZeroConfig.SERVER.lifts.get(),
                carrier != clicker && PartyManager.areInSameParty(clicker, carrier),
                clicker.isPassenger(), carrier.isVehicle(), carrier.isPassenger(),
                clicker.distanceTo(carrier), down(clicker), down(carrier)), ZeroConfig.SERVER.liftReach.get());
        if (!"ok".equals(why)) return why;
        PENDING.put(clicker.getUUID(), new Pending(carrier.getUUID(), clicker.server.getTickCount()));
        clicker.displayClientMessage(Component.translatableWithFallback("message.dmzzero.lift.stand",
                "Stand up to climb on %s's back.", carrier.getGameProfile().getName()).withStyle(ChatFormatting.GRAY), true);
        return why;
    }

    static boolean down(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator()) return true;
        StatsData d = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
        return d != null && (d.getStatus().isKnockedDown() || d.getStatus().isStunned());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        net.minecraft.server.MinecraftServer server = event.getServer();
        step(server.getTickCount(), id -> server.getPlayerList().getPlayer(id), server.getPlayerList().getPlayers());
    }

    /** One tick: pending climbers who stood up get on, carriers crouching on the ground set theirs down. */
    public static void step(long now, java.util.function.Function<UUID, ServerPlayer> lookup, Iterable<ServerPlayer> carriers) {
        if (!PENDING.isEmpty()) {
            Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Pending> e = it.next();
                ServerPlayer clicker = lookup.apply(e.getKey());
                ServerPlayer carrier = lookup.apply(e.getValue().carrier());
                if (clicker == null || carrier == null || !LiftMath.stillWaiting(e.getValue().askedAt(), now)) {
                    it.remove();
                    continue;
                }
                if (clicker.isShiftKeyDown()) continue;
                it.remove();
                climbOn(clicker, carrier);
            }
        }
        // a carrier crouching on the ground for a moment sets their passenger down; one knocked down drops them
        for (ServerPlayer carrier : carriers) {
            if (!(carrier.getFirstPassenger() instanceof ServerPlayer)) {
                CROUCHING.remove(carrier.getUUID());
                continue;
            }
            if (down(carrier)) {
                drop(carrier);
                continue;
            }
            int t = carrier.isShiftKeyDown() && carrier.onGround() ? CROUCHING.getOrDefault(carrier.getUUID(), 0) + 1 : 0;
            CROUCHING.put(carrier.getUUID(), t);
            if (LiftMath.setsDown(carrier.onGround(), t)) {
                drop(carrier);
            }
        }
    }

    /** Re-checks everything, then puts the clicker on the carrier's back. */
    public static boolean climbOn(ServerPlayer clicker, ServerPlayer carrier) {
        String why = LiftMath.reason(new LiftMath.Ask(ZeroConfig.SERVER.lifts.get(), PartyManager.areInSameParty(clicker, carrier),
                clicker.isPassenger(), carrier.isVehicle(), carrier.isPassenger(), clicker.distanceTo(carrier), down(clicker), down(carrier)),
                ZeroConfig.SERVER.liftReach.get());
        if (!"ok".equals(why) || clicker.level() != carrier.level()) return false;
        if (!clicker.startRiding(carrier, true)) return false;
        // vanilla tells everyone watching the carrier, but not the carrier themself
        if (carrier.connection != null) carrier.connection.send(new ClientboundSetPassengersPacket(carrier));
        carrier.displayClientMessage(Component.translatableWithFallback("message.dmzzero.lift.carrying",
                "%s is riding on your back. Crouch on the ground to set them down.", clicker.getGameProfile().getName()).withStyle(ChatFormatting.GRAY), true);
        clicker.displayClientMessage(Component.translatableWithFallback("message.dmzzero.lift.riding",
                "You're riding on %s's back. Crouch to hop off.", carrier.getGameProfile().getName()).withStyle(ChatFormatting.GRAY), true);
        return true;
    }

    private static void drop(ServerPlayer carrier) {
        carrier.ejectPassengers();
        if (carrier.connection != null) carrier.connection.send(new ClientboundSetPassengersPacket(carrier));
        CROUCHING.remove(carrier.getUUID());
    }

    // let go in the air, you float down instead of falling
    @SubscribeEvent
    public static void onDismount(EntityMountEvent event) {
        if (!event.isDismounting() || event.getLevel().isClientSide()) return;
        Entity vehicle = event.getEntityBeingMounted();
        if (!(event.getEntityMounting() instanceof ServerPlayer rider) || !(vehicle instanceof ServerPlayer carrier)) return;
        if (carrier.connection != null) carrier.connection.send(new ClientboundSetPassengersPacket(carrier));
        int floatTicks = ZeroConfig.SERVER.liftFloatSeconds.get() * 20;
        if (ZeroConfig.SERVER.lifts.get() && floatTicks > 0 && LiftMath.needsSoftLanding(rider.onGround(), 0)) {
            rider.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, floatTicks, 0, false, false, true));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        CROUCHING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        CROUCHING.clear();
    }

    public static boolean waiting(ServerPlayer clicker) {
        return PENDING.containsKey(clicker.getUUID());
    }
}
