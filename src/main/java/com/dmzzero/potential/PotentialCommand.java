package com.dmzzero.potential;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PotentialCommand {

    private PotentialCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dmzzero")
                .then(Commands.literal("potential")
                        .executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException(), true))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), false))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(Potential.MIN, Potential.MAX))
                                                .executes(ctx -> set(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "value"))))))));
    }

    private static int show(CommandSourceStack source, ServerPlayer player, boolean self) throws CommandSyntaxException {
        int potential = PotentialData.of(player);
        if (potential == 0) {
            source.sendFailure(Component.translatableWithFallback("command.dmzzero.potential.none",
                    "%s has no Potential yet. It is rolled when the character is made.", player.getDisplayName()));
            return 0;
        }
        String tp = PotentialService.times(Potential.tpMultiplier(potential));
        String mastery = PotentialService.times(Potential.masteryMultiplier(potential));
        Component line = self
                ? Component.translatableWithFallback("command.dmzzero.potential.self",
                        "Your Potential: %s (TP x%s, form mastery x%s)", PotentialService.number(potential), tp, mastery)
                : Component.translatableWithFallback("command.dmzzero.potential.other",
                        "%s's Potential: %s (TP x%s, form mastery x%s)", player.getDisplayName(), PotentialService.number(potential), tp, mastery);
        source.sendSuccess(() -> line, false);
        return potential;
    }

    private static int set(CommandSourceStack source, ServerPlayer player, int potential) {
        PotentialService.set(player, potential);
        source.sendSuccess(() -> Component.translatableWithFallback("command.dmzzero.potential.set",
                "Set %s's Potential to %s.", player.getDisplayName(), PotentialService.number(potential)), true);
        return potential;
    }
}
