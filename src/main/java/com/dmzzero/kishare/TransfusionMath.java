package com.dmzzero.kishare;

// how fast donors fill a knocked-down player back up
public final class TransfusionMath {
    public static final int MAX_DONORS = 5;
    public static final double TOSS_SECONDS = 2.0;
    public static final String FILLED = "§a";
    public static final String EMPTY = "§7";
    private static final double EPS = 1e-6;

    private TransfusionMath() {
    }

    public static double perTick(int donors, double oneDonorSeconds) {
        return perTick(donors, oneDonorSeconds, MAX_DONORS);
    }

    public static double perTick(int donors, double oneDonorSeconds, int maxDonors) {
        if (donors <= 0) return 0;
        return Math.min(donors, Math.max(1, maxDonors)) / (oneDonorSeconds * 20.0);
    }

    public static double tossBurst(double oneDonorSeconds) {
        return Math.min(1.0, TOSS_SECONDS / oneDonorSeconds);
    }

    public static String bar(double progress, int width) {
        int filled = (int) Math.floor(Math.max(0, Math.min(1, progress)) * width + EPS);
        return FILLED + "|".repeat(filled) + EMPTY + "|".repeat(width - filled);
    }

    public static final class Progress {
        private double value;
        private boolean done;

        // true on the tick it fills
        public boolean add(double amount) {
            if (done) return false;
            value = Math.min(1.0, value + amount);
            if (value < 1.0 - EPS) return false;
            value = 1.0;
            done = true;
            return true;
        }

        public double value() {
            return value;
        }

        public int percent() {
            return (int) Math.floor(value * 100 + 1e-9);
        }

        public boolean done() {
            return done;
        }
    }
}
