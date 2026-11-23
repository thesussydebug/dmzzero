package com.dmzzero.potential;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// a saiyan's zenkai grows with their potential, in dmz's zenkai and in overhaul's
public final class ZenkaiPotential {
    private static volatile double lastMultiplier = 1.0;
    private static volatile int scaled;

    private ZenkaiPotential() {
    }

    public static double scale(StatsData data, double base, String source) {
        if (data == null || !(data.getPlayer() instanceof ServerPlayer player)) return base;
        int potential = PotentialData.of(player);
        double multiplier = Potential.zenkaiMultiplier(potential, ZeroConfig.read(ZeroConfig.SERVER.zenkaiPerLevel, 1.0));
        lastMultiplier = multiplier;
        scaled++;
        if (multiplier <= 1.0) return base;
        DMZZero.LOG.debug("{} zenkai of {} x{} (Potential {})", source, player.getGameProfile().getName(), multiplier, potential);
        player.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.zenkai.potential",
                "Your Potential %s strengthens the Zenkai (x%s).", PotentialService.number(potential),
                PotentialService.times(multiplier)).withStyle(ChatFormatting.GOLD));
        return base * multiplier;
    }

    public static double lastMultiplier() {
        return lastMultiplier;
    }

    public static int scaledCount() {
        return scaled;
    }
}
