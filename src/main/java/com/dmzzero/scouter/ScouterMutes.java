package com.dmzzero.scouter;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// whose scouter talk and pings a player doesn't want to hear, kept with their character (uuid to name). its own command and
// not /sc mute: /sc takes any text, and a "mute" subcommand would swallow messages that start with that word
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class ScouterMutes {
    public static final int MAX = 64;
    private static final String ROOT = "dmzzero";
    private static final String MUTES = "scouterMutes";

    private ScouterMutes() {
    }

    public static boolean has(CompoundTag persisted, UUID id) {
        return list(persisted).contains(id.toString());
    }

    /** False when the list is full. */
    public static boolean add(CompoundTag persisted, UUID id, String name) {
        CompoundTag mutes = list(persisted);
        if (mutes.size() >= MAX && !mutes.contains(id.toString())) return false;
        mutes.putString(id.toString(), name);
        store(persisted, mutes);
        return true;
    }

    /** Unmutes by name, so someone who has logged off can be unmuted too. Returns their name, or null if not muted. */
    public static String remove(CompoundTag persisted, String name) {
        CompoundTag mutes = list(persisted);
        for (String key : mutes.getAllKeys()) {
            String stored = mutes.getString(key);
            if (!stored.equalsIgnoreCase(name)) continue;
            mutes.remove(key);
            store(persisted, mutes);
            return stored;
        }
        return null;
    }

    public static List<String> names(CompoundTag persisted) {
        CompoundTag mutes = list(persisted);
        List<String> out = new ArrayList<>();
        for (String key : mutes.getAllKeys()) out.add(mutes.getString(key));
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static CompoundTag list(CompoundTag persisted) {
        return persisted.getCompound(ROOT).getCompound(MUTES);
    }

    private static void store(CompoundTag persisted, CompoundTag mutes) {
        CompoundTag root = persisted.getCompound(ROOT);
        if (mutes.isEmpty()) root.remove(MUTES);
        else root.put(MUTES, mutes);
        persisted.put(ROOT, root);
    }

    /** Whether this listener has muted this talker on a server that allows it. */
    public static boolean muted(ServerPlayer listener, ServerPlayer talker) {
        return ZeroConfig.SERVER.scouterMute.get() && has(listener.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG), talker.getUUID());
    }

    public static String mute(ServerPlayer listener, ServerPlayer talker) {
        if (!ZeroConfig.SERVER.scouterMute.get()) return refuse(listener, "mute_disabled", "This server doesn't allow scouter mutes.");
        if (listener == talker) return refuse(listener, "mute_self", "You can't mute yourself.");
        String name = talker.getGameProfile().getName();
        CompoundTag persisted = listener.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!add(persisted, talker.getUUID(), name)) return refuse(listener, "mute_full", "Your mute list is full (%s). Unmute someone first.", MAX);
        listener.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        say(listener, "now_muted", "You won't hear %s on your scouter.", name);
        return "ok";
    }

    public static String unmute(ServerPlayer listener, String name) {
        CompoundTag persisted = listener.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        String stored = remove(persisted, name);
        if (stored == null) return refuse(listener, "not_muted", "%s isn't muted.", name);
        listener.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        say(listener, "unmuted", "You'll hear %s on your scouter again.", stored);
        return "ok";
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dmzzero")
                .then(Commands.literal("scouter")
                        .then(Commands.literal("mute").then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder))
                                .executes(ctx -> muteByName(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("unmute").then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        names(ctx.getSource().getPlayerOrException().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)), builder))
                                .executes(ctx -> ok(unmute(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "player"))))))
                        .then(Commands.literal("muted")
                                .executes(ctx -> showList(ctx.getSource().getPlayerOrException())))));
    }

    // online players only: looking an unknown name up would ask mojang from the server thread
    private static int muteByName(ServerPlayer listener, String name) {
        ServerPlayer talker = listener.server.getPlayerList().getPlayerByName(name);
        if (talker == null) return ok(refuse(listener, "mute_offline", "%s isn't online.", name));
        return ok(mute(listener, talker));
    }

    private static int showList(ServerPlayer listener) {
        List<String> muted = names(listener.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG));
        if (muted.isEmpty()) say(listener, "muted_none", "You haven't muted anyone.");
        else say(listener, "muted_list", "Muted on your scouter: %s.", String.join(", ", muted));
        return 1;
    }

    private static int ok(String result) {
        return "ok".equals(result) ? 1 : 0;
    }

    private static void say(ServerPlayer p, String key, String english, Object... args) {
        p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.scouter." + key, english, args).withStyle(ChatFormatting.GRAY));
    }

    private static String refuse(ServerPlayer p, String code, String english, Object... args) {
        p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.scouter." + code, english, args).withStyle(ChatFormatting.RED));
        return code;
    }
}
