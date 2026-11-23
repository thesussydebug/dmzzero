package com.dmzzero.form;

// giant forms (Great Ape, Namekian Giant, Corrupted Giant...) fight like giants
public final class GiantMath {
    private static final double MIN_CRATER = 1.5;
    private static final double MAX_CRATER = 3.0;

    private GiantMath() {
    }

    /** A form's size: its largest model scale. No scaling, or nothing usable, is normal size. */
    public static double scale(Float[] scaling) {
        if (scaling == null) return 1.0;
        double best = -1;
        for (Float f : scaling) {
            if (f != null && Float.isFinite(f) && f > best) best = f;
        }
        return best > 0 ? best : 1.0;
    }

    public static boolean isGiant(double scale, double minScale) {
        return Double.isFinite(scale) && scale >= minScale;
    }

    /** How far a stomp's shockwave reaches. */
    public static double stompRadius(double scale) {
        return 1.5 + scale;
    }

    /** How big a crater a stomp leaves. */
    public static double craterRadius(double scale) {
        if (!Double.isFinite(scale)) return MIN_CRATER;
        return Math.max(MIN_CRATER, Math.min(MAX_CRATER, 0.6 * scale));
    }

    /** Landing from at least this far up is a stomp. */
    public static boolean stomps(float fallDistance, double minFall) {
        return Float.isFinite(fallDistance) && fallDistance >= minFall;
    }

    /** How hard a stomp pushes something this far away, in blocks per tick. */
    public static double stompSpeed(double distance, double radius) {
        if (!Double.isFinite(distance) || !Double.isFinite(radius) || radius <= 0 || distance >= radius) return 0;
        return 1.4 * (1 - Math.max(0, distance) / radius);
    }

    public static double stompSpeed(double distance, double radius, double strength) {
        if (!Double.isFinite(strength) || strength <= 0) return 0;
        return strength * stompSpeed(distance, radius);
    }

    /** A giant shrugs off knockback from anyone much smaller; another giant can still move it. */
    public static boolean resists(double giantScale, double attackerScale, double ratio) {
        if (!Double.isFinite(giantScale) || giantScale <= 1) return false;
        if (!Double.isFinite(attackerScale)) return true;
        return attackerScale < giantScale * ratio;
    }
}
