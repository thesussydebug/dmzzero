package com.dmzzero.beam;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// a key that ends your own ki wave early, or lets go of one you're still charging
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class BeamCancel {
    // a fired beam stays where it was fired from while its caster moves on
    private static final double SEARCH = 96;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private BeamCancel() {
    }

    /** "ok", or why not: off, cannot, cooldown, none. */
    public static String request(ServerPlayer player) {
        if (!ZeroConfig.SERVER.beamCancel.get()) return "off";
        if (!player.isAlive() || player.isSpectator()) return "cannot";
        long now = player.server.getTickCount();
        Long lastUse = LAST.get(player.getUUID());
        if (!BeamCancelMath.ready(now, lastUse == null ? -1 : lastUse)) return "cooldown";
        LAST.put(player.getUUID(), now);
        UUID id = player.getUUID();
        List<KiWaveEntity> mine = player.serverLevel().getEntitiesOfClass(KiWaveEntity.class, player.getBoundingBox().inflate(SEARCH),
                w -> w.isAlive() && id.equals(w.getOwnerUUID()));
        if (mine.isEmpty()) return "none";
        for (KiWaveEntity w : mine) {
            int life = BeamCancelMath.lifeFor(w.tickCount, w.isFiring());
            if (life == BeamCancelMath.DROP) w.discard();
            else w.setMaxLife(life);
        }
        return "ok";
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST.clear();
    }

    // for the self-test
    public static void ready(UUID who) {
        LAST.remove(who);
    }
}
