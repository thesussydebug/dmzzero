package com.dmzzero.waterfall;

/** Pure rules for training under a waterfall: keeping still, the seconds spent under the falls, and when they pay. */
public final class WaterfallMath {
    // falling water nudges a player about, so a block a second of drift still counts as standing still
    public static final double DRIFT = 1.0;

    private WaterfallMath() {
    }

    /** Moved at most this far across the ground since the last second. */
    public static boolean still(double dx, double dz, double most) {
        return dx * dx + dz * dz <= most * most;
    }

    /** Seconds under the falls after this one. Stepping out or moving starts over. */
    public static int next(int seconds, boolean under, boolean still) {
        return under && still ? seconds + 1 : 0;
    }

    /** A payment comes due at every full stretch of this many seconds. */
    public static boolean due(int seconds, int every) {
        return every > 0 && seconds > 0 && seconds % every == 0;
    }
}
