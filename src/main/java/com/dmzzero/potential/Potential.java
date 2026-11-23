package com.dmzzero.potential;

import com.dmzzero.ZeroConfig;

public final class Potential {
    public static final int MIN = 1;
    public static final int MAX = 5;

    public static final double TP_PER_LEVEL = 0.50;
    public static final double MASTERY_PER_LEVEL = 0.50;

    private Potential() {
    }

    public static int roll(double r) {
        if (r < 0.01) return 5;
        if (r < 0.06) return 4;
        if (r < 0.16) return 3;
        if (r < 0.41) return 2;
        return 1;
    }

    public static boolean isValid(int potential) {
        return potential >= MIN && potential <= MAX;
    }

    // the server's [growth.potential] values, or the defaults before a world config is loaded
    public static double tpMultiplier(int potential) {
        return tpMultiplier(potential, ZeroConfig.read(ZeroConfig.SERVER.potentialTp, TP_PER_LEVEL));
    }

    public static double masteryMultiplier(int potential) {
        return masteryMultiplier(potential, ZeroConfig.read(ZeroConfig.SERVER.potentialMastery, MASTERY_PER_LEVEL));
    }

    public static double tpMultiplier(int potential, double perLevel) {
        return isValid(potential) && Double.isFinite(perLevel) ? 1.0 + perLevel * (potential - 1) : 1.0;
    }

    public static double masteryMultiplier(int potential, double perLevel) {
        return isValid(potential) && Double.isFinite(perLevel) ? 1.0 + perLevel * (potential - 1) : 1.0;
    }

    // each level above 1 adds perLevel times the zenkai boost, so at 1.0 Potential N makes it N times stronger
    public static double zenkaiMultiplier(int potential, double perLevel) {
        return isValid(potential) ? 1.0 + perLevel * (potential - 1) : 1.0;
    }

    public static TpScale scaleTp(int gain, double multiplier, double carry) {
        double exact = gain * multiplier + carry;
        if (exact >= Integer.MAX_VALUE) return new TpScale(Integer.MAX_VALUE, 0.0);
        int whole = (int) Math.floor(exact);
        return new TpScale(whole, exact - whole);
    }

    public record TpScale(int gain, double carry) {
    }
}
