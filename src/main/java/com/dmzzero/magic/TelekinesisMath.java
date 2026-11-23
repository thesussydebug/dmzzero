package com.dmzzero.magic;

import com.dmzzero.beam.BeamMath.V;

/** Pure maths for telekinesis: what you can lift, where it floats, the throw and its impact. */
public final class TelekinesisMath {
    /** How far past the grab range a held thing can drift before the hold slips. */
    public static final double SLACK = 4;
    private static final V STILL = new V(0, 0, 0);

    private TelekinesisMath() {
    }

    /** Up to ratio times your own power. With no power you lift nothing. */
    public static boolean canLift(double caster, double target, double ratio) {
        if (!Double.isFinite(caster) || !Double.isFinite(target) || !Double.isFinite(ratio)) return false;
        return caster > 0 && target >= 0 && target <= caster * ratio;
    }

    /** distance blocks along your look from your eyes, lowered so its middle sits there. */
    public static V holdPoint(V eye, V look, double distance, double halfHeight) {
        return eye.add(look.normalize().scale(distance)).sub(new V(0, halfHeight, 0));
    }

    /** The velocity that carries it toward the hold point, capped so it doesn't overshoot. */
    public static V pull(V at, V target, double gain, double max) {
        V d = target.sub(at).scale(gain);
        double len = d.length();
        if (!Double.isFinite(len)) return STILL;
        return len > max ? d.scale(max / len) : d;
    }

    public static V toss(V look, double speed) {
        V n = look.normalize();
        double len = n.length();
        if (!Double.isFinite(len) || len < 0.5 || !Double.isFinite(speed) || speed <= 0) return STILL;
        return n.scale(speed);
    }

    /** A share of your melee damage, scaled down when it hit slower than it was thrown. */
    public static float impact(double melee, double share, double speed, double throwSpeed) {
        if (!Double.isFinite(melee) || !Double.isFinite(share) || !Double.isFinite(speed) || !Double.isFinite(throwSpeed)) return 0f;
        if (melee <= 0 || share <= 0 || speed <= 0 || throwSpeed <= 0) return 0f;
        return (float) (melee * share * Math.min(1.0, speed / throwSpeed));
    }

    public static boolean breaksFree(int chargedTicks, int needTicks) {
        return needTicks > 0 && chargedTicks >= needTicks;
    }

    public static boolean expired(long ageTicks, int maxSeconds) {
        return ageTicks >= maxSeconds * 20L;
    }

    public static boolean slipped(double distance, double range) {
        return !Double.isFinite(distance) || distance > range + SLACK;
    }
}
