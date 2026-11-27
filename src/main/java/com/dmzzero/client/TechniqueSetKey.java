package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

// ; puts the next saved technique set in the slots; the server decides whether it can
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class TechniqueSetKey {
    public static final KeyMapping NEXT = new KeyMapping("key.dmzzero.next_technique_set", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, "key.categories.dmzzero");

    private TechniqueSetKey() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ZeroKeys.sendOnPress(NEXT, "dmzzero techniques next");
    }
}
