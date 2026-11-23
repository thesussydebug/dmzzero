package com.dmzzero.status;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.settings.SettingsText;
import com.dmzzero.settings.SettingsTree;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// /dmzzero status, for server owners: a six-line summary, then players, settings and ticks list everything in full.
// who may run any of them is [server.status] permission in server.toml
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class StatusCommand {
    private static volatile Integer testPermission;

    private StatusCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dmzzero")
                .then(Commands.literal("status")
                        .requires(src -> src.hasPermission(permission()))
                        .executes(ctx -> send(ctx, src -> summary(src.getServer(), src.isPlayer())))
                        .then(Commands.literal("players").executes(ctx -> send(ctx, src -> players(src.getServer()))))
                        .then(Commands.literal("settings").executes(ctx -> send(ctx, src -> settings())))
                        .then(Commands.literal("ticks").executes(ctx -> send(ctx, src -> ticks(src.getServer()))))));
    }

    /** [server.status] permission, unless the self-test has set it in memory. */
    public static int permission() {
        Integer test = testPermission;
        return test != null ? test : ZeroConfig.SERVER.statusPermission.get();
    }

    /** Self-test only: a permission level without writing server.toml. Null clears it. */
    public static void setTestPermission(Integer level) {
        testPermission = level;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        testPermission = null;
    }

    private static int send(CommandContext<CommandSourceStack> ctx, Function<CommandSourceStack, List<Component>> page) {
        CommandSourceStack src = ctx.getSource();
        for (Component line : page.apply(src)) src.sendSuccess(() -> line, false);
        return 1;
    }

    /** The summary. In chat each line ends in a link to its full page; in the console, in the command to type. */
    public static List<Component> summary(MinecraftServer server, boolean chat) {
        List<Component> out = new ArrayList<>();
        out.add(t("title", "DMZ Zero %s, network protocol %s", version(DMZZero.MOD_ID), ZeroNetwork.protocol()).withStyle(ChatFormatting.GOLD));
        out.add(t("mods", "Mods: DragonMineZ %s, Sparking %s, Overhaul %s", version("dragonminez"), version("dmzsparking"), version("dmzrevamp")));
        out.add(hooks());
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        long withMod = players.stream().filter(ZeroNetwork::reachable).count();
        out.add(t("players", "Players: %s online, %s with DMZ Zero", players.size(), withMod).append(page("players", chat)));
        SettingsSummary s = settingsSummary();
        out.add(t("settings", "Settings: %s of %s switches off, %s changed", s.off().size(), s.switches(), s.changed().size())
                .append(page("settings", chat)));
        out.add(tickTime(server).append(page("ticks", chat)));
        return out;
    }

    /** Who is online, split by whether they have DMZ Zero installed. */
    public static List<Component> players(MinecraftServer server) {
        List<ServerPlayer> all = server.getPlayerList().getPlayers();
        if (all.isEmpty()) return List.of(t("noPlayers", "No players online"));
        List<String> with = new ArrayList<>(), without = new ArrayList<>();
        for (ServerPlayer p : all) (ZeroNetwork.reachable(p) ? with : without).add(p.getGameProfile().getName());
        return List.of(t("with", "With DMZ Zero (%s): %s", with.size(), names(with)),
                t("without", "Without DMZ Zero (%s): %s", without.size(), names(without)));
    }

    /** Every switch that is off, then every setting that differs from its default, by its path in server.toml. */
    public static List<Component> settings() {
        SettingsSummary s = settingsSummary();
        List<Component> out = new ArrayList<>();
        out.add(t("off", "Switches off (%s of %s): %s", s.off().size(), s.switches(), names(s.off())));
        if (s.changed().isEmpty()) {
            out.add(t("changedNone", "Changed from default: none"));
            return out;
        }
        out.add(t("changed", "Changed from default (%s):", s.changed().size()));
        for (SettingsSummary.Changed c : s.changed()) {
            out.add(t("changedItem", "  %s = %s (default %s)", c.path(), c.value(), c.fallback()));
        }
        return out;
    }

    /**
     * The tick time, then every feature that took any, slowest first, with what it keeps in memory. Features that
     * keep something in memory but took no time in this window come last.
     */
    public static List<Component> ticks(MinecraftServer server) {
        List<Component> out = new ArrayList<>();
        out.add(tickTime(server));
        Map<String, Integer> held = TickCost.held();
        Set<String> listed = new HashSet<>();
        if (TickCost.timing()) {
            for (TickWindow.Part p : TickCost.report().parts()) {
                listed.add(p.name());
                Integer count = held.get(p.name());
                out.add(count == null ? t("part", "  %s: %s ms", label(p.name()), StatusText.ms(p.ms()))
                        : t("partHeld", "  %s: %s ms, %s in memory", label(p.name()), StatusText.ms(p.ms()), count));
            }
        }
        held.forEach((name, count) -> {
            if (count > 0 && !listed.contains(name)) out.add(t("heldOnly", "  %s: %s in memory", label(name), count));
        });
        return out;
    }

    private static MutableComponent hooks() {
        List<String> missed = HookPlugin.missed();
        MutableComponent line = t("hooks", "Mixin hooks: %s of %s found their target, in %s mixins", HookPlugin.bound(), HookPlugin.hooks(), HookPlugin.applied());
        if (missed.isEmpty()) return line;
        return line.append(t("missed", ". Found nothing: %s", String.join(", ", missed))).withStyle(ChatFormatting.RED);
    }

    private static MutableComponent tickTime(MinecraftServer server) {
        if (!TickCost.timing()) return t("timingOff", "Tick timing: off ([server.status] timing)");
        TickWindow.Report r = TickCost.report();
        if (r.ticks() == 0) return t("noTicks", "Tick timing: nothing recorded yet");
        double whole = server.getAverageTickTime();
        return t("ticks", "Tick time, last %s s: %s ms of the server's %s ms (%s)",
                StatusText.seconds(r.ticks()), StatusText.ms(r.totalMs()), StatusText.ms(whole), StatusText.percent(r.totalMs(), whole));
    }

    private static Component page(String name, boolean chat) {
        String command = "/dmzzero status " + name;
        if (!chat) return Component.literal(" (" + command + ")");
        return Component.literal(" [" + name + "]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    private static SettingsSummary settingsSummary() {
        return SettingsSummary.of(SettingsTree.of(ZeroConfig.SERVER_SPEC));
    }

    private static Component names(List<String> names) {
        return names.isEmpty() ? t("none", "none") : Component.literal(StatusText.names(names));
    }

    private static Component label(String section) {
        return Component.translatableWithFallback("dmzzero.config." + section, SettingsText.label(section));
    }

    private static MutableComponent t(String key, String english, Object... args) {
        return Component.translatableWithFallback("command.dmzzero.status." + key, english, args);
    }

    private static Component version(String mod) {
        return ModList.get().getModContainerById(mod).<Component>map(c -> Component.literal(c.getModInfo().getVersion().toString()))
                .orElseGet(() -> t("notInstalled", "not installed"));
    }
}
