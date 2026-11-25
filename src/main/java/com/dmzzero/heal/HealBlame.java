package com.dmzzero.heal;

import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// who did this: the player behind each block of ki damage waiting to heal, and what /dmzzero heal who says about them
public final class HealBlame {
    public static final int DEFAULT_RADIUS = 16;
    public static final int MAX_RADIUS = 64;
    // players named one by one; the rest are added up
    private static final int LISTED = 8;

    private HealBlame() {
    }

    /**
     * The player behind damage from this source, or null: a ki attack counts as its shooter's, and a mob, a boss or a shooter who
     * has logged off isn't someone to look up.
     */
    public static UUID playerBehind(Entity source) {
        Entity doer = source instanceof Projectile shot ? shot.getOwner() : source;
        return doer instanceof ServerPlayer player ? player.getUUID() : null;
    }

    /** The lines of the answer: how many blocks, the players with the most of them, and the blocks no player is known for. */
    static List<Component> lines(MinecraftServer server, HealQueue.Tally tally, int radius) {
        List<Component> out = new ArrayList<>();
        if (tally.total() == 0) {
            out.add(Component.translatableWithFallback("dmzzero.heal.who.none", "No damaged blocks within %s blocks of here are waiting to heal.", radius));
            return out;
        }
        out.add(Component.translatableWithFallback("dmzzero.heal.who.header", "%s damaged blocks within %s blocks of here are waiting to heal:",
                tally.total(), radius));
        List<Map.Entry<UUID, Integer>> ranked = HealMath.ranked(tally.byPlayer());
        int shown = Math.min(LISTED, ranked.size());
        for (Map.Entry<UUID, Integer> e : ranked.subList(0, shown)) {
            out.add(Component.translatableWithFallback("dmzzero.heal.who.player", "%s: %s blocks", name(server, e.getKey()), e.getValue()));
        }
        if (ranked.size() > shown) {
            int rest = ranked.subList(shown, ranked.size()).stream().mapToInt(Map.Entry::getValue).sum();
            out.add(Component.translatableWithFallback("dmzzero.heal.who.more", "%s more players: %s blocks", ranked.size() - shown, rest));
        }
        if (tally.unknown() > 0) {
            out.add(Component.translatableWithFallback("dmzzero.heal.who.unknown",
                    "Not traced to a player: %s blocks (damage from before it was recorded, a mob or a boss)", tally.unknown()).withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    // the name the server knows, or the id when it has forgotten it (the profile cache keeps the last 1000 players)
    private static String name(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache cache = server.getProfileCache();
        return cache == null ? id.toString() : cache.get(id).map(GameProfile::getName).orElse(id.toString());
    }
}
