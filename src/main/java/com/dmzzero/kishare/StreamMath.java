package com.dmzzero.kishare;

// arcs of light between two points, aura colors, and how a crowd of donors shakes the ground
public final class StreamMath {
    private static final double MAX_ARC = 3.0;
    private static final float MAX_TINT = 0.6f;
    private static final int GROUP_RAMP = 100;

    private StreamMath() {
    }

    // straight from a to b with a parabola on top, arc blocks high in the middle
    public static double[] point(double ax, double ay, double az, double bx, double by, double bz, double t, double arc) {
        double lift = 4.0 * arc * t * (1.0 - t);
        return new double[]{ax + (bx - ax) * t, ay + (by - ay) * t + lift, az + (bz - az) * t};
    }

    public static double arc(double length) {
        return Math.min(MAX_ARC, Math.max(0.0, length) * 0.12);
    }

    public static float[] rgb(int color) {
        return new float[]{((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f};
    }

    public static int hex(String color, int fallback) {
        if (color == null) return fallback;
        String s = color.startsWith("#") ? color.substring(1) : color;
        if (s.length() != 6) return fallback;
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static int pack(float[] c) {
        return channel(c[0]) << 16 | channel(c[1]) << 8 | channel(c[2]);
    }

    private static int channel(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    // the receiver keeps their own aura, more donors pull it further toward theirs
    public static float[] tint(float[] base, float[][] donors) {
        if (donors.length == 0) return base.clone();
        float[] avg = new float[3];
        for (float[] d : donors) {
            for (int i = 0; i < 3; i++) avg[i] += d[i] / donors.length;
        }
        float f = (float) (MAX_TINT * (1.0 - Math.pow(MAX_TINT, donors.length)));
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) out[i] = base[i] + (avg[i] - base[i]) * f;
        return out;
    }

    // how hard the ground reacts to a crowd giving one player ki
    public static int groupTier(int donors) {
        if (donors < 3) return 0;
        return Math.min(4, donors - 1);
    }

    public static float groupProgress(int ticks) {
        return Math.max(0f, Math.min(1f, ticks / (float) GROUP_RAMP));
    }
}
