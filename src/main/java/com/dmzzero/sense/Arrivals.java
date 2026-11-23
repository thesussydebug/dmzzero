package com.dmzzero.sense;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// the z fighters felt frieza and cell coming: a saga fighter or world boss far stronger than you appearing nearby is felt
// like a player's transformation. a boss changing form is a new entity in dmz, so that's felt too
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Arrivals {
    private static final int COST = TickCost.id("ki.powerSense", () -> Arrivals.PENDING.size() + Arrivals.FELT.size());
    /** More creatures dmz zero should feel arriving, for a datapack: entity type tag dmzzero:sensed_arrivals. */
    public static final TagKey<EntityType<?>> MORE = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(DMZZero.MOD_ID, "sensed_arrivals"));
    // a few ticks after it joins, once its quest has set its power
    private static final int DELAY = 3;
    private static final int MAX_PENDING = 64;
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<UUID, Long> FELT = new HashMap<>();

    private record Pending(LivingEntity source, long due) {
    }

    private Arrivals() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !(event.getEntity() instanceof LivingEntity e)) return;
        if (e instanceof net.minecraft.world.entity.player.Player || PENDING.size() >= MAX_PENDING) return;
        if (!ZeroConfig.SERVER.sense.get() || !ZeroConfig.SERVER.arrivals.get() || !arrives(e.getType())) return;
        PENDING.add(new Pending(e, e.level().getServer().getTickCount() + DELAY));
    }

    public static boolean arrives(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (id == null) return false;
        return CreatureKi.arrival(id.getNamespace(), id.getPath()) || type.is(MORE);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        long now = event.getServer().getTickCount();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.due > now) continue;
            it.remove();
            if (p.source.isAlive() && !p.source.isRemoved() && p.source.level() instanceof net.minecraft.server.level.ServerLevel level) {
                feel(p.source, level.players(), now);
            }
        }
    }

    /** Tells every weaker player in range, at most once per cooldown each. Returns how many felt it. */
    public static int feel(LivingEntity source, List<ServerPlayer> around, long now) {
        if (CreaturePower.hidden(source)) return 0;
        double power = CreaturePower.of(source);
        if (power <= 0) return 0;
        int color = CreaturePower.color(source);
        long cooldown = ZeroConfig.SERVER.arrivalCooldown.get() * 20L;
        int count = 0;
        for (ServerPlayer receiver : around) {
            if (receiver.isSpectator() || receiver.level() != source.level()) continue;
            StatsData other = StatsProvider.get(StatsCapability.INSTANCE, receiver).orElse(null);
            if (other == null || !other.getStatus().isHasCreatedCharacter()) continue;
            Long last = FELT.get(receiver.getUUID());
            if (last != null && now - last < cooldown) continue;
            int range = PowerSense.range(ZeroConfig.SERVER.senseRange.get(), other.getSkills().getSkillLevel("kisense"));
            double theirs = PowerSense.fullPower(other);
            if (!PowerSense.qualifies(power, theirs, ZeroConfig.SERVER.senseRatio.get(), false, other.getStatus().isAndroidUpgraded(),
                    receiver.distanceTo(source), range)) continue;
            PowerSense.notify(receiver, source.position(), color, PowerSense.strength(power, theirs));
            FELT.put(receiver.getUUID(), now);
            count++;
        }
        return count;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FELT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        FELT.clear();
    }

    /** The self-test feels the same arrival twice in a row. */
    public static void forget(UUID player) {
        FELT.remove(player);
    }
}
