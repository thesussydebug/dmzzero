package com.dmzzero.potential;

import com.dmzzero.DMZZero;
import com.dmzzero.network.ZeroNetwork;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class PotentialService {
    private static final ResourceLocation DMZ_FONT = new ResourceLocation("dragonminez", "smooth");

    private PotentialService() {
    }

    public static boolean hasCharacter(ServerPlayer player) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        return data != null && data.getStatus().isHasCreatedCharacter();
    }

    public static void ensureRolled(ServerPlayer player) {
        if (PotentialData.of(player) != 0 || !hasCharacter(player)) return;
        int potential = Potential.roll(player.getRandom().nextDouble());
        PotentialData.store(player, potential);
        ZeroNetwork.sendPotential(player, potential);
        DMZZero.LOG.info("{} rolled Potential {}", player.getGameProfile().getName(), potential);
        player.sendSystemMessage(Component.translatableWithFallback(
                "message.dmzzero.potential.rolled", "Your Potential is %s.", number(potential)));
    }

    public static void set(ServerPlayer player, int potential) {
        PotentialData.store(player, potential);
        ZeroNetwork.sendPotential(player, potential);
        DMZZero.LOG.info("Potential of {} set to {}", player.getGameProfile().getName(), potential);
    }

    public static void sync(ServerPlayer player) {
        ZeroNetwork.sendPotential(player, PotentialData.of(player));
    }

    public static Component tooltipLine(int potential) {
        return Component.translatableWithFallback("gui.dmzzero.character_stats.tp_multiplier.potential", "Potential %s: x%s",
                        potential, times(Potential.tpMultiplier(potential)))
                .withStyle(Style.EMPTY.withFont(DMZ_FONT).withColor(color(potential)));
    }

    public static MutableComponent number(int potential) {
        return Component.literal(Integer.toString(potential)).withStyle(color(potential), ChatFormatting.BOLD);
    }

    public static ChatFormatting color(int potential) {
        return switch (potential) {
            case 2 -> ChatFormatting.GREEN;
            case 3 -> ChatFormatting.AQUA;
            case 4 -> ChatFormatting.LIGHT_PURPLE;
            case 5 -> ChatFormatting.GOLD;
            default -> ChatFormatting.GRAY;
        };
    }

    public static String times(double multiplier) {
        String text = String.format(Locale.US, "%.2f", multiplier);
        if (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        return text;
    }
}
