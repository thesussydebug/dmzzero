package com.dmzzero.smash;

import com.dmzzero.beam.BeamMath.V;

// knocked flying into a wall, the ground or a ceiling: when it counts and how hard it lands
public final class SmashMath {
    // the same order as Minecraft's Direction.get3DDataValue
    public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    /** Ticks a launched target is watched for an impact. */
    public static final int WATCH_TICKS = 40;
    // by the time it hits it may have lost this much speed to drag and still count
    private static final double SLOWED_SHARE = 0.6;
    // and it has to stop short: now moving at most this share of the tick before
    private static final double STOP_SHARE = 0.4;

    private SmashMath() {
    }

    public static boolean launches(V knock, double minSpeed) {
        double speed = knock.length();
        return Double.isFinite(speed) && speed >= minSpeed;
    }

    /** before and now are the distances moved in the last two ticks. */
    public static boolean impact(double before, double now, boolean collided, double minSpeed) {
        if (!collided || !Double.isFinite(before) || !Double.isFinite(now)) return false;
        return before >= minSpeed * SLOWED_SHARE && now <= before * STOP_SHARE;
    }

    /**
     * One launched target, a tick at a time. Minecraft only flags a collision on the tick the target pushes into the block,
     * and that tick is often still a long move (it covered most of the way first). The next tick it has stopped, but isn't
     * pushing into anything any more, so the flag is gone. A collision counts for the tick it happened and the one after.
     */
    public static final class Watch {
        public static final int NONE = 0, WALL = 1, FLOOR = 2;
        private double before;
        private double last;
        private boolean wallBefore, floorBefore;
        private double speed;

        public Watch(double launchSpeed) {
            this.last = launchSpeed;
        }

        /** moved: distance covered this tick; wall and floor: this tick's collision flags (floor also means ceiling). */
        public int tick(double moved, boolean wall, boolean floor, double minSpeed) {
            boolean anyWall = wall || wallBefore, anyFloor = floor || floorBefore;
            int hit = NONE;
            if (impact(last, moved, anyWall || anyFloor, minSpeed)) {
                hit = anyWall ? WALL : FLOOR;
                // the tick it touched may already be cut short, so the full speed is one further back
                speed = Math.max(before, last);
            }
            before = last;
            last = moved;
            wallBefore = wall;
            floorBefore = floor;
            return hit;
        }

        /** How fast it was going when it hit. */
        public double speed() {
            return speed;
        }
    }

    /** Which side of the target hit something: a wall the way it was going sideways, otherwise the floor or the ceiling. */
    public static int face(V move, boolean horizontal) {
        if (horizontal) {
            if (Math.abs(move.x()) >= Math.abs(move.z())) return move.x() >= 0 ? EAST : WEST;
            return move.z() >= 0 ? SOUTH : NORTH;
        }
        return move.y() <= 0 ? DOWN : UP;
    }

    /** 1 to 3 by how fast it was going. */
    public static int power(double speed, double minSpeed) {
        if (speed >= minSpeed * 2.5) return 3;
        if (speed >= minSpeed * 1.6) return 2;
        return 1;
    }

    public static int crackCount(int power) {
        return switch (power) {
            case 3 -> 14;
            case 2 -> 9;
            default -> 5;
        };
    }

    public static double crackRadius(int power) {
        return 0.5 + power;
    }

    /** The most fragile blocks one impact breaks. */
    public static int breakCap(int power) {
        return switch (power) {
            case 3 -> 9;
            case 2 -> 5;
            default -> 2;
        };
    }

    /** From power 2 the block it hit gives way too (stone to cobblestone, grass to dirt...). */
    public static boolean degrades(int power) {
        return power >= 2;
    }

    public static float shake(int power) {
        return 0.15f + 0.2f * power;
    }

    // breaker ids past the ones impacts (-1e6 down to -1.921e9) and clashes (-1.93e9 down to -2.13e9) use
    public static int crackId(int n) {
        return -(2_130_000_001 + Math.floorMod(n, 17_000_000));
    }
}
