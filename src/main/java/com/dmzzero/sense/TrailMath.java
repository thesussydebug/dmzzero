package com.dmzzero.sense;

import java.util.ArrayDeque;
import java.util.Deque;

// ki trails: with ki sense up you see where a fighter flew in the last five seconds
public final class TrailMath {
    public static final int LIFE_TICKS = 100;
    public static final int MAX_POINTS = 60;
    private static final double MIN_STEP_SQ = 1.0;
    // blocks per tick, squared: about 18 blocks a second
    private static final double MIN_SPEED_SQ = 0.81;

    private TrailMath() {
    }

    public record Point(long tick, double x, double y, double z) {
    }

    public static final class Trail {
        private final Deque<Point> points = new ArrayDeque<>();

        /** Adds a point unless it's too close to the last one. Returns whether it was added. */
        public boolean add(long tick, double x, double y, double z) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
            Point last = points.peekLast();
            if (last != null) {
                double dx = x - last.x(), dy = y - last.y(), dz = z - last.z();
                if (dx * dx + dy * dy + dz * dz < MIN_STEP_SQ) return false;
            }
            points.addLast(new Point(tick, x, y, z));
            while (points.size() > MAX_POINTS) points.removeFirst();
            return true;
        }

        public void prune(long now) {
            while (!points.isEmpty() && now - points.peekFirst().tick() > LIFE_TICKS) points.removeFirst();
        }

        public int size() {
            return points.size();
        }

        public Iterable<Point> points() {
            return points;
        }
    }

    public static float fade(long age) {
        if (age <= 0) return 1f;
        if (age >= LIFE_TICKS) return 0f;
        return 1f - (float) age / LIFE_TICKS;
    }

    public static boolean leavesTrail(boolean onGround, double speedSq) {
        return !onGround && Double.isFinite(speedSq) && speedSq >= MIN_SPEED_SQ;
    }
}
