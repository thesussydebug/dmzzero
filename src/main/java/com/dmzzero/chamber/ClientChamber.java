package com.dmzzero.chamber;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

// what the server last told this client about the time chamber; plain fields so menus and mixins can read it
public final class ClientChamber {
    private static final ResourceLocation DMZ_FONT = new ResourceLocation("dragonminez", "smooth");
    private static final long STALE_MS = 3000;
    private static volatile int zone = -1;
    private static volatile float temperature;
    private static volatile float tp = 1f;
    private static volatile boolean winded;
    private static volatile long at;

    private ClientChamber() {
    }

    public static void set(int zone, float temperature, float tp, boolean winded) {
        ClientChamber.zone = zone;
        ClientChamber.temperature = temperature;
        ClientChamber.tp = zone < 0 ? 1f : tp;
        ClientChamber.winded = zone >= 0 && winded;
        ClientChamber.at = System.currentTimeMillis();
    }

    public static void clear() {
        set(-1, 0f, 1f, false);
    }

    public static boolean active() {
        return zone >= 0 && System.currentTimeMillis() - at < STALE_MS;
    }

    public static int zone() {
        return active() ? zone : -1;
    }

    public static float temperature() {
        return temperature;
    }

    public static boolean winded() {
        return active() && winded;
    }

    public static double tpMultiplier() {
        return active() ? tp : 1.0;
    }

    // listed apart from dmz's own "HTC" line
    public static Component tooltipLine() {
        return Component.translatableWithFallback("gui.dmzzero.character_stats.tp_multiplier.chamber", "HTC Depth %s: x%s",
                        zone(), ChamberDepths.times(tpMultiplier()))
                .withStyle(Style.EMPTY.withFont(DMZ_FONT).withColor(0xFFB347));
    }

    public static Component sourceLabel() {
        return Component.translatableWithFallback("gui.dmzzero.stats.tp_source.chamber", "HTC Depth %s", zone());
    }
}
