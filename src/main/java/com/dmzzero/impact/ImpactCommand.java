package com.dmzzero.impact;

import com.dmzzero.ZeroConfig;
import com.dragonminez.common.init.MainGameRules;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class ImpactCommand {

    private ImpactCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dmzzero")
                .then(Commands.literal("destruction")
                        .executes(ctx -> show(ctx.getSource()))
                        .then(Commands.literal("on").executes(ctx -> set(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> set(ctx.getSource(), false)))));
    }

    private static int show(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean on = !ImpactData.destructionOff(player);
        source.sendSuccess(() -> withNotes(player, Component.translatableWithFallback(
                on ? "command.dmzzero.destruction.on" : "command.dmzzero.destruction.off",
                on ? "Your aura and transformations break things around you. Turn it off with /dmzzero destruction off."
                        : "Your aura and transformations leave the world alone. Turn it on with /dmzzero destruction on.")), false);
        return on ? 1 : 0;
    }

    private static int set(CommandSourceStack source, boolean on) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ImpactData.setDestructionOff(player, !on);
        source.sendSuccess(() -> withNotes(player, Component.translatableWithFallback(
                on ? "command.dmzzero.destruction.set_on" : "command.dmzzero.destruction.set_off",
                on ? "Destruction on: glass shatters, plants blow away and strong forms crack the ground."
                        : "Destruction off: you only crack the ground for show.")), false);
        return on ? 1 : 0;
    }

    // tell the player when the server overrides their choice
    private static Component withNotes(ServerPlayer player, MutableComponent line) {
        if (ImpactData.destructionOff(player)) return line;
        if (!ZeroConfig.SERVER.destruction.get()) {
            line.append(Component.literal(" ")).append(Component.translatableWithFallback("command.dmzzero.destruction.server_off",
                    "(The server has destruction turned off.)").withStyle(ChatFormatting.GRAY));
        } else if (!player.level().getGameRules().getBoolean(MainGameRules.ALLOW_KI_GRIEFING_PLAYERS)) {
            line.append(Component.literal(" ")).append(Component.translatableWithFallback("command.dmzzero.destruction.gamerule_off",
                    "(The allowKiGriefingPlayers gamerule is off, so nothing breaks.)").withStyle(ChatFormatting.GRAY));
        }
        return line;
    }
}
