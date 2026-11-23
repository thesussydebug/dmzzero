package com.dmzzero.village;

// villagers know who the heroes and villains are, from DragonMineZ's alignment
public final class VillageMath {
    public enum Standing { HERO, VILLAIN, NEITHER }

    private VillageMath() {
    }

    public static Standing standing(boolean good, boolean evil) {
        if (good) return Standing.HERO;
        if (evil) return Standing.VILLAIN;
        return Standing.NEITHER;
    }

    /** How much a price of baseCount changes, in items: heroes get discountPct off, villains pay markupPct more, at least one either way. */
    public static int priceChange(int baseCount, double discountPct, Standing standing, double markupPct) {
        if (baseCount <= 0) return 0;
        return switch (standing) {
            case HERO -> -share(baseCount, discountPct, false);
            case VILLAIN -> share(baseCount, markupPct, true);
            case NEITHER -> 0;
        };
    }

    private static int share(int count, double pct, boolean up) {
        if (!Double.isFinite(pct) || pct <= 0) return 0;
        double raw = count * pct / 100.0;
        int n = up ? (int) Math.ceil(raw - 1e-9) : (int) Math.floor(raw + 1e-9);
        return Math.max(1, n);
    }

    public static boolean closeEnoughToFlee(double distanceSq, double range) {
        return Double.isFinite(distanceSq) && distanceSq <= range * range;
    }
}
