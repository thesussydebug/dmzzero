package com.dmzzero.lift;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.status.TickCost;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

// carry an animal or a villager over your head: crouch-click it with an empty hand, crouch on the ground to set it down
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Carries {
    private static final int COST = TickCost.id("travel.lifts", () -> Carries.CARRIED.size());
    // carrier -> what they carry
    private static final Map<UUID, UUID> CARRIED = new HashMap<>();
    private static final Map<UUID, Integer> CROUCHING = new HashMap<>();
    private static int setDown;

    private Carries() {
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer carrier) || event.getTarget() instanceof Player) return;
        if (event.getHand() != InteractionHand.MAIN_HAND || !carrier.isShiftKeyDown() || !carrier.getMainHandItem().isEmpty()) return;
        String why = pickUp(carrier, event.getTarget());
        if ("ok".equals(why)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        } else if (!CarryMath.silent(why)) {
            carrier.displayClientMessage(Component.translatableWithFallback("message.dmzzero.carry.cant_" + why, cantText(why)).withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static String cantText(String why) {
        return switch (why) {
            case "not_yours" -> "That's someone else's pet.";
            case "too_big" -> "That's too big to pick up.";
            case "riding" -> "Not while it's riding something.";
            case "full" -> "You're already carrying something, or riding.";
            case "too_far" -> "Get closer to pick it up.";
            case "down" -> "Not while you're knocked down.";
            default -> "You can't pick that up right now.";
        };
    }

    public static CarryMath.Kind kindOf(Entity e) {
        if (e instanceof Player || !(e instanceof LivingEntity) || e instanceof Enemy) return CarryMath.Kind.NONE;
        if (e instanceof AbstractVillager) return CarryMath.Kind.VILLAGER;
        // horses, donkeys, llamas and camels open their inventory on a crouch-click, so they stay vanilla's
        if (e instanceof Animal && !(e instanceof AbstractHorse)) return CarryMath.Kind.ANIMAL;
        return CarryMath.Kind.NONE;
    }

    private static boolean ownedByOther(Entity e, Player carrier) {
        if (!(e instanceof OwnableEntity pet)) return false;
        UUID owner = pet.getOwnerUUID();
        return owner != null && !owner.equals(carrier.getUUID());
    }

    /** The crouch-click: "ok" once it's on your head, or why not. */
    public static String pickUp(ServerPlayer carrier, Entity target) {
        if (!target.isAlive()) return "kind";
        String why = CarryMath.reason(new CarryMath.Ask(ZeroConfig.SERVER.carryAnimals.get(), ZeroConfig.SERVER.carryVillagers.get(),
                kindOf(target), ownedByOther(target, carrier), target.getBbWidth(), ZeroConfig.SERVER.carryMaxWidth.get(),
                target.isPassenger(), target.isVehicle(), carrier.isPassenger(), carrier.isVehicle(),
                carrier.distanceTo(target), ZeroConfig.SERVER.liftReach.get(), Lifts.down(carrier)));
        if (!"ok".equals(why)) return why;
        if (target.level() != carrier.level()) return "too_far";
        if (!target.startRiding(carrier, true)) return "full";
        // vanilla tells everyone watching the carrier, but not the carrier themself
        if (carrier.connection != null) carrier.connection.send(new ClientboundSetPassengersPacket(carrier));
        CARRIED.put(carrier.getUUID(), target.getUUID());
        CROUCHING.remove(carrier.getUUID());
        carrier.displayClientMessage(Component.translatableWithFallback("message.dmzzero.carry.picked",
                "Carrying %s. Crouch on the ground to set it down.", target.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
        return why;
    }

    /** Where a carried animal or villager sits on a player, or null to leave vanilla's spot. */
    public static Double overhead(Entity vehicle) {
        if (!(vehicle instanceof Player p)) return null;
        Entity first = p.getFirstPassenger();
        if (first == null || first instanceof Player) return null;
        return CarryMath.overhead(p.getBbHeight());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CARRIED.isEmpty()) return;
        long start = TickCost.start();
        MinecraftServer server = event.getServer();
        step(id -> server.getPlayerList().getPlayer(id));
        TickCost.add(COST, start);
    }

    /** One tick: a carrier crouching on the ground for a moment sets it down; one knocked down or asleep drops it. */
    public static void step(Function<UUID, ServerPlayer> lookup) {
        Iterator<Map.Entry<UUID, UUID>> it = CARRIED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> e = it.next();
            ServerPlayer carrier = lookup.apply(e.getKey());
            Entity first = carrier == null ? null : carrier.getFirstPassenger();
            if (first == null || !first.getUUID().equals(e.getValue())) {
                it.remove();
                CROUCHING.remove(e.getKey());
                continue;
            }
            if (Lifts.down(carrier) || carrier.isSleeping()) {
                it.remove();
                drop(carrier);
                continue;
            }
            int t = carrier.isShiftKeyDown() && carrier.onGround() ? CROUCHING.getOrDefault(carrier.getUUID(), 0) + 1 : 0;
            CROUCHING.put(carrier.getUUID(), t);
            if (LiftMath.setsDown(carrier.onGround(), t)) {
                it.remove();
                drop(carrier);
                setDown++;
            }
        }
    }

    private static void drop(ServerPlayer carrier) {
        CROUCHING.remove(carrier.getUUID());
        Entity carried = carrier.getFirstPassenger();
        carrier.ejectPassengers();
        if (carried != null && !(carried instanceof Player)) setDown(carrier, carried);
        if (carrier.connection != null) carrier.connection.send(new ClientboundSetPassengersPacket(carrier));
    }

    // vanilla leaves it on top of the carrier's head; it goes in front of them, at their feet, where there's room
    private static void setDown(ServerPlayer carrier, Entity carried) {
        double[] d = CarryMath.inFront(carrier.getYRot(), carrier.getBbWidth(), carried.getBbWidth());
        Vec3 spot = new Vec3(carrier.getX() + d[0], carrier.getY(), carrier.getZ() + d[1]);
        AABB box = carried.getDimensions(carried.getPose()).makeBoundingBox(spot);
        if (!carried.level().isLoaded(BlockPos.containing(spot)) || !carried.level().noCollision(carried, box)) spot = carrier.position();
        carried.teleportTo(spot.x, spot.y, spot.z);
    }

    /** Puts down whatever the player carries, wherever they are. */
    public static void release(ServerPlayer carrier) {
        if (CARRIED.remove(carrier.getUUID()) != null) drop(carrier);
    }

    // dropped in the air, it floats down instead of falling
    @SubscribeEvent
    public static void onDismount(EntityMountEvent event) {
        if (!event.isDismounting() || event.getLevel().isClientSide()) return;
        if (!(event.getEntityBeingMounted() instanceof ServerPlayer carrier) || !(event.getEntityMounting() instanceof LivingEntity carried)
                || carried instanceof Player) return;
        if (carrier.connection != null) carrier.connection.send(new ClientboundSetPassengersPacket(carrier));
        int floatTicks = ZeroConfig.SERVER.liftFloatSeconds.get() * 20;
        if (floatTicks > 0 && !carrier.onGround()) {
            carried.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, floatTicks, 0, false, false, true));
        }
    }

    // held over a head, a villager's eyes are often inside the block above a doorway
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!event.getSource().is(DamageTypes.IN_WALL)) return;
        LivingEntity hurt = event.getEntity();
        if (!(hurt instanceof Player) && hurt.getVehicle() instanceof Player) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer carrier) release(carrier);
    }

    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer carrier) release(carrier);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer carrier) release(carrier);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer carrier) release(carrier);
        CROUCHING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CARRIED.clear();
        CROUCHING.clear();
    }

    public static boolean carrying(ServerPlayer carrier) {
        return CARRIED.containsKey(carrier.getUUID());
    }

    public static int setDowns() {
        return setDown;
    }
}
