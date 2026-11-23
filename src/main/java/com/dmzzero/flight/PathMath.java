package com.dmzzero.flight;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// the blocks a flyer's body will pass through in the next few ticks
public final class PathMath {
    public static final double MAX_LENGTH = 12.0;
    private static final double STEP = 0.25;
    private static final double EDGE = 1e-6;
    private static final int MAX_PING_TICKS = 10;

    private PathMath() {
    }

    // feet at x, y, z; nearest first, each block once, at most cap of them
    public static List<int[]> cells(double x, double y, double z, double dx, double dy, double dz, double length, double halfWidth, double height, int cap) {
        List<int[]> out = new ArrayList<>();
        if (!finite(x, y, z, dx, dy, dz, length, halfWidth, height) || length <= 0 || halfWidth <= 0 || height <= 0 || cap <= 0) return out;
        double norm = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (norm < 1e-9) return out;
        dx /= norm;
        dy /= norm;
        dz /= norm;
        length = Math.min(length, MAX_LENGTH);
        Set<Long> seen = new HashSet<>();
        int steps = (int) Math.ceil(length / STEP);
        for (int i = 0; i <= steps; i++) {
            double t = Math.min(length, i * STEP);
            double cx = x + dx * t, cy = y + dy * t, cz = z + dz * t;
            int x0 = floor(cx - halfWidth), x1 = floor(cx + halfWidth - EDGE);
            int y0 = floor(cy), y1 = floor(cy + height - EDGE);
            int z0 = floor(cz - halfWidth), z1 = floor(cz + halfWidth - EDGE);
            for (int bx = x0; bx <= x1; bx++) {
                for (int by = y0; by <= y1; by++) {
                    for (int bz = z0; bz <= z1; bz++) {
                        if (!seen.add(pack(bx, by, bz))) continue;
                        out.add(new int[]{bx, by, bz});
                        if (out.size() >= cap) return out;
                    }
                }
            }
        }
        return out;
    }

    // the server sees a player a tick or two late and the broken blocks take as long to reach them, so it clears ahead
    public static double length(double speed, int aheadTicks) {
        if (!Double.isFinite(speed) || speed <= 0) return 0.0;
        return Math.min(MAX_LENGTH, speed * (Math.max(0, aheadTicks) + 1));
    }

    // the broken blocks reach a laggy player late, so their ping goes on top, a tick per 50 ms and at most 10
    public static int leadTicks(int aheadTicks, int pingMs) {
        int ahead = Math.max(0, aheadTicks);
        if (pingMs <= 0) return ahead;
        return ahead + Math.min(MAX_PING_TICKS, (pingMs + 49) / 50);
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    private static boolean finite(double... values) {
        for (double v : values) if (!Double.isFinite(v)) return false;
        return true;
    }
}
