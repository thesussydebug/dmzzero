package com.dmzzero.client;

import com.dmzzero.DMZZero;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

// every dmz zero key goes into minecraft's controls from here, under "DMZ Zero".
// one handler on purpose: the event bus names a handler after its class's simple name and method, so two nested
// Keys classes with an onRegisterKeys each shared one handler and only one key got registered (EventHandlerNamesTest)
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ZeroKeys {
    private ZeroKeys() {
    }

    /** Every DMZ Zero key. A new key goes in this list, not in an event handler of its own. */
    public static List<KeyMapping> all() {
        return List.of(ScouterClient.PING, BeamCancelKey.CANCEL);
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : all()) event.register(key);
    }
}
