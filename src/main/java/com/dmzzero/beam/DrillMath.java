package com.dmzzero.beam;

import java.util.ArrayList;
import java.util.List;

// the special beam cannon bores a one-block tunnel along its own line
public final class DrillMath {
    public static final int MAX_CELLS = 64;

    private DrillMath() {
    }

    /** DMZ's player Special Beam Cannon is "makkanko"; its saga fighters' version is named "makkankosanpo". */
    public static boolean drills(String techniqueId) {
        return "makkanko".equals(techniqueId) || "makkankosanpo".equals(techniqueId);
    }

    /** The block cells a ray from (x, y, z) passes through, in order, up to max. A voxel walk, so no corner is skipped. */
    public static List<int[]> cells(double x, double y, double z, double dx, double dy, double dz, int max) {
        List<int[]> out = new ArrayList<>();
        int n = Math.min(max, MAX_CELLS);
        if (n <= 0 || !finite(x, y, z) || !finite(dx, dy, dz) || (dx == 0 && dy == 0 && dz == 0)) return out;
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
        int sx = (int) Math.signum(dx), sy = (int) Math.signum(dy), sz = (int) Math.signum(dz);
        double tdx = step(dx), tdy = step(dy), tdz = step(dz);
        double tmx = next(x, cx, sx, tdx), tmy = next(y, cy, sy, tdy), tmz = next(z, cz, sz, tdz);
        out.add(new int[]{cx, cy, cz});
        while (out.size() < n) {
            if (tmx <= tmy && tmx <= tmz) {
                cx += sx;
                tmx += tdx;
            } else if (tmy <= tmz) {
                cy += sy;
                tmy += tdy;
            } else {
                cz += sz;
                tmz += tdz;
            }
            out.add(new int[]{cx, cy, cz});
        }
        return out;
    }

    /** A block gives way when it has a hardness at all (bedrock has none) and no more than the limit. */
    public static boolean breaks(float hardness, double maxHardness) {
        return Float.isFinite(hardness) && hardness >= 0 && hardness <= maxHardness;
    }

    private static double step(double d) {
        return d == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / d);
    }

    private static double next(double p, int cell, int sign, double step) {
        if (sign == 0) return Double.POSITIVE_INFINITY;
        return (sign > 0 ? cell + 1 - p : p - cell) * step;
    }

    private static boolean finite(double a, double b, double c) {
        return Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(c);
    }
}
