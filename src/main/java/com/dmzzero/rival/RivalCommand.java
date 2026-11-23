package com.dmzzero.rival;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// /dmzzero rival, /dmzzero rival clear, /dmzzero rival shadow, /dmzzero rival <player> (op)
public final class RivalCommand {

    private RivalCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dmzzero")
                .then(Commands.literal("rival")
                        .executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clear(ctx.getSource(), ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("shadow")
                                .executes(ctx -> "ok".equals(RivalShadows.summon(ctx.getSource().getPlayerOrException())) ? 1 : 0))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                                .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static int show(CommandSourceStack src, ServerPlayer player) throws CommandSyntaxException {
        CompoundTag persisted = RivalData.persisted(player);
        boolean self = src.getEntity() == player;
        if (RivalData.rival(persisted) == null) {
            src.sendSuccess(() -> self
                    ? Component.translatableWithFallback("command.dmzzero.rival.none", "You have no rival.")
                    : Component.translatableWithFallback("command.dmzzero.rival.none_other", "%s has no rival.", player.getGameProfile().getName()), false);
            return 0;
        }
        Component name = Component.literal(RivalData.name(persisted)).withStyle(ChatFormatting.BOLD);
        Component score = Rivals.score(persisted);
        src.sendSuccess(() -> self
                ? Component.translatableWithFallback("command.dmzzero.rival.show", "Your rival is %s (%s).", name, score)
                : Component.translatableWithFallback("command.dmzzero.rival.show_other", "The rival of %s is %s (%s).",
                player.getGameProfile().getName(), name, score), false);
        return 1;
    }

    private static int clear(CommandSourceStack src, ServerPlayer player) {
        CompoundTag persisted = RivalData.persisted(player);
        RivalData.clear(persisted);
        RivalData.save(player, persisted);
        src.sendSuccess(() -> Component.translatableWithFallback("command.dmzzero.rival.cleared", "Rivalry ended."), false);
        return 1;
    }
}
