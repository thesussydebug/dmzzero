package com.dmzzero.swat;

import com.dmzzero.beam.BeamMath.V;

// punching an incoming ki blast at the right moment knocks it away where you look
public final class SwatMath {
    public static final double MAX_SPEED = 4.0;
    // a blast this close is swatted even when it isn't heading for you any more
    private static final double POINT_BLANK = 1.5;

    private SwatMath() {
    }

    /** The blast is in front of you, within reach, and coming at you (or right on you). */
    public static boolean inWindow(V eye, V look, V ball, V vel, double reach, double coneDeg) {
        if (!finite(eye) || !finite(look) || !finite(ball) || !finite(vel)) return false;
        double lookLen = look.length();
        if (lookLen < 1e-9) return false;
        V to = ball.sub(eye);
        double dist = to.length();
        if (dist > reach) return false;
        if (dist < 1e-9) return true;
        double cos = to.dot(look) / (dist * lookLen);
        if (cos < Math.cos(Math.toRadians(coneDeg))) return false;
        boolean coming = vel.dot(eye.sub(ball)) > 0;
        return coming || dist <= POINT_BLANK;
    }

    /** Only a blast that couldn't take more than share of your max health can be slapped away. */
    public static boolean strongEnough(float damage, float maxHealth, double share) {
        if (!Float.isFinite(damage) || !Float.isFinite(maxHealth) || damage < 0 || maxHealth <= 0) return false;
        return damage <= maxHealth * share;
    }

    public static boolean smallEnough(float size, float maxSize) {
        return Float.isFinite(size) && size <= maxSize;
    }

    /** Where it goes: along your look, at least minSpeed, faster than it came by boost, never past MAX_SPEED. */
    public static V velocity(V look, double oldSpeed, double minSpeed, double boost) {
        if (!finite(look)) return null;
        double len = look.length();
        if (len < 1e-9) return null;
        double speed = Math.min(MAX_SPEED, Math.max(minSpeed, (Double.isFinite(oldSpeed) ? oldSpeed : 0) * boost));
        return look.scale(speed / len);
    }

    public static boolean ready(long now, long lockedUntil) {
        return now >= lockedUntil;
    }

    private static boolean finite(V v) {
        return v != null && Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z());
    }
}
