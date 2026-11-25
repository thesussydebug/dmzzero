package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.network.PowerSenseS2C;
import com.dmzzero.sense.PowerSense;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// a far stronger power rising somewhere: a heartbeat and a marker at the edge of the screen pointing to it
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class SenseClient {
    private static final int LIFE = 110;
    private static final int[] BEATS = {0, 14, 28};
    private static final List<Sense> SENSES = new ArrayList<>();
    private static int felt;

    private SenseClient() {
    }

    private static final class Sense {
        final double x, z;
        final int color;
        final float strength;
        int age;

        Sense(PowerSenseS2C msg) {
            x = msg.x();
            z = msg.z();
            color = msg.color();
            strength = msg.strength();
        }
    }

    public static void felt(PowerSenseS2C msg) {
        felt++;
        SENSES.removeIf(s -> s.age > 20 && Math.abs(s.x - msg.x()) < 4 && Math.abs(s.z - msg.z()) < 4);
        SENSES.add(new Sense(msg));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SENSES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            SENSES.clear();
            return;
        }
        if (mc.isPaused()) return;
        Iterator<Sense> it = SENSES.iterator();
        while (it.hasNext()) {
            Sense s = it.next();
            for (int beat : BEATS) {
                if (s.age == beat) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.8f + 0.3f * s.strength, 0.5f + 0.5f * s.strength));
                }
            }
            if (++s.age > LIFE) it.remove();
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (SENSES.isEmpty() || !ZeroConfig.CLIENT.senseMarker.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        float partial = event.getPartialTick();
        for (Sense s : SENSES) {
            float angle = PowerSense.screenAngle(mc.player.getViewYRot(partial), s.x - mc.player.getX(), s.z - mc.player.getZ());
            float t = s.age + partial;
            float fade = Math.min(1f, t / 6f) * Math.min(1f, (LIFE - t) / 30f);
            float pulse = 0.65f + 0.35f * (float) Math.sin(t * 0.45f);
            int alpha = (int) (255 * Math.max(0f, Math.min(1f, fade * pulse)));
            if (alpha < 8) continue;
            double rad = Math.toRadians(angle);
            float cx = (float) (w / 2.0 + Math.sin(rad) * (w / 2.0 - 22));
            float cy = (float) (h / 2.0 - Math.cos(rad) * (h / 2.0 - 22));
            arrow(g, cx, cy, angle, 5 + Math.round(4 * s.strength), (alpha << 24) | (s.color & 0xFFFFFF));
        }
    }

    // a chevron pointing outward, away from the middle of the screen; scouter marks use it too
    static void arrow(GuiGraphics g, float cx, float cy, float angle, int size, int argb) {
        int dark = (argb & 0xFF000000) | 0x101010;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        for (int row = 0; row < size; row++) {
            int half = row + 1;
            g.fill(-half - 1, -size + row * 2 - 1, half + 1, -size + row * 2 + 2, dark);
        }
        for (int row = 0; row < size; row++) {
            int half = row + 1;
            g.fill(-half, -size + row * 2, half, -size + row * 2 + 1, argb);
        }
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SENSES.clear();
    }

    // a marker points at coordinates in the dimension it was felt in
    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) SENSES.clear();
    }

    public static int feltCount() {
        return felt;
    }

    public static int active() {
        return SENSES.size();
    }

    // where the newest marker sits, in degrees from the crosshair
    public static float newestAngle() {
        Minecraft mc = Minecraft.getInstance();
        if (SENSES.isEmpty() || mc.player == null) return Float.NaN;
        Sense s = SENSES.get(SENSES.size() - 1);
        return PowerSense.screenAngle(mc.player.getYRot(), s.x - mc.player.getX(), s.z - mc.player.getZ());
    }
}
