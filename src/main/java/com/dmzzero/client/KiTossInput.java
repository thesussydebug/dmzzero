package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.network.KiTossC2S;
import com.dmzzero.network.ZeroNetwork;
import com.dragonminez.client.systems.kishare.KiShareInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// dmz's ki sharing chord (ctrl+alt) with a left click instead of a held right click
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class KiTossInput {
    // the server checks the real range from its config
    private static final double PICK_RANGE = 64;
    private static final int RESEND_TICKS = 5;
    private static long lastSent = -100;

    private KiTossInput() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || !KiShareInput.isChordDown()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || !ZeroNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection())) return;
        event.setCanceled(true);
        event.setSwingHand(true);
        Player target = KiShareInput.pickTarget(player, PICK_RANGE);
        if (target == null) return;
        long now = player.level().getGameTime();
        if (now - lastSent < RESEND_TICKS) return;
        lastSent = now;
        send(target.getId());
    }

    static void send(int targetId) {
        ZeroNetwork.CHANNEL.sendToServer(new KiTossC2S(targetId));
    }
}
