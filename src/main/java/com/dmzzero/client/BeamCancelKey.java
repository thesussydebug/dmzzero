package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.network.BeamCancelC2S;
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

// the cancel-beam key: asks the server to end your own ki wave. it's registered in ZeroKeys
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class BeamCancelKey {
    public static final KeyMapping CANCEL = new KeyMapping("key.dmzzero.cancel_beam", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.dmzzero");
    private static int sent;

    private BeamCancelKey() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (CANCEL.consumeClick()) press(mc);
    }

    static boolean press(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null || !ZeroNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection())) return false;
        ZeroNetwork.CHANNEL.sendToServer(new BeamCancelC2S());
        sent++;
        return true;
    }

    // for the dev autotest
    public static int sent() {
        return sent;
    }
}
