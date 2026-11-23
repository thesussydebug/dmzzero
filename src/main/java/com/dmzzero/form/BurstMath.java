package com.dmzzero.form;

import com.dmzzero.swat.SwatMath;

// the moment a transformation finishes, the power pushes everything nearby away
public final class BurstMath {
    private static final double MIN_BLAST_SPEED = 1.2;

    private BurstMath() {
    }

    static int tier(int tier) {
        return Math.max(1, Math.min(5, tier));
    }

    /** How far the burst reaches: the setting plus a block per tier. */
    public static double radius(double base, int tier) {
        return base + tier(tier);
    }

    /** How hard something this far away is pushed, in blocks per tick: strongest up close, nothing at the edge. */
    public static double speed(double distance, double radius, int tier, double strength) {
        if (!Double.isFinite(distance) || !Double.isFinite(radius) || !Double.isFinite(strength)) return 0;
        if (radius <= 0 || strength <= 0 || distance >= radius) return 0;
        double falloff = 1 - Math.max(0, distance) / radius;
        return strength * (0.6 + 0.25 * tier(tier)) * falloff;
    }

    /** The upward part of the push, so things leave the ground. */
    public static double lift(int tier) {
        return 0.2 + 0.05 * tier(tier);
    }

    /** The flat direction from the middle out to something at dx, dz. Something right in the middle goes east. */
    public static double[] away(double dx, double dz) {
        double len = Math.hypot(dx, dz);
        if (!Double.isFinite(len) || len < 1e-6) return new double[]{1, 0};
        return new double[]{dx / len, dz / len};
    }

    /** A ki blast thrown back out flies at least briskly, and no faster than a swat sends one. */
    public static double blastSpeed(double oldSpeed) {
        if (!Double.isFinite(oldSpeed)) return MIN_BLAST_SPEED;
        return Math.min(SwatMath.MAX_SPEED, Math.max(MIN_BLAST_SPEED, oldSpeed));
    }

    public static boolean ready(long now, long until) {
        return now >= until;
    }
}
