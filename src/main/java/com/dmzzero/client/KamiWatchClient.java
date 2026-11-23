package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.network.KamiWatchS2C;
import com.dmzzero.sense.PowerSense;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

// kami's watch: an arrow at the screen edge for each power sensed in the dimension, with how far away it is
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class KamiWatchClient {
    // the server refreshes every two seconds; markers go if it stops
    private static final int LIFE = 80;
    private static final List<KamiWatchS2C.Entry> ENTRIES = new ArrayList<>();
    private static int age = LIFE + 1;
    private static int drawn;

    private KamiWatchClient() {
    }

    public static void received(KamiWatchS2C msg) {
        ENTRIES.clear();
        ENTRIES.addAll(msg.entries());
        age = msg.entries().isEmpty() ? LIFE + 1 : 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ENTRIES.isEmpty()) return;
        if (Minecraft.getInstance().player == null || ++age > LIFE) ENTRIES.clear();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        drawn = 0;
        if (ENTRIES.isEmpty() || !ZeroConfig.CLIENT.watchMarkers.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        float partial = event.getPartialTick();
        float fade = Math.min(1f, (LIFE - age) / 20f);
        int alpha = (int) (230 * Math.max(0.15f, fade));
        for (KamiWatchS2C.Entry e : ENTRIES) {
            double dx = e.x() - mc.player.getX(), dz = e.z() - mc.player.getZ();
            float angle = PowerSense.screenAngle(mc.player.getViewYRot(partial), dx, dz);
            double rad = Math.toRadians(angle);
            float cx = (float) (w / 2.0 + Math.sin(rad) * (w / 2.0 - 30));
            float cy = (float) (h / 2.0 - Math.cos(rad) * (h / 2.0 - 30));
            int argb = (alpha << 24) | (e.color() & 0xFFFFFF);
            SenseClient.arrow(g, cx, cy, angle, 4 + Math.round(4 * Math.max(0f, Math.min(1f, e.strength()))), argb);
            String label = e.name().isEmpty() ? e.distance() + "m" : e.name() + " " + e.distance() + "m";
            int tw = mc.font.width(label);
            float lx = cx - (float) Math.sin(rad) * 16 - tw / 2f, ly = cy + (float) Math.cos(rad) * 16 - 4;
            g.drawString(mc.font, Component.literal(label), Math.round(lx), Math.round(ly), (alpha << 24) | 0xFFFFFF, true);
            drawn++;
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ENTRIES.clear();
    }

    public static int showing() {
        return ENTRIES.size();
    }

    public static int drawn() {
        return drawn;
    }
}
