package com.dmzzero.impact;

public record ImpactProfile(int tier, int flags) {
    public static final int FLICKER = 1;
    public static final int LIGHTNING = 1 << 1;
    public static final int CRACKS = 1 << 2;
    public static final int QUAKE = 1 << 3;
    public static final int LIGHT = 1 << 4;
    public static final int SHOCKWAVE = 1 << 5;
    public static final int DARK = 1 << 6;
    public static final int CALM = 1 << 7;

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public boolean destructive() {
        return !has(CALM);
    }

    public static ImpactProfile strongest(ImpactProfile a, ImpactProfile b) {
        if (a == null) return b;
        if (b == null) return a;
        int calm = a.flags & b.flags & CALM;
        return new ImpactProfile(Math.max(a.tier, b.tier), ((a.flags | b.flags) & ~CALM) | calm);
    }
}
