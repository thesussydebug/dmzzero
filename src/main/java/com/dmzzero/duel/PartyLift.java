package com.dmzzero.duel;

import com.dmzzero.DMZZero;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.server.world.tournament.Tournament;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// dmz keeps a party's lifted friendly fire as one flag in a set, not a count. duels and null realm battles each lift it
// for party members fighting each other, so it's counted here: it drops only when the last of those fights ends, and
// never while one of the party is in a dmz tournament, which lifts the same flag
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class PartyLift {
    private static final Map<UUID, Integer> HELD = new HashMap<>();

    private PartyLift() {
    }

    public static void lift(MinecraftServer server, UUID party) {
        if (party == null) return;
        if (HELD.merge(party, 1, Integer::sum) == 1) PartyManager.setTournamentFriendlyFire(server, party, true);
    }

    public static void drop(MinecraftServer server, UUID party) {
        if (party == null) return;
        Integer held = HELD.get(party);
        if (held == null) return;
        if (held > 1) {
            HELD.put(party, held - 1);
            return;
        }
        HELD.remove(party);
        if (!inTournament(server, party)) PartyManager.setTournamentFriendlyFire(server, party, false);
    }

    /** How many fights hold the party's friendly fire lifted, for the self-test. */
    public static int held(UUID party) {
        return HELD.getOrDefault(party, 0);
    }

    private static boolean inTournament(MinecraftServer server, UUID party) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (party.equals(PartyManager.getPartyId(p)) && Tournament.Manager.runOf(p) != null) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HELD.clear();
    }
}
