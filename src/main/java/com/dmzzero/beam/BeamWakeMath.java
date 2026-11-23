package com.dmzzero.beam;

// a beam fired low over the ground stirs it up along its path, the way fast flight leaves a wake
public final class BeamWakeMath {
    // at most 30 degrees up or down
    private static final double MAX_SLOPE = 0.5;
    // the wake starts a little way out from the caster's hands
    public static final double START = 2.0;

    private BeamWakeMath() {
    }

    public static boolean skims(double dirY) {
        return Math.abs(dirY) <= MAX_SLOPE;
    }

    /** How high above the surface a beam of this size still throws things up, in blocks. */
    public static double reach(float size) {
        float s = Float.isFinite(size) ? Math.max(0.5f, Math.min(4f, size)) : 0.5f;
        return 3 + 2 * s;
    }

    /** 1 right at the surface, fading to 0 at the reach; nothing below the ground. */
    public static float strength(double height, float size) {
        if (Double.isNaN(height) || height < -0.5) return 0f;
        double s = 1 - Math.max(0, height) / reach(size);
        return (float) Math.max(0, Math.min(1, s));
    }

    /** How many spots along a beam this long are checked. */
    public static int samples(double length, double spacing, int max) {
        if (!(length >= START) || !(spacing > 0)) return 0;
        return (int) Math.min(max, Math.floor((length - START) / spacing) + 1);
    }

    /** How far along the beam the i-th spot is (counted from 1). */
    public static double sampleAt(int i, double spacing) {
        return START + (i - 1) * spacing;
    }

    /** A horizontal unit vector at right angles to the beam. */
    public static double[] side(double dx, double dz) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) return new double[]{1, 0};
        return new double[]{-dz / len, dx / len};
    }
}
