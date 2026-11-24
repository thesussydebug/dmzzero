package com.dmzzero.client;

import com.dmzzero.DMZZero;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

// the server's word on your ki above full during ki boost's overcharge, for dmz's ki bar
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class ClientOvercharge {
    // each of dmz's hud styles keeps your own ki bar in a KI_BAR field; party and ki sense bars are other people's
    private static final String[] HUDS = {"AlternativeHUD", "ModernHUD", "XenoverseHUD", "MinecraftHUD"};
    private static float energy;
    private static float max;
    private static Set<Object> own;

    private ClientOvercharge() {
    }

    public static void set(float energy, float max) {
        boolean over = Float.isFinite(energy) && Float.isFinite(max) && max > 0f && energy > max;
        ClientOvercharge.energy = over ? energy : 0f;
        ClientOvercharge.max = over ? max : 0f;
    }

    /** Your ki above full, or 0 when it isn't. */
    public static float energy() {
        return energy;
    }

    /** Your normal full ki as the server counts it, or 0. */
    public static float max() {
        return max;
    }

    /** Whether this hud bar is your own ki bar in one of dmz's hud styles. */
    public static boolean ownBar(Object bar) {
        if (own == null) own = findBars();
        return own.contains(bar);
    }

    private static Set<Object> findBars() {
        Set<Object> bars = Collections.newSetFromMap(new IdentityHashMap<>());
        for (String hud : HUDS) {
            try {
                Field f = Class.forName("com.dragonminez.client.gui.hud." + hud).getDeclaredField("KI_BAR");
                f.setAccessible(true);
                Object bar = f.get(null);
                if (bar != null) bars.add(bar);
            } catch (ReflectiveOperationException | LinkageError e) {
                DMZZero.LOG.warn("[DMZ Zero] Ki Boost's overcharge can't find DMZ's {} ki bar, so it won't show above 100% there: {}", hud, e.toString());
            }
        }
        return bars;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        set(0f, 0f);
    }
}
