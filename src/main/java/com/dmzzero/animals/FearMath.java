package com.dmzzero.animals;

/** Pure rules for animals running from a raised aura: who is scared of whom, a fresh fright, and the way out. */
public final class FearMath {
    private FearMath() {
    }

    /**
     * Whether an animal with {@code animal} battle power runs from a fighter with {@code fighter}: the fighter is at least
     * {@code ratio} times stronger. DMZ gives animals a battle power of their own (a cow about 13, a horse 56); one with
     * none at all (another mod's) is scared of any raised aura.
     */
    public static boolean fears(double animal, double fighter, double ratio) {
        if (!(animal > 0)) return true;
        if (!Double.isFinite(fighter) || !(fighter > 0)) return false;
        return Double.isFinite(animal) && fighter >= animal * Math.max(1.0, ratio);
    }

    /** Whether this is a new fright (a horse rears, a wolf whines): none yet, or the last one has passed. */
    public static boolean fresh(Long until, long now) {
        return until == null || now >= until;
    }

    /** A spot {@code distance} blocks straight away from the fighter, for an animal at (ax, az); north when they stand on it. */
    public static double[] away(double fx, double fz, double ax, double az, double distance) {
        double dx = ax - fx, dz = az - fz, len = Math.sqrt(dx * dx + dz * dz);
        if (!(len > 1e-6) || !Double.isFinite(len)) return new double[]{ax, az - distance};
        return new double[]{ax + dx / len * distance, az + dz / len * distance};
    }
}
