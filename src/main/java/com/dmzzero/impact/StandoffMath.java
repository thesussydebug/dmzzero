package com.dmzzero.impact;

public final class StandoffMath {
    public static final double MIN_DISTANCE = 1.5;
    // a standoff takes this long to reach full force, twice as long as a clash
    public static final int FULL_TICKS = 120;

    private StandoffMath() {
    }

    // minecraft yaw 0 looks south (+z) and 90 west (-x)
    public static boolean facing(float yaw, double fromX, double fromZ, double toX, double toZ, double maxAngle) {
        double dx = toX - fromX, dz = toZ - fromZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) return false;
        double rad = Math.toRadians(yaw);
        double lookX = -Math.sin(rad), lookZ = Math.cos(rad);
        return (lookX * dx + lookZ * dz) / len >= Math.cos(Math.toRadians(maxAngle));
    }

    public static boolean faceOff(float yawA, double ax, double az, float yawB, double bx, double bz, double maxAngle) {
        return facing(yawA, ax, az, bx, bz, maxAngle) && facing(yawB, bx, bz, ax, az, maxAngle);
    }

    public static boolean inRange(double distance, double max) {
        return distance >= MIN_DISTANCE && distance <= max;
    }

    public static float push(int age) {
        return Math.min(1f, Math.max(0, age) / (float) FULL_TICKS);
    }

    // interned, so the clash table (keyed by identity) sees one key per pair
    public static String key(String a, String b) {
        return (a.compareTo(b) <= 0 ? "standoff|" + a + "|" + b : "standoff|" + b + "|" + a).intern();
    }
}
