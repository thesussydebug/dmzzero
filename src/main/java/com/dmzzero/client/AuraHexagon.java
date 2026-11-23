package com.dmzzero.client;

import com.dmzzero.ZeroConfig;
import com.dragonminez.client.render.HelmetFormLook;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;

// the hexagon stats view, in dmz's menu and in sparking's, takes the aura color of the current form
public final class AuraHexagon {
    private static volatile String lastColor = "";

    private AuraHexagon() {
    }

    public static String pick(boolean followForm, String formColor, String baseColor) {
        return followForm && formColor != null && !formColor.isEmpty() ? formColor : baseColor;
    }

    public static String color(StatsData stats, Character character) {
        String base = character.getAuraColor();
        String form = stats == null ? null : HelmetFormLook.activeAuraColor(stats);
        String color = pick(ZeroConfig.read(ZeroConfig.CLIENT.hexagonFormColor, true), form, base);
        lastColor = color == null ? "" : color;
        return color;
    }

    public static String lastColor() {
        return lastColor;
    }

    static void forget() {
        lastColor = "";
    }
}
