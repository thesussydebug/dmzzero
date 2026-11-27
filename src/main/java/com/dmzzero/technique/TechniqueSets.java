package com.dmzzero.technique;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.kishare.TossMath;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.ReviveTechniqueData;
import com.dragonminez.common.stats.techniques.Techniques;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

// named sets of what's in your technique slots: /dmzzero techniques, or the key that sends "next". a set follows dmz's own
// equip rules (unlocked, allowed for the race, the revive and form slots stay) and overhaul's limits
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class TechniqueSets {
    private static final String K = "message.dmzzero.techniques.";
    private static final TossMath.Cooldowns WAITS = new TossMath.Cooldowns();

    private TechniqueSets() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dmzzero").then(Commands.literal("techniques")
                .executes(ctx -> list(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("next").executes(ctx -> code(next(ctx.getSource().getPlayerOrException()), "used")))
                .then(Commands.literal("save").then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> code(save(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "name")), "saved"))))
                .then(named("use", "used", TechniqueSets::use))
                .then(named("delete", "deleted", TechniqueSets::delete))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> named(String literal, String done, BiFunction<ServerPlayer, String, String> run) {
        RequiredArgumentBuilder<CommandSourceStack, String> name = Commands.argument("name", StringArgumentType.word());
        name.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TechniqueSetMath.names(persisted(ctx.getSource().getPlayerOrException())), builder));
        name.executes(ctx -> code(run.apply(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "name")), done));
        return Commands.literal(literal).then(name);
    }

    private static int code(String result, String done) {
        return done.equals(result) ? 1 : 0;
    }

    private static int list(ServerPlayer player) {
        List<String> names = TechniqueSetMath.names(persisted(player));
        if (!ZeroConfig.SERVER.techniqueSets.get()) say(player, false, "off", "Technique sets are turned off on this server.");
        else if (names.isEmpty()) say(player, false, "none", "No technique sets yet. Equip your techniques, then /dmzzero techniques save <name>.");
        else say(player, false, "list", "Technique sets: %s.", String.join(", ", names));
        return 1;
    }

    /** Saves what's in the slots now. Returns "saved", or why not: off, cannot, form, name, empty, full. */
    public static String save(ServerPlayer player, String name) {
        StatsData data = character(player);
        if (!ZeroConfig.SERVER.techniqueSets.get()) return refuse(player, false, "off", "Technique sets are turned off on this server.");
        if (data == null) return refuse(player, false, "cannot", "You can't do that right now.");
        Techniques techniques = data.getTechniques();
        if (techniques.isFormLoadoutActive()) return refuse(player, false, "form", "Your form holds its own techniques. Try again once it ends.");
        if (name.length() > TechniqueSetMath.MAX_NAME) return refuse(player, false, "name", "A set's name is up to %s characters.", TechniqueSetMath.MAX_NAME);
        String[] slots = techniques.getEquippedSlots();
        if (Arrays.stream(slots).allMatch(id -> id == null || id.isEmpty())) return refuse(player, false, "empty", "Equip a technique first.");
        CompoundTag persisted = persisted(player);
        int max = ZeroConfig.SERVER.techniqueSetMax.get();
        if (!TechniqueSetMath.put(persisted, name, slots, max)) return refuse(player, false, "full", "You can keep %s sets. Delete one first.", max);
        TechniqueSetMath.used(persisted, name);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        say(player, false, "saved", "Saved your technique set as %s.", name);
        return "saved";
    }

    /** Switches to a saved set. Returns "used", or why not: off, cannot, form, charging, unknown, wait. */
    public static String use(ServerPlayer player, String name) {
        StatsData data = character(player);
        if (!ZeroConfig.SERVER.techniqueSets.get()) return refuse(player, true, "off", "Technique sets are turned off on this server.");
        if (data == null) return refuse(player, true, "cannot", "You can't do that right now.");
        Techniques techniques = data.getTechniques();
        if (techniques.isFormLoadoutActive()) return refuse(player, true, "form", "Your form holds its own techniques. Try again once it ends.");
        if (techniques.isTechniqueCharging() || techniques.isTechniqueChargeActive()) return refuse(player, true, "charging", "Finish your technique first.");
        CompoundTag persisted = persisted(player);
        String[] wanted = TechniqueSetMath.slots(persisted, name);
        if (wanted == null) return refuse(player, true, "unknown", "You have no technique set called %s.", name);
        long now = player.server.getTickCount();
        if (WAITS.remaining(player.getUUID(), now) > 0) return refuse(player, true, "wait", "Wait a moment before switching again.");
        int missed = equip(data, wanted);
        WAITS.start(player.getUUID(), now, ZeroConfig.SERVER.techniqueSetSeconds.get() * 20L);
        TechniqueSetMath.used(persisted, name);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        // what dmz sends after its own equip
        NetworkHandler.sendToPlayer(new ProgressionSyncS2C(player), player);
        if (missed == 0) say(player, true, "used", "Technique set: %s", name);
        else say(player, true, "used_some", "Technique set: %s (%s couldn't be equipped)", name, missed);
        return "used";
    }

    /** The set after the one last used or saved. Returns what use does, or "none" with no sets saved. */
    public static String next(ServerPlayer player) {
        String name = TechniqueSetMath.next(persisted(player));
        if (name != null) return use(player, name);
        if (!ZeroConfig.SERVER.techniqueSets.get()) return refuse(player, true, "off", "Technique sets are turned off on this server.");
        return refuse(player, true, "none", "No technique sets yet. Equip your techniques, then /dmzzero techniques save <name>.");
    }

    /** Returns "deleted", or "unknown". Allowed with the feature off, so nobody is stuck with their sets. */
    public static String delete(ServerPlayer player, String name) {
        CompoundTag persisted = persisted(player);
        String stored = TechniqueSetMath.remove(persisted, name);
        if (stored == null) return refuse(player, false, "unknown", "You have no technique set called %s.", name);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        say(player, false, "deleted", "Deleted the technique set %s.", stored);
        return "deleted";
    }

    private static int equip(StatsData data, String[] wanted) {
        Techniques techniques = data.getTechniques();
        // dmz's live array: its own removeTechnique writes it the same way, and its equip method can't leave a slot empty
        String[] slots = techniques.getEquippedSlots();
        boolean[] fixed = new boolean[slots.length];
        for (int i = 0; i < slots.length; i++) fixed[i] = ReviveTechniqueData.isRevive(slots[i]);
        String race = data.getCharacter().getRaceName();
        boolean android = data.getStatus().isAndroidUpgraded();
        return TechniqueSetMath.apply(slots, wanted, fixed, (slot, id) -> techniques.getUnlockedTechniques().containsKey(id)
                && !ReviveTechniqueData.isRevive(id)
                && ConfigManager.getSkillsConfig().isSkillAllowedForRace(id, race, android)
                && !OverhaulEquipRules.blocks(techniques, slot, id));
    }

    // alive, playing and with a dmz character
    private static StatsData character(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return null;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        return data != null && data.getStatus().isHasCreatedCharacter() ? data : null;
    }

    private static CompoundTag persisted(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    // the key's answers go over the hotbar, a command's into chat
    private static void say(ServerPlayer player, boolean overHotbar, String key, String english, Object... args) {
        MutableComponent text = Component.translatableWithFallback(K + key, english, args);
        player.displayClientMessage(text.withStyle(ChatFormatting.GRAY), overHotbar);
    }

    private static String refuse(ServerPlayer player, boolean overHotbar, String code, String english, Object... args) {
        MutableComponent text = Component.translatableWithFallback(K + code, english, args);
        player.displayClientMessage(text.withStyle(ChatFormatting.RED), overHotbar);
        return code;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WAITS.clear();
    }
}
