package com.dmzzero.realm;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// /dmzzero battle open|join|start|leave|record [player]
public final class BattleCommand {

    private BattleCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dmzzero")
                .then(Commands.literal("battle")
                        .then(Commands.literal("open").executes(ctx -> ok(Battles.open(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("join").executes(ctx -> ok(Battles.join(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("start").executes(ctx -> ok(Battles.startNow(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("leave").executes(ctx -> ok(Battles.leave(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("record")
                                .executes(ctx -> record(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> record(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static int ok(String result) {
        return "ok".equals(result) ? 1 : 0;
    }

    private static int record(CommandSourceStack src, ServerPlayer player) {
        int[] s = Battles.score(player);
        boolean self = src.getEntity() == player;
        src.sendSuccess(() -> self
                ? Component.translatableWithFallback("command.dmzzero.battle.record", "Null Realm: %s battles, %s won, %s knockouts.", s[0], s[1], s[2])
                : Component.translatableWithFallback("command.dmzzero.battle.record_other", "%s in the Null Realm: %s battles, %s won, %s knockouts.",
                player.getGameProfile().getName(), s[0], s[1], s[2]), false);
        return 1;
    }
}
