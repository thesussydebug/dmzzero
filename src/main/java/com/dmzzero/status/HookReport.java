package com.dmzzero.status;

import com.dmzzero.DMZZero;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

// one log line with the mixin hook count once the server is up. a hook in a class that loads later is counted
// when that class loads, and HookPlugin logs it then if it finds nothing
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class HookReport {

    private HookReport() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        List<String> missed = HookPlugin.missed();
        if (missed.isEmpty()) {
            DMZZero.LOG.info("Mixin hooks: {} of {} found their target, in the {} mixins applied so far", HookPlugin.bound(), HookPlugin.hooks(), HookPlugin.applied());
        } else {
            DMZZero.LOG.warn("Mixin hooks: {} of {} found their target, in the {} mixins applied so far. Found nothing: {}",
                    HookPlugin.bound(), HookPlugin.hooks(), HookPlugin.applied(), String.join(", ", missed));
        }
    }
}
