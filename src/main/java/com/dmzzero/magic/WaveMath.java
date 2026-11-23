package com.dmzzero.magic;

import com.dmzzero.beam.BeamMath.V;

/** Pure maths for the telekinetic wave: who's in front of you and how hard they're shoved. */
public final class WaveMath {
    private static final V STILL = new V(0, 0, 0);
    private static final double LIFT = 0.3;

    private WaveMath() {
    }

    /** Within halfAngleDeg of where you look. Something right on top of you counts. */
    public static boolean inCone(V look, V toTarget, double halfAngleDeg) {
        if (toTarget.length() < 1e-6) return true;
        return look.normalize().dot(toTarget.normalize()) >= Math.cos(Math.toRadians(halfAngleDeg));
    }

    /** Flat away from you with a small lift, weakening to half at the edge of the range. */
    public static V push(V toTarget, V look, double range, double strength) {
        double dist = toTarget.length();
        if (!Double.isFinite(dist) || !Double.isFinite(range) || !Double.isFinite(strength) || range <= 0 || strength <= 0) return STILL;
        V flat = new V(toTarget.x(), 0, toTarget.z());
        if (flat.length() < 1e-6) flat = new V(look.x(), 0, look.z());
        if (!Double.isFinite(flat.length()) || flat.length() < 1e-6) return STILL;
        double falloff = Math.max(0.5, Math.min(1.0, 1 - 0.5 * dist / range));
        return flat.normalize().scale(strength * falloff).add(new V(0, LIFT * falloff, 0));
    }
}
