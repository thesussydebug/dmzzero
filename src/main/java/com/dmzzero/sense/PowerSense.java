package com.dmzzero.sense;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.scouter.Scouters;
import com.dmzzero.network.PowerSenseS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.rival.Rivals;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

// weaker players feel it when someone far stronger transforms nearby
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class PowerSense {
    private static final int COST = TickCost.id("ki.powerSense", () -> PowerSense.PENDING.size());
    private static final String[] COMPASS = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
    private static final String[] COMPASS_TEXT = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
    // a couple of ticks after the form lands, so the battle power counts the new form
    private static final int DELAY = 2;
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(UUID source, long due) {
    }

    private PowerSense() {
    }

    public static boolean qualifies(double sourcePower, double receiverPower, double ratio, boolean sourceAndroid,
                                    boolean receiverAndroid, double distance, double range) {
        if (sourceAndroid || receiverAndroid || receiverPower <= 0 || sourcePower <= 0) return false;
        return distance <= range && sourcePower >= receiverPower * ratio;
    }

    // your rival's rise is felt whatever their strength, and from twice as far
    public static boolean qualifiesRival(double sourcePower, double receiverPower, boolean sourceAndroid,
                                         boolean receiverAndroid, double distance, double range) {
        if (sourceAndroid || receiverAndroid || receiverPower <= 0 || sourcePower <= 0) return false;
        return distance <= range * 2;
    }

    public static String messageKey(float strength, boolean named, boolean rival) {
        if (rival) return "message.dmzzero.sense.rival";
        boolean terrifying = strength >= 0.75f;
        if (named) return terrifying ? "message.dmzzero.sense.named_terrifying" : "message.dmzzero.sense.named";
        return terrifying ? "message.dmzzero.sense.terrifying" : "message.dmzzero.sense.immense";
    }

    public static int range(int base, int kiSenseLevel) {
        return kiSenseLevel > 0 ? base * 2 : base;
    }

    // 0.25 at the threshold, full once a hundred times further past it
    public static float strength(double sourcePower, double receiverPower) {
        double over = sourcePower / Math.max(1e-9, receiverPower) / 1.5;
        double t = Math.max(0, Math.min(1, Math.log10(Math.max(1, over)) / 2));
        return (float) (0.25 + 0.75 * t);
    }

    // minecraft's north is -z
    public static String compass(double dx, double dz) {
        return COMPASS[index(dx, dz)];
    }

    static int index(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        return Math.floorMod((int) Math.round(angle / 45.0), 8);
    }

    // degrees from where the player looks, positive to the right
    public static float screenAngle(float playerYaw, double dx, double dz) {
        float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float d = (target - playerYaw) % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    public static void onAscent(ServerPlayer source) {
        if (!ZeroConfig.SERVER.sense.get()) return;
        PENDING.add(new Pending(source.getUUID(), source.server.getTickCount() + DELAY));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.due > now) continue;
            it.remove();
            ServerPlayer source = server.getPlayerList().getPlayer(p.source);
            if (source != null) feel(source, source.serverLevel().players());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    public static int feel(ServerPlayer source, List<ServerPlayer> around) {
        return feel(source, around, PowerSense::fullPower);
    }

    public static int feel(ServerPlayer source, List<ServerPlayer> around, ToDoubleFunction<StatsData> powerOf) {
        StatsData data = stats(source);
        if (data == null || data.getStatus().isAndroidUpgraded()) return 0;
        double power = powerOf.applyAsDouble(data);
        int color = color(data.getCharacter().getActiveAuraColor());
        int felt = 0;
        for (ServerPlayer receiver : around) {
            if (receiver == source || receiver.isSpectator() || receiver.level() != source.level()) continue;
            StatsData other = stats(receiver);
            if (other == null || !other.getStatus().isHasCreatedCharacter()) continue;
            double distance = receiver.distanceTo(source);
            int range = range(ZeroConfig.SERVER.senseRange.get(), other.getSkills().getSkillLevel("kisense"));
            double theirs = powerOf.applyAsDouble(other);
            boolean rival = Rivals.isRival(receiver, source.getUUID());
            boolean android = other.getStatus().isAndroidUpgraded();
            boolean qualifies = rival
                    ? qualifiesRival(power, theirs, false, android, distance, range)
                    : qualifies(power, theirs, ZeroConfig.SERVER.senseRatio.get(), false, android, distance, range);
            if (!qualifies) continue;
            String name = knows(receiver, source, rival) ? source.getGameProfile().getName() : null;
            notify(receiver, source.position(), color, strength(power, theirs), name, rival);
            felt++;
        }
        return felt;
    }

    // you know someone's ki once you've fought them, while you're in their party, and always your rival's
    static boolean knows(ServerPlayer receiver, ServerPlayer source, boolean rival) {
        if (!ZeroConfig.SERVER.senseNames.get()) return false;
        return rival || KnownKi.knows(receiver, source.getUUID()) || PartyManager.areInSameParty(receiver, source);
    }

    public static void notify(ServerPlayer receiver, Vec3 at, int color, float strength) {
        notify(receiver, at, color, strength, null, false);
    }

    public static void notify(ServerPlayer receiver, Vec3 at, int color, float strength, String name, boolean rival) {
        ZeroNetwork.send(receiver, new PowerSenseS2C(at.x, at.y + 1, at.z, color, strength));
        int i = index(at.x - receiver.getX(), at.z - receiver.getZ());
        Component direction = Component.translatableWithFallback("direction.dmzzero." + COMPASS[i], COMPASS_TEXT[i]);
        boolean named = name != null;
        boolean terrifying = strength >= 0.75f;
        String key = messageKey(strength, named, rival && named);
        lastKey = key;
        Component message;
        if (rival && named) {
            message = Component.translatableWithFallback(key, "You feel your rival %s's power rise to the %s!", name, direction)
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
        } else if (named) {
            message = Component.translatableWithFallback(key, terrifying ? "You feel %s's terrifying power to the %s!"
                    : "You feel %s's power rise to the %s.", name, direction).withStyle(terrifying ? ChatFormatting.RED : ChatFormatting.GOLD);
        } else {
            message = Component.translatableWithFallback(key, terrifying ? "You feel a terrifying power to the %s!"
                    : "You feel an immense power to the %s.", direction).withStyle(terrifying ? ChatFormatting.RED : ChatFormatting.GOLD);
        }
        // a scouter shows the reading itself, so its wearer gets the numbers instead of these words
        if (!Scouters.readsSpikes(receiver)) receiver.displayClientMessage(message, true);
    }

    // the last message key sent, read by the self-test
    private static volatile String lastKey = "";

    public static String lastKey() {
        return lastKey;
    }

    // dmz scales battle power by the current power release; a player resting at 0% still has a full strength to compare
    public static double fullPower(StatsData data) {
        var resources = data.getResources();
        int release = resources.getPowerRelease();
        try {
            resources.setPowerRelease(100);
            return data.getBattlePowerExact();
        } finally {
            resources.setPowerRelease(release);
        }
    }

    private static StatsData stats(ServerPlayer player) {
        return StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
    }

    public static int color(String hex) {
        if (hex == null) return 0xFFFFFF;
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }
}
