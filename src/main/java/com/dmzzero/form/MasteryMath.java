package com.dmzzero.form;

// winning DMZ Zero's fights while in a form pays some of that form's mastery
public final class MasteryMath {
    // dmz's own default when a form doesn't set one
    private static final double DEFAULT_MAX = 100.0;

    private MasteryMath() {
    }

    /** percent of the form's max mastery. */
    public static double amount(Double maxMastery, double percent) {
        double max = maxMastery == null ? DEFAULT_MAX : maxMastery;
        if (!Double.isFinite(max) || !Double.isFinite(percent) || max <= 0 || percent <= 0) return 0;
        return max * percent / 100.0;
    }

    /** lastPaidMs below zero means never. */
    public static boolean ready(long nowMs, long lastPaidMs, int cooldownMinutes) {
        if (lastPaidMs <= 0 || cooldownMinutes <= 0) return true;
        return nowMs - lastPaidMs >= cooldownMinutes * 60_000L;
    }
}
