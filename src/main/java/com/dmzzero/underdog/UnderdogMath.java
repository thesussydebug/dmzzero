package com.dmzzero.underdog;

// beating something stronger than you pays extra TP, more the bigger the gap
public final class UnderdogMath {

    private UnderdogMath() {
    }

    public static double ratio(double theirPower, double yourPower) {
        if (!Double.isFinite(theirPower) || !Double.isFinite(yourPower) || theirPower <= 0 || yourPower <= 0) return 0;
        return theirPower / yourPower;
    }

    /** Extra TP as a share of the kill: perRatio for each time stronger past even, from minRatio, never past max. */
    public static double share(double ratio, double minRatio, double perRatio, double max) {
        if (!Double.isFinite(ratio) || ratio < minRatio || perRatio <= 0) return 0;
        return Math.min(max, perRatio * (ratio - 1));
    }

    public static int tp(int killTp, double share) {
        if (killTp <= 0 || !(share > 0)) return 0;
        return (int) Math.round(killTp * share);
    }
}
