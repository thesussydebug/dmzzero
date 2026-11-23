package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.network.TitleCardS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// a big title over the screen that shrinks to fit long form names
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class TitleCard {
    private static final int FADE_IN = 6;
    private static final int FADE_OUT = 25;
    private static final float TITLE_SCALE = 3.5f;
    private static final float SUB_SCALE = 1.6f;

    private static Component title = Component.empty();
    private static Component subtitle = Component.empty();
    private static int age = -1;
    private static int life;
    // the last few cards, so dev tests can't miss one that was quickly replaced
    private static final java.util.ArrayDeque<String> RECENT = new java.util.ArrayDeque<>();

    private TitleCard() {
    }

    public static void show(TitleCardS2C msg) {
        title = msg.title();
        subtitle = msg.subtitle();
        RECENT.addLast(title.getString() + " / " + subtitle.getString());
        while (RECENT.size() > 8) RECENT.removeFirst();
        life = FADE_IN + msg.stay() + FADE_OUT;
        age = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || age < 0 || Minecraft.getInstance().isPaused()) return;
        if (++age > life) age = -1;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (age < 0 || mc.options.hideGui) return;
        float t = age + event.getPartialTick();
        float alpha = Math.min(1f, t / FADE_IN) * Math.min(1f, (life - t) / FADE_OUT);
        if (alpha <= 0.02f) return;
        int a = Math.max(4, (int) (alpha * 255)) << 24;
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        float titleScale = fit(font.width(title), w, TITLE_SCALE);
        float subScale = fit(font.width(subtitle), w, SUB_SCALE);
        float y = h * 0.28f;
        draw(g, font, title, w / 2f, y, titleScale, a);
        draw(g, font, subtitle, w / 2f, y + font.lineHeight * titleScale + 4, subScale, a);
    }

    // as big as it may be, but never wider than the screen with a margin
    static float fit(int textWidth, int screenWidth, float max) {
        if (textWidth <= 0) return max;
        return Math.max(0.5f, Math.min(max, (screenWidth - 24f) / textWidth));
    }

    private static void draw(GuiGraphics g, Font font, Component text, float cx, float y, float scale, int alpha) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, -font.width(text) / 2, 0, 0xFFFFFF | alpha, true);
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        age = -1;
    }

    static boolean shownRecently(String text) {
        for (String s : RECENT) if (s.contains(text)) return true;
        return false;
    }

    public static String titleText() {
        return age < 0 ? "" : title.getString();
    }

    public static String subtitleText() {
        return age < 0 ? "" : subtitle.getString();
    }

    // how wide the title is drawn right now, for the dev tests
    public static float titleWidthOnScreen() {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        return mc.font.width(title) * fit(mc.font.width(title), w, TITLE_SCALE);
    }
}
