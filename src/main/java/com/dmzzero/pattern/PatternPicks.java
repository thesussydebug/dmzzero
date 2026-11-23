package com.dmzzero.pattern;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// the clothing pattern wish's picks: kept in PlayerPersisted.dmzzero.patternPicks until used, picked from a list in
// chat. the list and its buttons are commands, so anyone can type them: each pick is checked here
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class PatternPicks {
    public static final int PER_PAGE = 8;
    private static final String ROOT = "dmzzero";
    private static final String KEY = "patternPicks";
    private static List<Item> patterns;

    private PatternPicks() {
    }

    /** Every DMZ clothing pattern, by item id. The item registry doesn't change while the server runs. */
    public static List<Item> patterns() {
        List<Item> known = patterns;
        if (known != null) return known;
        List<Item> found = new ArrayList<>();
        for (var e : ForgeRegistries.ITEMS.getEntries()) {
            ResourceLocation id = e.getKey().location();
            if (PatternMath.isPattern(id.getNamespace(), id.getPath())) found.add(e.getValue());
        }
        found.sort(Comparator.comparing(i -> String.valueOf(ForgeRegistries.ITEMS.getKey(i))));
        patterns = List.copyOf(found);
        return patterns;
    }

    public static int picks(Player p) {
        return Math.max(0, p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ROOT).getInt(KEY));
    }

    public static void setPicks(Player p, int picks) {
        CompoundTag data = p.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        if (picks <= 0) root.remove(KEY);
        else root.putInt(KEY, Math.min(picks, PatternMath.MAX_PICKS));
        persisted.put(ROOT, root);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** The wish: adds the configured picks and shows the picker. The new number of picks. */
    public static int grant(ServerPlayer p) {
        int granted = ZeroConfig.SERVER.patternPicks.get();
        setPicks(p, PatternMath.addPicks(picks(p), granted));
        p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.pattern.granted",
                "Shenron grants you %s clothing patterns of your choice.", granted).withStyle(ChatFormatting.GOLD));
        p.sendSystemMessage(picker(p, 1));
        return picks(p);
    }

    /** Gives the pattern and uses a pick. "ok", or why not. */
    public static String pick(ServerPlayer p, ResourceLocation id) {
        if (!ZeroConfig.SERVER.patternWish.get()) return say(p, "off", "The clothing pattern wish is off on this server.");
        if (picks(p) <= 0) return say(p, "no_picks", "You have no clothing pattern picks. Shenron can grant some.");
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || !patterns().contains(item)) return say(p, "not_pattern", "That isn't a clothing pattern.");
        ItemStack stack = new ItemStack(item);
        Component name = stack.getHoverName();
        ItemHandlerHelper.giveItemToPlayer(p, stack);
        setPicks(p, picks(p) - 1);
        int left = picks(p);
        MutableComponent msg = Component.translatableWithFallback("message.dmzzero.pattern.picked", "You got %s. %s picks left.", name, left)
                .withStyle(ChatFormatting.GOLD);
        if (left > 0) msg.append(" ").append(button(Component.translatableWithFallback("message.dmzzero.pattern.another", "[Pick another]"),
                "/dmzzero pattern list 1", Component.translatableWithFallback("message.dmzzero.pattern.another_hover", "Show the patterns again")));
        p.sendSystemMessage(msg);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1f, 1f);
        return "ok";
    }

    /** One page of the picker: a line per pattern to click, then the page buttons. */
    public static Component picker(ServerPlayer p, int page) {
        List<Item> all = patterns();
        int pages = PatternMath.pages(all.size(), PER_PAGE);
        int shown = Math.max(1, Math.min(page, pages));
        MutableComponent out = Component.translatableWithFallback("message.dmzzero.pattern.header",
                "Pick a clothing pattern (%s left), page %s of %s:", picks(p), shown, pages).withStyle(ChatFormatting.YELLOW);
        for (Item item : PatternMath.page(all, shown, PER_PAGE)) {
            ItemStack stack = new ItemStack(item);
            out.append(Component.literal("\n  "));
            out.append(Component.translatable(item.getDescriptionId()).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dmzzero pattern pick " + ForgeRegistries.ITEMS.getKey(item)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack)))));
        }
        out.append(Component.literal("\n  "));
        if (shown > 1) out.append(button(Component.translatableWithFallback("message.dmzzero.pattern.previous", "[< Previous]"),
                "/dmzzero pattern list " + (shown - 1), Component.translatableWithFallback("message.dmzzero.pattern.page_hover", "Page %s", shown - 1))).append(" ");
        if (shown < pages) out.append(button(Component.translatableWithFallback("message.dmzzero.pattern.next", "[Next >]"),
                "/dmzzero pattern list " + (shown + 1), Component.translatableWithFallback("message.dmzzero.pattern.page_hover", "Page %s", shown + 1)));
        return out;
    }

    static int show(ServerPlayer p, int page) {
        if (!ZeroConfig.SERVER.patternWish.get()) {
            say(p, "off", "The clothing pattern wish is off on this server.");
            return 0;
        }
        if (picks(p) <= 0) {
            say(p, "no_picks", "You have no clothing pattern picks. Shenron can grant some.");
            return 0;
        }
        p.sendSystemMessage(picker(p, page));
        return 1;
    }

    private static Component button(Component text, String command, Component hover) {
        return text.copy().withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static String say(ServerPlayer p, String why, String english) {
        p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.pattern." + why, english).withStyle(ChatFormatting.GRAY));
        return why;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("dmzzero").then(Commands.literal("pattern")
                .executes(c -> show(c.getSource().getPlayerOrException(), 1))
                .then(Commands.literal("list").then(Commands.argument("page", IntegerArgumentType.integer(1, 1000))
                        .executes(c -> show(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "page")))))
                .then(Commands.literal("pick").then(Commands.argument("pattern", ResourceLocationArgument.id())
                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(patterns().stream().map(ForgeRegistries.ITEMS::getKey), b))
                        .executes(c -> "ok".equals(pick(c.getSource().getPlayerOrException(), ResourceLocationArgument.getId(c, "pattern"))) ? 1 : 0)))
                .then(Commands.literal("wish").requires(s -> s.hasPermission(2))
                        .executes(c -> ZeroConfig.SERVER.patternWish.get() ? grant(c.getSource().getPlayerOrException()) : 0))));
    }

    // picks left over from a wish wait for the player; a reminder when they come back
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !ZeroConfig.SERVER.patternWish.get()) return;
        int left = picks(p);
        if (left <= 0) return;
        p.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.pattern.reminder",
                "You still have %s clothing pattern picks from Shenron.", left).withStyle(ChatFormatting.GOLD).append(" ")
                .append(button(Component.translatableWithFallback("message.dmzzero.pattern.open", "[Pick]"), "/dmzzero pattern list 1",
                        Component.translatableWithFallback("message.dmzzero.pattern.another_hover", "Show the patterns again"))));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        patterns = null;
    }
}
