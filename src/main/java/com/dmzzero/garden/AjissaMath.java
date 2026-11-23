package com.dmzzero.garden;

/** Pure rules for a Namekian's Ajissa sapling: when it grows. */
public final class AjissaMath {
    private static final long HOUR = 1_000;

    private AjissaMath() {
    }

    /** Ticks until it grows: somewhere from a quarter of maxHours in-game hours to all of them, by the roll. */
    public static long delay(double maxHours, double roll) {
        double r = Double.isFinite(roll) ? Math.max(0, Math.min(1, roll)) : 0;
        double hours = Double.isFinite(maxHours) && maxHours > 0 ? maxHours : 24;
        return Math.round(hours * HOUR * (0.25 + 0.75 * r));
    }

    public static long due(long planted, long delay) {
        long add = Math.max(0, delay);
        return planted > Long.MAX_VALUE - add ? Long.MAX_VALUE : planted + add;
    }
}
