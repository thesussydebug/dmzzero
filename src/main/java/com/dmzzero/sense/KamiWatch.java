package com.dmzzero.sense;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.network.KamiWatchS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.status.TickCost;
import net.minecraftforge.registries.ForgeRegistries;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// kami's watch: crouch still on kami's lookout and you sense every strong power in the dimension
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class KamiWatch {
    private static final int COST = TickCost.id("ki.kamiWatch", () -> KamiWatch.WATCHES.size());
    public static final ResourceLocation LOOKOUT = new ResourceLocation("dragonminez", "kamilookout");
    private static final int REFRESH = 40;
    private static final Map<UUID, Watch> WATCHES = new HashMap<>();

    private KamiWatch() {
    }

    static final class Watch {
        Vec3 last;
        int still;
        boolean watching;
        long nextSense;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        long start = TickCost.start();
        playerTick(event);
        TickCost.add(COST, start);
    }

    private static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (!ZeroConfig.SERVER.kamiWatch.get()) {
            Watch w = WATCHES.remove(player.getUUID());
            if (w != null && w.watching) end(player);
            return;
        }
        tick(player, player.server.getTickCount());
    }

    static void tick(ServerPlayer player, long now) {
        boolean crouching = player.isShiftKeyDown() && player.onGround() && player.isAlive() && !player.isSpectator();
        Watch w = WATCHES.get(player.getUUID());
        if (!crouching) {
            if (w != null) {
                if (w.watching) end(player);
                WATCHES.remove(player.getUUID());
            }
            return;
        }
        if (w == null) {
            w = new Watch();
            WATCHES.put(player.getUUID(), w);
        }
        Vec3 pos = player.position();
        if (w.last != null && !WatchMath.still(pos.distanceToSqr(w.last))) {
            if (w.watching) end(player);
            w.still = 0;
            w.watching = false;
        } else {
            w.still++;
        }
        w.last = pos;
        if (!WatchMath.settled(w.still, ZeroConfig.SERVER.watchCrouchSeconds.get() * 20) || now < w.nextSense) return;
        w.nextSense = now + REFRESH;
        if (!onLookout(player.serverLevel(), player.blockPosition())) {
            if (w.watching) end(player);
            w.watching = false;
            return;
        }
        if (!w.watching) {
            w.watching = true;
            player.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.watch.begin",
                    "You open your senses to the whole world, as Kami does.").withStyle(ChatFormatting.GREEN));
        }
        sense(player);
    }

    /** Standing on kami's lookout: inside one of its pieces, or right on top of one. */
    public static boolean onLookout(ServerLevel level, BlockPos pos) {
        Structure lookout = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(LOOKOUT);
        if (lookout == null) return false;
        return level.structureManager().getStructureWithPieceAt(pos, lookout).isValid()
                || level.structureManager().getStructureWithPieceAt(pos.below(), lookout).isValid();
    }

    /** Every strong power in the watcher's dimension, strongest first. Sends it to the watcher. */
    public static List<KamiWatchS2C.Entry> sense(ServerPlayer watcher) {
        List<LivingEntity> creatures = ZeroConfig.SERVER.watchCreatures.get() ? creatures(watcher.serverLevel()) : List.of();
        return sense(watcher, watcher.serverLevel().players(), creatures);
    }

    public static List<KamiWatchS2C.Entry> sense(ServerPlayer watcher, List<? extends ServerPlayer> candidates) {
        return sense(watcher, candidates, List.of());
    }

    public static List<KamiWatchS2C.Entry> sense(ServerPlayer watcher, List<? extends ServerPlayer> candidates, List<? extends LivingEntity> creatures) {
        StatsData mine = stats(watcher);
        double own = mine == null ? 0 : mine.getBattlePowerExact();
        double share = ZeroConfig.SERVER.watchShare.get();
        Map<Integer, LivingEntity> byId = new HashMap<>();
        List<WatchMath.Power> powers = new ArrayList<>();
        for (ServerPlayer other : candidates) {
            if (other == watcher || !other.isAlive() || other.isSpectator() || other.level() != watcher.level()) continue;
            StatsData d = stats(other);
            if (d == null || !d.getStatus().isHasCreatedCharacter() || d.getStatus().isAndroidUpgraded()) continue;
            double power = d.getBattlePowerExact();
            if (!WatchMath.strong(power, own, share)) continue;
            powers.add(new WatchMath.Power(other.getId(), power, other.getX() - watcher.getX(), other.getZ() - watcher.getZ()));
            byId.put(other.getId(), other);
        }
        for (LivingEntity c : creatures) {
            if (c instanceof Player || !c.isAlive() || c.level() != watcher.level() || CreaturePower.hidden(c)) continue;
            double power = CreaturePower.of(c);
            if (!WatchMath.strong(power, own, share)) continue;
            powers.add(new WatchMath.Power(c.getId(), power, c.getX() - watcher.getX(), c.getZ() - watcher.getZ()));
            byId.put(c.getId(), c);
        }
        List<KamiWatchS2C.Entry> entries = new ArrayList<>();
        for (WatchMath.Power p : WatchMath.strongest(powers, ZeroConfig.SERVER.watchMax.get())) {
            LivingEntity e = byId.get(p.id());
            int color;
            String name;
            if (e instanceof ServerPlayer other) {
                StatsData d = stats(other);
                color = d == null ? 0xFFFFFF : PowerSense.color(d.getCharacter().getActiveAuraColor());
                name = PowerSense.knows(watcher, other, com.dmzzero.rival.Rivals.isRival(watcher, other.getUUID())) ? other.getGameProfile().getName() : "";
            } else {
                // kami knows the creatures of his world
                color = CreaturePower.color(e);
                name = e.getName().getString();
            }
            entries.add(new KamiWatchS2C.Entry(e.getX(), e.getZ(), (int) Math.round(Math.hypot(p.dx(), p.dz())), color,
                    PowerSense.strength(p.power(), own), name));
        }
        if (ZeroNetwork.reachable(watcher)) {
            ZeroNetwork.send(watcher, new KamiWatchS2C(entries));
        }
        watcher.displayClientMessage(summary(watcher, entries), true);
        return entries;
    }

    // dmz's and sparking's own creatures loaded in the level. dmz gives every mob a battle power, so vanilla ones are left out
    // or zombies would fill the list. only the watchers' own refreshes walk it, every 2 seconds
    static List<LivingEntity> creatures(ServerLevel level) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof LivingEntity l && !(e instanceof Player) && (e instanceof DBSagasEntity || dmzOwn(e))) out.add(l);
        }
        return out;
    }

    private static boolean dmzOwn(Entity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id != null && (id.getNamespace().equals("dragonminez") || id.getNamespace().equals("dmzsparking"));
    }

    // for players without dmz zero too: a line on the action bar
    static Component summary(ServerPlayer watcher, List<KamiWatchS2C.Entry> entries) {
        if (entries.isEmpty()) {
            return Component.translatableWithFallback("message.dmzzero.watch.none", "You sense no strong power anywhere in this world.")
                    .withStyle(ChatFormatting.GRAY);
        }
        KamiWatchS2C.Entry top = entries.get(0);
        String way = WatchMath.compass(top.x() - watcher.getX(), top.z() - watcher.getZ());
        if (entries.size() == 1) {
            return Component.translatableWithFallback("message.dmzzero.watch.summary_one", "You sense one strong power, %s blocks %s.", top.distance(), way)
                    .withStyle(ChatFormatting.GREEN);
        }
        return Component.translatableWithFallback("message.dmzzero.watch.summary", "You sense %s strong powers. The strongest is %s blocks %s.",
                entries.size(), top.distance(), way).withStyle(ChatFormatting.GREEN);
    }

    private static void end(ServerPlayer player) {
        ZeroNetwork.send(player, new KamiWatchS2C(List.of()));
    }

    private static StatsData stats(ServerPlayer player) {
        return player == null ? null : StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WATCHES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Watch w = WATCHES.remove(event.getEntity().getUUID());
        if (w != null && w.watching && event.getEntity() instanceof ServerPlayer p) end(p);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WATCHES.clear();
    }

    public static boolean watching(ServerPlayer player) {
        Watch w = WATCHES.get(player.getUUID());
        return w != null && w.watching;
    }
}
