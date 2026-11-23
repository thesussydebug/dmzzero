package com.dmzzero.light;

import net.minecraft.util.Mth;

// how bright an aura shines and how far; sources are packed x, y, z, level so the chunk builder threads can read a snapshot
public final class AuraLight {
    public static final int STRIDE = 4;
    private static final int BASE = 10;
    private static final int DARK_DIMMING = 3;

    private AuraLight() {
    }

    public static int level(int tier, boolean dark) {
        int level = BASE + Math.max(1, tier);
        if (dark) level -= DARK_DIMMING;
        return Mth.clamp(level, 0, 15);
    }

    public static int lightAt(float[] sources, int x, int y, int z) {
        if (sources == null) return 0;
        double cx = x + 0.5, cy = y + 0.5, cz = z + 0.5;
        int best = 0;
        for (int i = 0; i + STRIDE - 1 < sources.length; i += STRIDE) {
            float level = sources[i + 3];
            if (level <= best) continue;
            double dx = cx - sources[i], dy = cy - sources[i + 1], dz = cz - sources[i + 2];
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 >= level * level) continue;
            int light = (int) level - (int) Math.sqrt(d2);
            if (light > best) best = light;
        }
        return best;
    }

    // packed the way LightTexture does: block light in bits 4-7, sky light from bit 20
    public static int pack(int block, int sky) {
        return block << 4 | sky << 20;
    }

    public static int brighten(int packed, int light) {
        int block = packed >> 4 & 0xFFFF;
        if (light <= block) return packed;
        return packed & ~(0xFFFF << 4) | light << 4;
    }

    public static int minSection(double center, int level) {
        return Mth.floor((center - level) / 16.0);
    }

    public static int maxSection(double center, int level) {
        return Mth.floor((center + level) / 16.0);
    }

    // the same blocks lit the same way, so the chunks don't need rebuilding
    public static boolean same(float[] a, float[] b) {
        if (a == b) return true;
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i += STRIDE) {
            if (Mth.floor(a[i]) != Mth.floor(b[i]) || Mth.floor(a[i + 1]) != Mth.floor(b[i + 1])
                    || Mth.floor(a[i + 2]) != Mth.floor(b[i + 2]) || a[i + 3] != b[i + 3]) return false;
        }
        return true;
    }
}
