package com.dmzzero.beam;

import com.dmzzero.beam.BeamMath.V;

// blocking a much weaker fighter's beam knocks it up into the sky
public final class DeflectMath {
    private static final float SKY_PITCH = -65f;
    // how far off straight at the beam you can look and still be blocking it
    private static final double FACING_DEG = 60;

    private DeflectMath() {
    }

    public static boolean strongerBy(double defenderPower, double casterPower, double ratio) {
        if (!Double.isFinite(defenderPower) || !Double.isFinite(casterPower) || defenderPower <= 0) return false;
        return defenderPower >= casterPower * ratio;
    }

    /** Against a creature without DMZ stats: the beam's hit would take at most share of your max health. */
    public static boolean weakHit(float damage, float maxHealth, double share) {
        if (!Float.isFinite(damage) || !Float.isFinite(maxHealth) || maxHealth <= 0) return false;
        return damage <= maxHealth * share;
    }

    /** Looking into the beam. beamDir is the way the beam is going. */
    public static boolean facing(V look, V beamDir) {
        return facing(look, beamDir, FACING_DEG);
    }

    public static boolean facing(V look, V beamDir, double maxDegrees) {
        double ll = look.length(), bl = beamDir.length();
        if (!Double.isFinite(ll) || !Double.isFinite(bl) || ll < 1e-9 || bl < 1e-9 || !Double.isFinite(maxDegrees)) return false;
        double cos = -look.dot(beamDir) / (ll * bl);
        return cos >= Math.cos(Math.toRadians(Math.min(maxDegrees, 179)));
    }

    /** Minecraft pitch: negative is up. */
    public static float deflectedPitch(float pitch) {
        if (!Float.isFinite(pitch)) return SKY_PITCH;
        return Math.min(pitch, SKY_PITCH);
    }
}
