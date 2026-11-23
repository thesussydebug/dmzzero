package com.dmzzero.impact;

import com.dmzzero.Titles;
import com.dmzzero.ZeroConfig;
import com.dmzzero.network.ImpactPulseS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Locale;

// the first time a character reaches a form: its name on screen, a bigger jolt, and word to everyone nearby
public final class FirstMoment {
    // the burst's visual reach; the chat line uses [server.messages] newsRange
    static final double PULSE_RANGE = 64;

    private FirstMoment() {
    }

    static void celebrate(ServerPlayer player, StatsData data, String group, String form, boolean stack, ImpactProfile p) {
        String race = data.getCharacter().getRaceName();
        int color = color(ImpactLookup.formData(race, group, form, stack));
        MutableComponent name = formName(race, group, form, stack)
                .withStyle(s -> s.withColor(TextColor.fromRgb(color)).withBold(true));
        Titles.show(player, name, Component.translatableWithFallback("title.dmzzero.first_form", "First transformation")
                .withStyle(ChatFormatting.GRAY), 5, 50, 25);
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.0f);

        ServerLevel level = player.serverLevel();
        Component news = Component.translatableWithFallback("message.dmzzero.first_form.nearby", "%s reached %s for the first time!",
                player.getDisplayName(), formName(race, group, form, stack).withStyle(s -> s.withColor(TextColor.fromRgb(color))));
        double newsRange = ZeroConfig.SERVER.newsRange.get();
        for (ServerPlayer other : level.players()) {
            if (other != player && other.distanceTo(player) <= newsRange) other.displayClientMessage(news, true);
        }
        ZeroNetwork.sendAround(level, player.position(), PULSE_RANGE, new ImpactPulseS2C(player.getX(), player.getY() + 1, player.getZ(),
                1.2f + 0.4f * p.tier(), 48f, washed(color), 0.45f, 0.12f));
    }

    public static MutableComponent formName(String race, String group, String form, boolean stack) {
        String key = stack ? "race.dragonminez.stack.form." + group + "." + form
                : "race.dragonminez." + race + ".form." + group + "." + form;
        return Component.translatableWithFallback(key, pretty(form));
    }

    static int color(FormConfig.FormData form) {
        String hex = form == null ? null : form.getAuraColor();
        if (hex == null || hex.isEmpty()) return 0xFFFFFF;
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    // the aura color washed toward white, for flashes
    static int washed(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        r = (int) (0.4 * r + 0.6 * 255);
        g = (int) (0.4 * g + 0.6 * 255);
        b = (int) (0.4 * b + 0.6 * 255);
        return (r << 16) | (g << 8) | b;
    }

    private static String pretty(String form) {
        if (form == null || form.isEmpty()) return "?";
        return form.substring(0, 1).toUpperCase(Locale.ROOT) + form.substring(1);
    }
}
