package com.dmzzero.flight;

// taking off and landing, read from where a player's feet are each tick. the server and the player's own client both use it
public final class TouchdownMath {
    // flight that speeds up later than this after leaving the ground is just flight
    public static final int TAKEOFF_WINDOW = 8;
    public static final int LANDING_COOLDOWN = 10;
    private static final double RISING = 0.15;
    private static final int FALL_SPAN = 3;

    public enum Event { NONE, TAKEOFF, LANDING }

    private TouchdownMath() {
    }

    public static final class Feet {
        private boolean known;
        private double lastX, lastY, lastZ;
        private boolean ground;
        private boolean launched;
        private long leftAt;
        private long landedAt = Long.MIN_VALUE / 2;
        // the drops of the last few ticks: the server doesn't get a position every tick, and a quiet one reads as no fall
        private final double[] falls = new double[FALL_SPAN];
        private int fallAt;
        private double speed;
        private double groundX, groundY, groundZ;

        public Event step(double x, double y, double z, boolean onGround, boolean flying, long now, double takeoffSpeed, double landingSpeed) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return Event.NONE;
            if (!known) {
                known = true;
                ground = onGround;
                launched = !onGround;
                move(x, y, z, onGround, now);
                return Event.NONE;
            }
            double dx = x - lastX, dy = y - lastY, dz = z - lastZ;
            double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (moved > FlightMath.TELEPORT) {
                ground = onGround;
                launched = !onGround;
                java.util.Arrays.fill(falls, 0);
                move(x, y, z, onGround, now);
                return Event.NONE;
            }
            Event event = Event.NONE;
            if (onGround) {
                // the last move stops short on the ground, so the drops just before count too
                double hit = Math.max(fall(), -dy);
                if (!ground && hit >= landingSpeed && now - landedAt >= LANDING_COOLDOWN) {
                    event = Event.LANDING;
                    speed = hit;
                    landedAt = now;
                }
                ground = true;
                launched = false;
                java.util.Arrays.fill(falls, 0);
                move(x, y, z, true, now);
                return event;
            }
            if (ground) {
                ground = false;
                leftAt = now - 1;
            }
            if (flying && !launched && now - leftAt <= TAKEOFF_WINDOW && dy > RISING && moved >= takeoffSpeed) {
                event = Event.TAKEOFF;
                speed = moved;
                launched = true;
            }
            fallAt = (fallAt + 1) % falls.length;
            falls[fallAt] = Math.max(0, -dy);
            move(x, y, z, false, now);
            return event;
        }

        private double fall() {
            double most = 0;
            for (double f : falls) most = Math.max(most, f);
            return most;
        }

        private void move(double x, double y, double z, boolean onGround, long now) {
            lastX = x;
            lastY = y;
            lastZ = z;
            if (onGround) {
                groundX = x;
                groundY = y;
                groundZ = z;
                leftAt = now;
            }
        }

        // how fast the last takeoff or landing was, in blocks a tick
        public double speed() {
            return speed;
        }

        // where the feet last stood, for a takeoff's dust
        public double groundX() {
            return groundX;
        }

        public double groundY() {
            return groundY;
        }

        public double groundZ() {
            return groundZ;
        }
    }

    // dmz's fly toggle grants vanilla's mayfly, so a dmz flyer can be in vanilla's flying state too; only flight without dmz's is left out
    public static boolean tracks(boolean spectator, boolean passenger, boolean inWater, boolean vanillaFlying, boolean flySkill) {
        return !spectator && !passenger && !inWater && (!vanillaFlying || flySkill);
    }

    // 0 below the threshold, a quarter at it, all of it at twice the threshold
    public static float strength(double speed, double threshold) {
        if (!Double.isFinite(speed) || !(threshold > 0) || speed < threshold) return 0f;
        return (float) Math.min(1.0, 0.25 + 0.75 * (speed - threshold) / threshold);
    }

    public static int crouchTicks(float strength) {
        return 4 + Math.round(6 * clamp(strength));
    }

    public static double ringRadius(float strength) {
        return 1.5 + 2.0 * clamp(strength);
    }

    private static float clamp(float s) {
        return Float.isFinite(s) ? Math.max(0f, Math.min(1f, s)) : 0f;
    }
}
