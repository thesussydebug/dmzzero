package com.dmzzero.sense;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// kami's watch: meditating on the lookout, you sense every strong power in the dimension
public final class WatchMath {
    /** How long to crouch still before the watch begins. */
    public static final int SETTLE_TICKS = 60;
    // moved less than this per tick (squared, in blocks) counts as still
    private static final double STILL_SQ = 0.0025;
    private static final String[] COMPASS = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

    private WatchMath() {
    }

    public record Power(int id, double power, double dx, double dz) {
    }

    public static boolean strong(double power, double watcherPower, double share) {
        if (!Double.isFinite(power) || power <= 0) return false;
        if (!Double.isFinite(watcherPower) || watcherPower <= 0) return true;
        return power >= watcherPower * share;
    }

    public static List<Power> strongest(List<Power> all, int max) {
        List<Power> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingDouble(Power::power).reversed());
        return sorted.subList(0, Math.max(0, Math.min(max, sorted.size())));
    }

    /** Minecraft's north is -z, east is +x. */
    public static String compass(double dx, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6)) return "here";
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        if (deg < 0) deg += 360;
        return COMPASS[(int) Math.round(deg / 45.0) % 8];
    }

    public static boolean settled(int stillTicks) {
        return settled(stillTicks, SETTLE_TICKS);
    }

    public static boolean settled(int stillTicks, int settleTicks) {
        return stillTicks >= settleTicks;
    }

    public static boolean still(double movedSq) {
        return Double.isFinite(movedSq) && movedSq <= STILL_SQ;
    }
}
