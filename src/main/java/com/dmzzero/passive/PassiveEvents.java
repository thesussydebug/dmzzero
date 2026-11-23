package com.dmzzero.passive;

import com.dmzzero.DMZZero;
import com.dragonminez.common.events.DMZEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PassiveEvents {

    private PassiveEvents() {
    }

    // dmz's class passives scale this same amount, so the bonuses stack the way theirs do
    @SubscribeEvent
    public static void onStaminaRegen(DMZEvent.StaminaRegenEvent event) {
        double multiplier = TenaciousBody.multiplier(event.getStatsData());
        if (multiplier != 1.0) event.setAmount(event.getAmount() * multiplier);
    }
}
