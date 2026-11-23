package com.dmzzero.impact;

import com.dmzzero.beam.BeamMath.V;

// shockwaves, booms and smashes near trees tear leaves loose
public final class LeafMath {
    /** Farthest a blast looks for leaves, in blocks, so a big one stays cheap. */
    public static final int MAX_REACH = 12;

    private LeafMath() {
    }

    /** A torn leaf's speed: straight out from the blast and a little up, faster closer in. */
    public static V velocity(V offset, double radius, float strength) {
        double flat = Math.hypot(offset.x(), offset.z());
        double r = Math.max(1, radius);
        double speed = (0.15 + 0.35 * Math.max(0, 1 - flat / r)) * strength + 0.05;
        double up = 0.08 + 0.12 * strength;
        if (flat < 1e-9) return new V(0, up + speed, 0);
        return new V(offset.x() / flat * speed, up, offset.z() / flat * speed);
    }

    public static int reach(double radius) {
        if (!Double.isFinite(radius) || radius <= 0) return 0;
        return (int) Math.min(MAX_REACH, Math.ceil(radius));
    }

    public static boolean strips(int tier, int stripTier) {
        return stripTier > 0 && tier >= stripTier;
    }
}
