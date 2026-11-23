package com.dmzzero.garden;

/** Pure rules for plowing with bare hands: what a block pays, and the daily count it pays for. */
public final class PlowMath {
    private static final long DAY = 24_000;

    private PlowMath() {
    }

    /** A share of DMZ's TP per hit, at least 1 when it pays at all. */
    public static int tp(int tpPerHit, double share) {
        if (tpPerHit <= 0 || !Double.isFinite(share) || share <= 0) return 0;
        return (int) Math.max(1, Math.round(tpPerHit * share));
    }

    public static long day(long gameTime) {
        return Math.floorDiv(gameTime, DAY);
    }

    public static int countToday(long day, long savedDay, int saved) {
        return day == savedDay ? Math.max(0, saved) : 0;
    }

    public static boolean pays(int countToday, int cap) {
        return countToday < cap;
    }
}
