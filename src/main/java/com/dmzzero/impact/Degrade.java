package com.dmzzero.impact;

import java.util.Map;
import java.util.Set;

public final class Degrade {
    private static final Map<String, String> NEXT = Map.ofEntries(
            Map.entry("minecraft:grass_block", "minecraft:dirt"),
            Map.entry("minecraft:podzol", "minecraft:dirt"),
            Map.entry("minecraft:mycelium", "minecraft:dirt"),
            Map.entry("minecraft:dirt_path", "minecraft:dirt"),
            Map.entry("minecraft:dirt", "minecraft:coarse_dirt"),
            Map.entry("minecraft:rooted_dirt", "minecraft:coarse_dirt"),
            Map.entry("minecraft:smooth_stone", "minecraft:stone"),
            Map.entry("minecraft:stone", "minecraft:cobblestone"),
            Map.entry("minecraft:deepslate", "minecraft:cobbled_deepslate"),
            Map.entry("minecraft:stone_bricks", "minecraft:cracked_stone_bricks"),
            Map.entry("minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks"),
            Map.entry("minecraft:deepslate_tiles", "minecraft:cracked_deepslate_tiles"),
            Map.entry("minecraft:nether_bricks", "minecraft:cracked_nether_bricks"),
            Map.entry("minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks"));

    private Degrade() {
    }

    public static String next(String blockId) {
        return blockId == null ? null : NEXT.get(blockId);
    }

    static Set<String> sources() {
        return NEXT.keySet();
    }
}
