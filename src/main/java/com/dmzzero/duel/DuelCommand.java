package com.dmzzero.duel;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// /dmzzero duel <player>, accept/decline <player>, off/on, record [player]
public final class DuelCommand {

    private DuelCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dmzzero")
                .then(Commands.literal("duel")
                        .then(Commands.literal("accept").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> ok(Duels.accept(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"))))))
                        .then(Commands.literal("decline").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> ok(Duels.decline(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"))))))
                        .then(Commands.literal("off")
                                .executes(ctx -> ok(Duels.setOptOut(ctx.getSource().getPlayerOrException(), true))))
                        .then(Commands.literal("on")
                                .executes(ctx -> ok(Duels.setOptOut(ctx.getSource().getPlayerOrException(), false))))
                        .then(Commands.literal("record")
                                .executes(ctx -> record(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> record(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> ok(Duels.challenge(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static int ok(String result) {
        return "ok".equals(result) || "declined".equals(result) ? 1 : 0;
    }

    private static int record(CommandSourceStack src, ServerPlayer player) {
        int[] s = Duels.score(player);
        boolean self = src.getEntity() == player;
        src.sendSuccess(() -> self
                ? Component.translatableWithFallback("command.dmzzero.duel.record", "Duels: %s won, %s lost, %s drawn.", s[0], s[1], s[2])
                : Component.translatableWithFallback("command.dmzzero.duel.record_other", "%s's duels: %s won, %s lost, %s drawn.",
                player.getGameProfile().getName(), s[0], s[1], s[2]), false);
        return 1;
    }
}
