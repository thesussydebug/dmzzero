package com.dmzzero.garden;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.scouter.Scouters;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

// /dmzzero garden: where your senzu bushes, saibaman seeds and trees of might are and how long they have to go
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class GardenCommand {
    private static final String K = "command.dmzzero.garden.";

    private GardenCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dmzzero")
                .then(Commands.literal("garden").executes(ctx -> show(ctx.getSource().getPlayerOrException()))));
    }

    private static int show(ServerPlayer player) {
        if (!ZeroConfig.SERVER.gardenList.get()) {
            player.sendSystemMessage(line("disabled", "The garden list is turned off on this server.").withStyle(ChatFormatting.RED));
            return 0;
        }
        List<Component> growing = growing(player);
        if (growing.isEmpty()) {
            player.sendSystemMessage(line("none", "Nothing of yours is growing."));
        } else {
            player.sendSystemMessage(line("header", "Growing for you:").withStyle(ChatFormatting.GOLD));
            growing.forEach(player::sendSystemMessage);
        }
        return 1;
    }

    /** One line for each thing this player has growing, in every dimension. */
    public static List<Component> growing(ServerPlayer player) {
        return Growth.of(player).stream().map(GardenCommand::describe).toList();
    }

    /** "12 64 -30", as a position reads in chat. */
    static String at(long pos) {
        BlockPos p = BlockPos.of(pos);
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    // a seed climbs out, and a tree grows and bears fruit, only while someone is there, so each can be overdue
    private static Component describe(Growth g) {
        String at = at(g.pos()), world = Scouters.worldName(g.level());
        long left = g.ticksLeft(), hours = SenzuMath.hours(left);
        return switch (g.kind()) {
            case BUSH -> left == 0
                    ? line("bush_ripe", "Senzu bush at %s in %s: ripe.", at, world)
                    : line("bush", "Senzu bush at %s in %s: ripe in about %s in-game hours.", at, world, hours);
            case SPROUT -> left == 0
                    ? line("sprout_ready", "Saibaman seed at %s in %s: ready, and waiting for someone to come near.", at, world)
                    : line("sprout", "Saibaman seed at %s in %s: climbs out in %s seconds.", at, world, (left + 19) / 20);
            case TREE -> left == 0
                    ? line("tree_due", "Tree of Might at %s in %s: finishes growing when someone is near.", at, world)
                    : line("tree", "Tree of Might at %s in %s: grown in about %s in-game hours.", at, world, hours);
            case FRUIT -> left == 0
                    ? line("tree_fruit_due", "Tree of Might at %s in %s: grown, with a fruit due.", at, world)
                    : line("tree_fruit", "Tree of Might at %s in %s: grown, next fruit in about %s in-game hours.", at, world, hours);
        };
    }

    private static MutableComponent line(String key, String english, Object... args) {
        return Component.translatableWithFallback(K + key, english, args);
    }
}
