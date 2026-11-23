package com.dmzzero.light;

import java.util.Arrays;

// how bright ki blasts and beams shine. sources are packed x, y, z, level like the aura's (AuraLight.STRIDE)
public final class KiLightMath {
    private static final int DARK_DIMMING = 3;
    private static final double DARK_LUMA = 0.15;

    private KiLightMath() {
    }

    /** A ki ball's light by its size: dmz's small ball is 3, medium 10, giant 17.5. */
    public static int ballLevel(float size) {
        if (!(size > 0)) return 0;
        if (size < 5) return 10;
        if (size < 12) return 12;
        if (size < 25) return 14;
        return 15;
    }

    /** A wave, laser or beam's light by its size (dmz's default is 1). */
    public static int beamLevel(float size) {
        if (!(size > 0)) return 0;
        int level = 13;
        if (size >= 1.5f) level++;
        if (size >= 2.5f) level++;
        return Math.min(15, level);
    }

    /** Ki that is nearly black glows dimmer, the way dark auras do. */
    public static int dimmed(int level, int rgb) {
        double r = (rgb >> 16 & 0xFF) / 255.0, g = (rgb >> 8 & 0xFF) / 255.0, b = (rgb & 0xFF) / 255.0;
        double luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return luma < DARK_LUMA ? Math.max(0, level - DARK_DIMMING) : level;
    }

    /** How far along a beam its lights sit: from where it starts, spread over the whole length, at most max of them. */
    public static double[] beamPoints(double length, int max, double minSpacing) {
        if (!(length > 0) || max <= 1) return new double[]{0};
        double spacing = Math.max(minSpacing, length / max);
        int n = Math.min(max, (int) Math.floor(length / spacing) + 1);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = i * spacing;
        return out;
    }

    /** The max sources nearest the given point, nearest first. */
    public static float[] nearest(float[] sources, double x, double y, double z, int max) {
        int n = sources.length / AuraLight.STRIDE;
        if (n <= max) return sources;
        Integer[] order = new Integer[n];
        double[] d2 = new double[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
            int k = i * AuraLight.STRIDE;
            double dx = sources[k] - x, dy = sources[k + 1] - y, dz = sources[k + 2] - z;
            d2[i] = dx * dx + dy * dy + dz * dz;
        }
        Arrays.sort(order, (a, b) -> Double.compare(d2[a], d2[b]));
        float[] out = new float[max * AuraLight.STRIDE];
        for (int i = 0; i < max; i++) System.arraycopy(sources, order[i] * AuraLight.STRIDE, out, i * AuraLight.STRIDE, AuraLight.STRIDE);
        return out;
    }
}
