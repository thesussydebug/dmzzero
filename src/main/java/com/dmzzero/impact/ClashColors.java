package com.dmzzero.impact;

// a clash shows both sides' colors: each one's sparks on its own side of the clash point, blended in the middle
public final class ClashColors {
    private ClashColors() {
    }

    /** The point moved dist toward the side; a side right on the point leaves it where it is. */
    public static double[] toward(double[] point, double[] side, double dist) {
        double dx = side[0] - point[0], dy = side[1] - point[1], dz = side[2] - point[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) return new double[]{point[0], point[1], point[2]};
        double s = dist / len;
        return new double[]{point[0] + dx * s, point[1] + dy * s, point[2] + dz * s};
    }

    /** a at 0, b at 1. */
    public static int mix(int a, int b, float t) {
        float u = Math.max(0f, Math.min(1f, t));
        int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * u);
        int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * u);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * u);
        return r << 16 | g << 8 | bl;
    }

    /** Vanilla's colored dust size, bigger in stronger clashes. */
    public static float dustSize(int tier) {
        return 1.0f + 0.25f * Math.max(0, Math.min(5, tier));
    }
}
