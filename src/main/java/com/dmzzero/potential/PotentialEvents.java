package com.dmzzero.potential;

import com.dmzzero.DMZZero;
import com.dmzzero.chamber.ChamberDepths;
import com.dmzzero.status.TickCost;
import com.dragonminez.common.events.DMZEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class PotentialEvents {
    private static final int COST = TickCost.id("growth.potential", () -> PotentialEvents.TP_CARRY.size());
    private static final Map<UUID, Double> TP_CARRY = new HashMap<>();

    private PotentialEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        long start = TickCost.start();
        playerTick(event);
        TickCost.add(COST, start);
    }

    private static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 == 0) PotentialService.ensureRolled(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PotentialService.sync(player);
        PotentialService.ensureRolled(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TP_CARRY.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TP_CARRY.clear();
    }

    public static void resetCarry(UUID id) {
        TP_CARRY.remove(id);
    }

    // after dmz's HIGH listener so party shares aren't boosted twice; the time chamber's depth multiplies in here too
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTpGain(DMZEvent.TPGainEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        int gain = event.getTpGain();
        if (gain <= 0) return;
        double multiplier = Potential.tpMultiplier(PotentialData.of(player)) * ChamberDepths.tpMultiplier(player);
        if (multiplier == 1.0) return;
        UUID id = player.getUUID();
        Potential.TpScale scaled = Potential.scaleTp(gain, multiplier, TP_CARRY.getOrDefault(id, 0.0));
        TP_CARRY.put(id, scaled.carry());
        event.setTpGain(scaled.gain());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PotentialCommand.register(event.getDispatcher());
    }
}
