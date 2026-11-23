package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.chamber.ChamberDepths;
import com.dmzzero.chamber.ClientChamber;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// a line at the top of the screen in the time chamber: what the depth multiplies tp by
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class ChamberClient {
    private static Component lastLine = Component.empty();
    private static int drawn;

    private ChamberClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // winded players can't sprint; the server slows them, this stops the run itself
        if (mc.player != null && ClientChamber.winded() && mc.player.isSprinting()) mc.player.setSprinting(false);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ClientChamber.active() || !ZeroConfig.CLIENT.chamberReadout.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Component line = line();
        lastLine = line;
        if (line.getString().isEmpty()) return;
        drawn++;
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth();
        g.drawCenteredString(mc.font, line, w / 2, 4, 0xFFFFFF);
    }

    // just the depth's tp multiplier; nothing by the house, where it pays no more than dmz does
    static Component line() {
        double tp = ClientChamber.tpMultiplier();
        if (tp <= 1.0) return Component.empty();
        return Component.literal("TP ×" + ChamberDepths.times(tp)).withStyle(ChatFormatting.GREEN);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientChamber.clear();
    }

    public static String lastLine() {
        return lastLine.getString();
    }

    public static int drawn() {
        return drawn;
    }
}
