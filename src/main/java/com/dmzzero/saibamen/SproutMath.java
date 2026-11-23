package com.dmzzero.saibamen;

// saibaman seeds grow one of dmz's six saibamen, at the odds dmz spawns them with
public final class SproutMath {
    // dmz's spawn weights for saga_saibaman1 to 6 (data/dragonminez/forge/biome_modifier/saibamans.json)
    private static final int[] WEIGHTS = {80, 70, 60, 45, 35, 25};
    private static final int TOTAL = 315;

    private SproutMath() {
    }

    /** Which saibaman a seed grows, 1 to 6, from a roll in [0, 1). */
    public static int kind(double roll) {
        if (!(roll > 0)) return 1;
        int acc = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            acc += WEIGHTS[i];
            if (roll < acc / (double) TOTAL) return i + 1;
        }
        return WEIGHTS.length;
    }

    public static String entityPath(int kind) {
        return "saga_saibaman" + Math.max(1, Math.min(WEIGHTS.length, kind));
    }

    /** Game time the sprout climbs out. */
    public static long readyAt(long now, int seconds) {
        if (seconds <= 0) return now;
        long add = seconds * 20L;
        return now > Long.MAX_VALUE - add ? Long.MAX_VALUE : now + add;
    }

    public static boolean ready(long readyAt, long now) {
        return now >= readyAt;
    }

    public static boolean isSaibaman(String namespace, String path) {
        return "dragonminez".equals(namespace) && path != null && path.matches("saga_saibaman[1-6]");
    }

    public static boolean drops(double roll, double chance) {
        return roll < chance;
    }

    public static boolean canPlant(int pending, int max) {
        return pending < max;
    }
}
