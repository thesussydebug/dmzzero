package com.dmzzero.kishare;

import com.dmzzero.beam.BeamMath.V;

// a senzu lobbed to a friend, the way goku tossed cell one: how long it flies, its arc, and whether the catcher eats it
public final class BeanTossMath {
    public static final double SPEED = 0.9;
    public static final double CATCH = 1.3;
    public static final int MIN_TICKS = 4;
    public static final int MAX_TICKS = 40;
    public static final double MAX_LIFT = 3.0;
    private static final double LIFT_PER_BLOCK = 0.2;

    private BeanTossMath() {
    }

    public static int flightTicks(double distance) {
        if (!Double.isFinite(distance) || distance <= 0) return MIN_TICKS;
        return (int) Math.max(MIN_TICKS, Math.min(MAX_TICKS, Math.ceil(distance / SPEED)));
    }

    // how high above the straight line it rises halfway
    public static double lift(double distance) {
        if (!Double.isFinite(distance) || distance <= 0) return 0;
        return Math.min(MAX_LIFT, distance * LIFT_PER_BLOCK);
    }

    public static V at(V from, V to, double share, double lift) {
        double s = Double.isFinite(share) ? Math.max(0, Math.min(1, share)) : 1;
        V line = from.add(to.sub(from).scale(s));
        return new V(line.x(), line.y() + 4 * lift * s * (1 - s), line.z());
    }

    // only what this kind of bean restores counts
    public static boolean needs(boolean health, boolean ki, boolean stamina, float hp, float maxHp, float kiNow, float kiMax,
                                float st, float stMax) {
        return health && hp < maxHp || ki && kiNow < kiMax || stamina && st < stMax;
    }
}
