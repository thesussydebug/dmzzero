package com.dmzzero.callout;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.network.CalloutS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.sense.PowerSense;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// yamcha always shouted "Wolf Fang Fist!": a strike attack's name goes to dmz zero clients nearby, shown like a ki technique's
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class StrikeCallouts {
    private static final double RANGE = 64;
    private static int sent;
    private static String last = "";

    private StrikeCallouts() {
    }

    @SubscribeEvent
    public static void onStrike(DMZEvent.StrikeAttackFireEvent event) {
        if (!ZeroConfig.SERVER.strikeNames.get()) return;
        ServerPlayer p = event.getPlayer();
        StrikeAttackData strike = event.getStrike();
        if (p == null || strike == null) return;
        send(p, strike.getName(), strike.getId(), PowerSense.color(event.getStatsData().getCharacter().getActiveAuraColor()));
    }

    public static void send(ServerPlayer p, String name, String technique, int color) {
        String clean = CalloutMath.clean(name);
        String id = technique == null || technique.length() > CalloutS2C.MAX_TEXT ? "" : technique;
        if (clean == null && id.isEmpty()) return;
        ZeroNetwork.sendAround(p.serverLevel(), p.position(), RANGE, new CalloutS2C(p.getId(), clean == null ? "" : clean, id, color));
        sent++;
        last = clean == null ? id : clean;
    }

    public static int sent() {
        return sent;
    }

    public static String last() {
        return last;
    }
}
