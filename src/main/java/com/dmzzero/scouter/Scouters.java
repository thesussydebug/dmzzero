package com.dmzzero.scouter;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.network.ScouterPingS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.scouter.ScouterMath.Channel;
import com.dmzzero.scouter.ScouterMath.Spike;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.entities.IBattlePower;
import com.dragonminez.common.racial.impl.GlindDivinity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.util.CuriosUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// scouters as radios: a channel per colour, pings for your channel, and a warning when a power level jumps
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Scouters {
    private static final int COST = TickCost.id("ki.scouters", () -> Scouters.LAST_HEARD.size() + Scouters.LAST_ALERT.size());
    public static final double NO_READING = -1;
    public static final double HIDDEN = -2;
    private static final ScouterMath.SpikeWatch WATCH = new ScouterMath.SpikeWatch();
    private static final ScouterMath.Heard HEARD = new ScouterMath.Heard();
    private static final ScouterMath.PingCooldowns PINGS = new ScouterMath.PingCooldowns();
    // fake players aren't in the player list, so the self-test adds them here
    private static final Set<ServerPlayer> TEST_PLAYERS = new LinkedHashSet<>();
    private static final Map<UUID, String> LAST_HEARD = new HashMap<>();
    private static final Map<UUID, String> LAST_ALERT = new HashMap<>();
    private static final Map<UUID, ScouterPingS2C> LAST_PING = new HashMap<>();

    private Scouters() {
    }

    // which channel someone's scouter is on, or null without one
    public static Channel wearing(Player p) {
        ItemStack stack = CuriosUtil.getFirstStackForItem(p, "head_tech", "scouter");
        if (stack.isEmpty()) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : Channel.of(id.getPath());
    }

    // bulma's anti-ki cloak hides you from scouters, as it does in dmz's own hud
    public static boolean cloaked(LivingEntity e) {
        return e instanceof Player p && CuriosUtil.getFirstStackForItem(p, "head_tech", "anti_ki_cloak").getItem() == MainItems.ANTI_KI_CLOAK.get();
    }

    public static double battlePower(LivingEntity e) {
        if (e instanceof Player p) {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
            return data == null ? 0 : data.getBattlePower();
        }
        if (e instanceof IBattlePower bp) return bp.getBattlePower();
        return 0;
    }

    private static boolean readable(LivingEntity e) {
        return e.isAlive() && !e.isSpectator() && (e instanceof Player || e instanceof IBattlePower);
    }

    // scouter wearers read numbers, so power sense leaves the words to them
    public static boolean readsSpikes(ServerPlayer p) {
        return ZeroConfig.SERVER.scouterSpikes.get() && wearing(p) != null;
    }

    // ---- channel talk

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sc")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> say(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "message")).equals("ok") ? 1 : 0)));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChat(ServerChatEvent event) {
        String raw = event.getRawText();
        if (raw == null || raw.length() < 2 || raw.charAt(0) != '#') return;
        ServerPlayer p = event.getPlayer();
        if (!ZeroConfig.SERVER.scouterChannels.get() || wearing(p) == null) return;
        event.setCanceled(true);
        p.server.execute(() -> say(p, raw.substring(1)));
    }

    public static String say(ServerPlayer from, String text) {
        if (!ZeroConfig.SERVER.scouterChannels.get()) return refuse(from, "disabled", "Scouter talk is turned off on this server.");
        Channel ch = wearing(from);
        if (ch == null) return refuse(from, "no_scouter", "Put a scouter on first.");
        String msg = text.strip();
        if (msg.isEmpty()) return "empty";
        String name = from.getGameProfile().getName();
        Component line = prefix(ch).append(Component.literal(name + ": ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(msg).withStyle(ChatFormatting.GRAY));
        for (ServerPlayer p : everyone(from.server)) {
            if (wearing(p) != ch) continue;
            p.sendSystemMessage(line);
            pip(p);
            LAST_HEARD.put(p.getUUID(), name + ": " + msg);
        }
        DMZZero.LOG.info("[Scouter/{}] {}: {}", ch.id(), name, msg);
        return "ok";
    }

    private static MutableComponent prefix(Channel ch) {
        return Component.translatableWithFallback("message.dmzzero.scouter.prefix", "[Scouter]")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(ch.color)).withBold(true))
                .append(Component.literal(" ").withStyle(Style.EMPTY.withBold(false)));
    }

    // ---- pings

    public static String ping(ServerPlayer from, int entityId, Vec3 at) {
        if (!ZeroConfig.SERVER.scouterPings.get()) return "disabled";
        Channel ch = wearing(from);
        if (ch == null) return actionBar(from, "no_scouter", "Put a scouter on first.");
        if (!PINGS.tryPing(from.getUUID(), from.server.getTickCount())) return "cooldown";
        LivingEntity target = null;
        if (entityId >= 0) {
            Entity e = from.level().getEntity(entityId);
            if (e instanceof LivingEntity living && living.isAlive() && living != from) target = living;
        }
        Vec3 pos = target != null ? target.position() : at;
        if (pos == null || !ScouterMath.finite(pos.x, pos.y, pos.z)) return "no_target";
        if (from.position().distanceTo(pos) > ZeroConfig.SERVER.scouterPingRange.get() + 2) return actionBar(from, "too_far", "Too far for your scouter to mark.");
        if (target != null && GlindDivinity.isHiddenFrom(target, from)) return actionBar(from, "hidden", "Your scouter can't read anything there.");
        String label = target != null ? target.getDisplayName().getString()
                : Component.translatableWithFallback("gui.dmzzero.scouter.here", "Here").getString();
        double bp = target == null || !readable(target) ? NO_READING : cloaked(target) ? HIDDEN : battlePower(target);
        double y = target != null ? pos.y + target.getBbHeight() : pos.y + 1;
        String sender = from.getGameProfile().getName();
        ScouterPingS2C msg = new ScouterPingS2C(ScouterPingS2C.PING, ch.ordinal(), sender, label, pos.x, y, pos.z, target != null ? target.getId() : -1, bp, 0);
        Component elsewhere = null;
        for (ServerPlayer p : everyone(from.server)) {
            if (wearing(p) != ch) continue;
            if (p.level() == from.level()) {
                ZeroNetwork.send(p, msg);
                LAST_PING.put(p.getUUID(), msg);
                if (p != from) pip(p);
            } else {
                if (elsewhere == null) elsewhere = elsewhereLine(ch, sender, label, bp, from.level());
                p.sendSystemMessage(elsewhere);
            }
        }
        return "ok";
    }

    private static Component elsewhereLine(Channel ch, String sender, String label, double bp, Level level) {
        MutableComponent line = prefix(ch).append(Component.translatableWithFallback("message.dmzzero.scouter.pinged_elsewhere", "%s pinged %s on %s",
                sender, label, worldName(level)).withStyle(ChatFormatting.GRAY));
        if (bp >= 0) line.append(Component.literal(" · " + ScouterMath.reading(bp)).withStyle(ChatFormatting.WHITE));
        return line;
    }

    public static String worldName(Level level) {
        ResourceLocation id = level.dimension().location();
        if (id.equals(Level.OVERWORLD.location())) return "Earth";
        String[] words = id.getPath().split("_");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    // ---- power spikes

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long start = TickCost.start();
        serverTick(event);
        TickCost.add(COST, start);
    }

    private static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().getTickCount();
        if (now % 20 == 0) spikes(event.getServer(), now);
    }

    // samples everything readable near any wearer once a second; returns how many alerts went out
    public static int spikes(MinecraftServer server, long now) {
        return spikes(server, now, ZeroConfig.SERVER.scouterSpikeRatio.get(), ZeroConfig.SERVER.scouterSpikeMin.get());
    }

    public static int spikes(MinecraftServer server, long now, double ratio, double min) {
        if (!ZeroConfig.SERVER.scouterSpikes.get()) return 0;
        List<ServerPlayer> wearers = new ArrayList<>();
        for (ServerPlayer p : everyone(server)) if (wearing(p) != null) wearers.add(p);
        if (now % 200 == 0) {
            WATCH.prune(now);
            HEARD.prune(now);
        }
        if (wearers.isEmpty()) return 0;
        double range = ZeroConfig.SERVER.scouterSpikeRange.get();
        Map<UUID, LivingEntity> targets = new LinkedHashMap<>();
        for (ServerPlayer w : wearers) {
            for (LivingEntity e : w.level().getEntitiesOfClass(LivingEntity.class, w.getBoundingBox().inflate(range), Scouters::readable)) {
                targets.putIfAbsent(e.getUUID(), e);
            }
        }
        for (ServerPlayer t : TEST_PLAYERS) if (readable(t)) targets.putIfAbsent(t.getUUID(), t);
        int sent = 0;
        for (LivingEntity t : targets.values()) {
            if (cloaked(t)) {
                WATCH.forget(t.getUUID());
                continue;
            }
            Spike s = WATCH.sample(t.getUUID(), now, battlePower(t), ratio, min);
            if (s == null) continue;
            for (ServerPlayer w : wearers) {
                if (w == t || w.level() != t.level() || w.distanceToSqr(t) > range * range) continue;
                if (GlindDivinity.isHiddenFrom(t, w)) continue;
                if (!HEARD.tryHear(w.getUUID(), t.getUUID(), now)) continue;
                alert(w, t, s);
                sent++;
            }
        }
        return sent;
    }

    private static void alert(ServerPlayer w, LivingEntity t, Spike s) {
        Channel ch = wearing(w);
        String label = t.getDisplayName().getString();
        double dx = t.getX() - w.getX(), dz = t.getZ() - w.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        String text = label + " " + ScouterMath.reading(s.from()) + " → " + ScouterMath.reading(s.to()) + " · " + dist + " m " + ScouterMath.compass(dx, dz);
        w.displayClientMessage(Component.translatableWithFallback("message.dmzzero.scouter.spike", "Power spike! %s", text)
                .withStyle(ChatFormatting.RED), true);
        LAST_ALERT.put(w.getUUID(), text);
        if (ch != null) {
            ZeroNetwork.send(w, new ScouterPingS2C(ScouterPingS2C.SPIKE, ch.ordinal(), "", label, t.getX(), t.getY() + t.getBbHeight(), t.getZ(),
                    t.getId(), s.to(), s.from()));
        }
        pip(w);
    }

    private static void pip(ServerPlayer p) {
        p.playNotifySound(MainSounds.PIP_MENU.get(), SoundSource.PLAYERS, 0.7f, 1.3f);
    }

    private static List<ServerPlayer> everyone(MinecraftServer server) {
        List<ServerPlayer> all = new ArrayList<>(server.getPlayerList().getPlayers());
        for (ServerPlayer p : TEST_PLAYERS) if (!p.isRemoved() && !all.contains(p)) all.add(p);
        return all;
    }

    private static String refuse(ServerPlayer p, String code, String text) {
        p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.scouter." + code, text).withStyle(ChatFormatting.RED));
        return code;
    }

    private static String actionBar(ServerPlayer p, String code, String text) {
        p.displayClientMessage(Component.translatableWithFallback("message.dmzzero.scouter." + code, text).withStyle(ChatFormatting.GRAY), true);
        return code;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        WATCH.forget(id);
        HEARD.forget(id);
        PINGS.forget(id);
        LAST_HEARD.remove(id);
        LAST_ALERT.remove(id);
        LAST_PING.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WATCH.clear();
        HEARD.clear();
        PINGS.clear();
        TEST_PLAYERS.clear();
        LAST_HEARD.clear();
        LAST_ALERT.clear();
        LAST_PING.clear();
    }

    // ---- self-test hooks

    public static void addTestPlayer(ServerPlayer p) {
        TEST_PLAYERS.add(p);
    }

    public static void clearTest() {
        for (ServerPlayer p : TEST_PLAYERS) {
            WATCH.forget(p.getUUID());
            HEARD.forget(p.getUUID());
            PINGS.forget(p.getUUID());
            LAST_HEARD.remove(p.getUUID());
            LAST_ALERT.remove(p.getUUID());
            LAST_PING.remove(p.getUUID());
        }
        TEST_PLAYERS.clear();
    }

    public static void resetCooldowns(ServerPlayer p) {
        PINGS.forget(p.getUUID());
        HEARD.forget(p.getUUID());
        WATCH.forget(p.getUUID());
    }

    public static String lastHeard(ServerPlayer p) {
        return LAST_HEARD.get(p.getUUID());
    }

    public static String lastAlert(ServerPlayer p) {
        return LAST_ALERT.get(p.getUUID());
    }

    public static ScouterPingS2C lastPing(ServerPlayer p) {
        return LAST_PING.get(p.getUUID());
    }
}
