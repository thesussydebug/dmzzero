package com.dmzzero.kishare;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// a thrown ball of ki: how much it carries and how it finds its catcher
public final class TossMath {
    public static final double SPEED = 1.2;
    public static final double CATCH = 1.2;
    public static final int LIFE = 60;
    private static final double STEER = 0.35;

    private TossMath() {
    }

    public static float amount(float maxKi, float currentKi, double share) {
        if (currentKi <= 0) return 0f;
        return (float) Math.min(currentKi, maxKi * share);
    }

    public static boolean caught(double distance, double radius) {
        return distance <= radius;
    }

    public static final class Orb {
        public double x, y, z;
        public double vx, vy, vz;

        public Orb(double x, double y, double z, double vx, double vy, double vz) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
        }

        // steers toward the target, slowing down so it never flies past it
        public void step(double tx, double ty, double tz, double speed) {
            double dx = tx - x, dy = ty - y, dz = tz - z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 1e-9) return;
            double want = Math.min(speed, d) / d;
            vx += (dx * want - vx) * STEER;
            vy += (dy * want - vy) * STEER;
            vz += (dz * want - vz) * STEER;
            double v = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double cap = Math.min(speed, d);
            if (v > cap) {
                vx *= cap / v;
                vy *= cap / v;
                vz *= cap / v;
            }
            x += vx;
            y += vy;
            z += vz;
        }

        public double distanceTo(double tx, double ty, double tz) {
            double dx = tx - x, dy = ty - y, dz = tz - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    public static final class Cooldowns {
        private final Map<UUID, Long> until = new HashMap<>();

        // a cooldown outlives a logout, so relogging doesn't skip it; the ones that have run out go when a new one starts
        public void start(UUID id, long now, long ticks) {
            until.values().removeIf(end -> end <= now);
            until.put(id, now + ticks);
        }

        public int size() {
            return until.size();
        }

        public long remaining(UUID id, long now) {
            Long end = until.get(id);
            return end == null ? 0 : Math.max(0, end - now);
        }

        public void clear() {
            until.clear();
        }
    }
}
