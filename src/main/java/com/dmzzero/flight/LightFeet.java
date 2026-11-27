package com.dmzzero.flight;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// a fighter who can fly steps lightly: their jumps and landings leave farmland as it is. dmz clears fall distance only
// while someone is flying, so a jump or a drop with flight off would still trample it
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class LightFeet {
    private LightFeet() {
    }

    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && light(player)) event.setCanceled(true);
    }

    public static boolean light(ServerPlayer player) {
        if (!ZeroConfig.SERVER.lightFeet.get()) return false;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        return data != null && data.getSkills().getSkillLevel("fly") >= ZeroConfig.SERVER.lightFeetFlyLevel.get();
    }
}
