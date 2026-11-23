package com.dmzzero.form;

/** Pure rules for a fully mastered transformation's ki techniques: when it counts, the cost and the damage. */
public final class KiMasteryMath {
    // a share of max mastery this close to 1 is full; mastery is a double that grows in fractions
    private static final double FULL = 1.0 - 1e-6;
    private static final double MAX_BONUS = 10.0;

    private KiMasteryMath() {
    }

    /** Transformed at all, and every active form and stack form is at its max mastery. */
    public static boolean mastered(boolean form, double formShare, boolean stack, double stackShare) {
        if (!form && !stack) return false;
        return (!form || formShare >= FULL) && (!stack || stackShare >= FULL);
    }

    /** What a ki technique's cost is multiplied by: the setting when mastered, else 1. Never more than 1. */
    public static double costFactor(boolean mastered, double setting) {
        if (!mastered || !Double.isFinite(setting)) return 1.0;
        return Math.max(0.0, Math.min(1.0, setting));
    }

    /** Ki damage with the mastered bonus, a share added on top. */
    public static float damage(float amount, boolean mastered, double bonus) {
        if (!mastered || !Double.isFinite(bonus) || bonus <= 0 || !Float.isFinite(amount) || amount <= 0) return amount;
        return (float) (amount * (1.0 + Math.min(bonus, MAX_BONUS)));
    }
}
