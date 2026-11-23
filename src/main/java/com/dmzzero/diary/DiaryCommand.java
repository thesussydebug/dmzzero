package com.dmzzero.diary;

import com.dmzzero.ZeroConfig;
import com.dmzzero.diary.DiaryMath.Days;
import com.dmzzero.diary.DiaryMath.Kind;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// /dmzzero today: the tp you earned today and yesterday, by where it came from. Ops can read anyone's
public final class DiaryCommand {

    private DiaryCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dmzzero")
                .then(Commands.literal("today")
                        .executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException(), true))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), false)))));
    }

    private static int show(CommandSourceStack source, ServerPlayer player, boolean self) {
        if (!ZeroConfig.SERVER.diary.get()) {
            source.sendFailure(Component.translatableWithFallback("command.dmzzero.today.off", "The training diary is off on this server."));
            return 0;
        }
        List<Component> lines = lines(Diaries.view(player, Diaries.today()), self ? null : player);
        for (Component line : lines) source.sendSuccess(() -> line, false);
        return (int) Math.min(Integer.MAX_VALUE, DiaryMath.total(Diaries.view(player, Diaries.today()).today()));
    }

    /** The diary as chat lines. {@code other} names whose diary it is, or null for your own. */
    public static List<Component> lines(Days days, ServerPlayer other) {
        long[] today = days.today();
        long[] yesterday = days.yesterday();
        List<Component> out = new ArrayList<>();
        long total = DiaryMath.total(today);
        long before = DiaryMath.total(yesterday);
        if (total == 0 && before == 0) {
            out.add(other == null
                    ? Component.translatableWithFallback("command.dmzzero.today.empty", "Nothing in your training diary for today or yesterday yet.")
                    : Component.translatableWithFallback("command.dmzzero.today.empty_other", "Nothing in %s's training diary for today or yesterday yet.", other.getDisplayName()));
            return out;
        }
        out.add(other == null
                ? Component.translatableWithFallback("command.dmzzero.today.self", "Your training today: %s TP (yesterday %s)", number(total), number(before))
                : Component.translatableWithFallback("command.dmzzero.today.other", "%s's training today: %s TP (yesterday %s)", other.getDisplayName(), number(total), number(before)));
        for (Kind k : Kind.values()) {
            long t = today[k.ordinal()];
            long y = yesterday[k.ordinal()];
            if (t == 0 && y == 0) continue;
            MutableComponent line = Component.translatableWithFallback("command.dmzzero.today.line", "  %s: %s (yesterday %s)", name(k), number(t), number(y));
            out.add(line.withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    public static Component name(Kind k) {
        return Component.translatableWithFallback("dmzzero.diary." + k.key(), switch (k) {
            case FIGHTS -> "Fights";
            case TRAINING -> "Training";
            case CHAMBER -> "Time Chamber";
            case EVERYDAY -> "Everyday (travel, mining, crafting)";
            case QUESTS -> "Quests and wishes";
            case PARTY -> "Shared by your party";
            case OTHER -> "Other";
        });
    }

    static String number(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }
}
