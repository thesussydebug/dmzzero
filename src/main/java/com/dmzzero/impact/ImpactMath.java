package com.dmzzero.impact;

public final class ImpactMath {
    public static final int MAX_CRACKS = 24;
    public static final int AURA_RAMP_TICKS = 120;

    private ImpactMath() {
    }

    // 0 keeps the old look, 1 shows the new one; flashes get longer and closer together as the charge fills
    public static float flicker(float factor, double time) {
        if (factor <= 0.1f) return Math.max(0f, factor);
        if (factor >= 0.98f) return 1f;
        double period = 10.0 - 7.0 * factor;
        double cycles = time / period;
        long cycle = (long) Math.floor(cycles);
        boolean skipped = unit(cycle) > 0.35 + factor;
        if (!skipped && cycles - cycle < 0.25 + 0.45 * factor) return 1f;
        return factor * factor * 0.6f;
    }

    private static double unit(long n) {
        long z = n * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }

    // negative so it never matches a real entity breaking a block
    public static int crackId(int entityId, int slot) {
        return -(1_000_000 + (Math.floorMod(entityId, 80_000_000)) * MAX_CRACKS + slot);
    }

    public static int maxCracks(int tier) {
        return 4 + 3 * tier;
    }

    public static int crackStageCap(float progress) {
        return Math.min(9, (int) (Math.max(0f, progress) * 10f));
    }

    public static double crackRadius(int tier) {
        return 2.0 + tier;
    }

    public static double shockwaveRadius(int tier) {
        return 3.0 + 1.5 * tier;
    }

    public static double breakRadius(int tier) {
        return 1.5 + tier;
    }

    public static double lightningChance(int tier, float progress) {
        return (0.006 + 0.004 * tier) * (0.5 + progress);
    }

    public static float auraIntensity(int chargeTicks) {
        return Math.min(1f, Math.max(0, chargeTicks) / (float) AURA_RAMP_TICKS);
    }

    public static float falloff(double distance, double range) {
        if (distance >= range) return 0f;
        float f = (float) (1.0 - Math.max(0.0, distance) / range);
        return f * f;
    }

    // degrees of camera shake at full charge, right next to the player
    public static float quakeAmplitude(int tier) {
        return 0.3f * Math.max(0, tier - 1);
    }

    // dmz's default max mastery is 100
    public static double masteryRatio(double mastery, Double max) {
        double cap = max == null ? 100.0 : max;
        if (cap <= 0) return 0;
        return Math.max(0, Math.min(1, mastery / cap));
    }

    // how stormy the sky gets around a transformation, tier 4 and up only
    public static float stormTarget(int tier, float factor) {
        if (tier < 4) return 0f;
        float f = Math.max(0f, Math.min(1f, factor));
        return (tier >= 5 ? 1f : 0.7f) * f;
    }

    public static float thunder(float storm, int tier) {
        return tier >= 5 ? storm : storm * 0.5f;
    }

    // clouds gather fast and clear slowly
    public static float easeStorm(float storm, float target) {
        float next = storm + (target - storm) * (target > storm ? 0.08f : 0.03f);
        return Math.abs(next - target) < 0.001f ? target : next;
    }

    // npcs have no form profile, they clash like a tier 3 form
    public static int clashTier(int... tiers) {
        int tier = tiers.length == 0 ? 3 : 1;
        for (int t : tiers) tier = Math.max(tier, t);
        return Math.max(1, Math.min(5, tier));
    }

    public static double clashRadius(int tier, int age) {
        return Math.min(2.0 + tier, 1.0 + age / 30.0);
    }
}
