package com.dmzzero.flight;

import net.minecraft.util.Mth;

public final class FlightMath {
    // more than this in one tick is a teleport, not flight
    public static final double TELEPORT = 8.0;
    public static final int BOOM_COOLDOWN = 60;
    // slow down past this share of the boom speed and the next one can go off
    static final double REARM = 0.8;
    private static final int SPAN = 3;

    private FlightMath() {
    }

    // the last few server positions; the client sends one a tick, but late packets bunch up, so speed is averaged
    public static final class Track {
        private final double[] xs;
        private final double[] ys;
        private final double[] zs;
        private int count;
        private int head = -1;

        public Track() {
            this(SPAN);
        }

        /** Speed averaged over this many ticks. */
        public Track(int span) {
            xs = new double[span + 1];
            ys = new double[span + 1];
            zs = new double[span + 1];
        }

        public void add(double x, double y, double z) {
            if (count > 0) {
                double dx = x - xs[head], dy = y - ys[head], dz = z - zs[head];
                if (dx * dx + dy * dy + dz * dz > TELEPORT * TELEPORT) count = 0;
            }
            head = (head + 1) % xs.length;
            xs[head] = x;
            ys[head] = y;
            zs[head] = z;
            count = Math.min(count + 1, xs.length);
        }

        public void reset() {
            count = 0;
        }

        private int oldest() {
            return Math.floorMod(head - (count - 1), xs.length);
        }

        public double speed() {
            if (count < 2) return 0.0;
            int o = oldest();
            double dx = xs[head] - xs[o], dy = ys[head] - ys[o], dz = zs[head] - zs[o];
            return Math.sqrt(dx * dx + dy * dy + dz * dz) / (count - 1);
        }

        public double[] direction() {
            if (count < 2) return new double[]{0, 0, 0};
            int o = oldest();
            double dx = xs[head] - xs[o], dy = ys[head] - ys[o], dz = zs[head] - zs[o];
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-9) return new double[]{0, 0, 0};
            return new double[]{dx / len, dy / len, dz / len};
        }
    }

    // goes off once on breaking through the boom speed, then only after slowing down well below it and a cooldown
    public static final class Boom {
        private boolean armed = true;
        private long readyAt;

        public boolean update(double speed, double boomSpeed, long now) {
            if (speed < boomSpeed * REARM) armed = true;
            if (!armed || speed < boomSpeed || now < readyAt) return false;
            armed = false;
            readyAt = now + BOOM_COOLDOWN;
            return true;
        }
    }

    // 0 when too slow or too high, up to 1 skimming the surface flat out
    public static float wake(double speed, double height, double wakeSpeed, double maxHeight) {
        if (speed < wakeSpeed || height > maxHeight || height < 0) return 0f;
        double fast = Mth.clamp((speed - wakeSpeed) / (wakeSpeed * 1.5) + 0.25, 0.0, 1.0);
        double low = 1.0 - height / (maxHeight + 1.0);
        return (float) Mth.clamp(fast * low, 0.0, 1.0);
    }
}
