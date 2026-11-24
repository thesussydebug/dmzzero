package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.network.ZeroNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

// K asks the server for the time chamber training menu; the server decides whether you may train
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class TrainingKey {
    public static final KeyMapping OPEN = new KeyMapping("key.dmzzero.training", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.dmzzero");

    private TrainingKey() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (mc.player != null && mc.screen == null && mc.getConnection() != null
                    && ZeroNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection())) {
                mc.player.connection.sendCommand("dmzzero training");
            }
        }
    }
}
