package com.dmzzero.garden;

import com.dmzzero.DMZZero;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// a client without dmz zero follows a planting click with eating what was planted (a senzu, a might tree fruit); the
// server ignores that eat for a couple of ticks after a planting
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class PlantGuard {
    private static final Map<UUID, Long> PLANTED = new HashMap<>();

    private PlantGuard() {
    }

    static void mark(ServerPlayer player) {
        PLANTED.put(player.getUUID(), player.serverLevel().getGameTime());
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!SenzuGarden.isBean(event.getItemStack()) && !MightTree.isFruit(event.getItemStack())) return;
        Long at = PLANTED.get(p.getUUID());
        if (at != null && p.serverLevel().getGameTime() - at <= 2) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PLANTED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PLANTED.clear();
    }
}
