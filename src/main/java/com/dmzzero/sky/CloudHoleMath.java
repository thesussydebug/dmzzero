package com.dmzzero.sky;

import com.dmzzero.beam.BeamMath.V;

// a beam fired through the cloud layer punches a hole in it, which hangs open a moment after the beam and slowly closes
public final class CloudHoleMath {
    /** Ticks to open, to hang open once the beam is gone, and to close. */
    public static final int OPEN = 15;
    public static final int HOLD = 60;
    public static final int CLOSE = 140;
    public static final double MIN_RADIUS = 10;
    public static final double MAX_RADIUS = 48;
    /** Vanilla clouds drift toward -x this many blocks a tick. */
    public static final double DRIFT = 0.03;
    // a beam flatter than this skims the clouds instead of punching through
    private static final double MIN_RISE = 0.25;

    private CloudHoleMath() {
    }

    /** Where a beam from origin along dir, this long, crosses height y. Null if it doesn't get there. */
    public static V crossing(V origin, V dir, double length, double y) {
        if (!Double.isFinite(length) || !Double.isFinite(y) || !Double.isFinite(dir.y()) || Math.abs(dir.y()) < MIN_RISE) return null;
        double t = (y - origin.y()) / dir.y();
        if (!Double.isFinite(t) || t < 0 || t > length) return null;
        return new V(origin.x() + dir.x() * t, y, origin.z() + dir.z() * t);
    }

    public static double fullRadius(float beamSize) {
        if (!Float.isFinite(beamSize)) return MIN_RADIUS;
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, 9 + 6 * beamSize));
    }

    /** The radius age ticks after the hole opened. endedAt is the age the beam stopped crossing, -1 while it still does. */
    public static double radius(double full, int age, int endedAt) {
        if (endedAt < 0) return full * opened(age);
        double reached = full * opened(endedAt);
        int since = age - endedAt;
        if (since <= HOLD) return reached;
        double c = Math.min(1, (since - HOLD) / (double) CLOSE);
        return reached * (1 - c * c);
    }

    public static boolean done(int age, int endedAt) {
        return endedAt >= 0 && age - endedAt >= HOLD + CLOSE;
    }

    /** Radius multiplier round the edge, so the hole isn't a perfect circle. The same seed gives the same shape. */
    public static double edge(double angle, int seed) {
        double p = seed * 2.399963;
        return 1 + 0.12 * Math.sin(3 * angle + p) + 0.07 * Math.sin(5 * angle + 1.7 * p) + 0.04 * Math.sin(9 * angle + 2.3 * p);
    }

    /** Once the beam is gone nothing holds the hole in place, and it drifts off with the clouds. */
    public static double driftX(int age, int endedAt) {
        if (endedAt < 0) return 0;
        return -DRIFT * Math.max(0, age - endedAt);
    }

    private static double opened(int age) {
        if (age >= OPEN) return 1;
        double t = (Math.max(0, age) + 1) / (double) (OPEN + 1);
        return 1 - (1 - t) * (1 - t);
    }
}
