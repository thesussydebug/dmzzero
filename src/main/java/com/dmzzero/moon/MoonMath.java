package com.dmzzero.moon;

import com.dmzzero.beam.BeamMath.V;

// where the moon is in vanilla's sky, what it takes to hit it, when it comes back and how it looks blowing up
public final class MoonMath {
    // how far off the moon's middle a shot can be and still hit it; vanilla draws it about 11 degrees wide either way
    public static final double AIM_DEGREES = 8;
    // a moon this close to the horizon is hidden by the land and fog
    static final double MIN_ELEVATION_DEGREES = 5;
    // vanilla's moon phases run 0 (full) to 7; the new moon leaves the sky empty
    public static final int NEW_MOON = 4;
    public static final int BLAST_TICKS = 20;
    public static final float BLAST_SWELL = 1.8f;
    private static final long DAY = 24000L;
    private static final double MINUTE = 1200.0;

    private MoonMath() {
    }

    // vanilla turns the sky by the time of day: the sun at (-sin a, cos a, 0), the moon opposite it. 0 is noon, 0.5 midnight
    public static V direction(float timeOfDay) {
        double a = timeOfDay * Math.PI * 2;
        return new V(Math.sin(a), -Math.cos(a), 0);
    }

    public static boolean hit(V aim, float timeOfDay) {
        return near(aim, timeOfDay, AIM_DEGREES);
    }

    // within degrees of the moon's middle, with the moon clear of the horizon
    public static boolean near(V dir, float timeOfDay, double degrees) {
        if (!(degrees > 0)) return false;
        double length = dir.length();
        if (!(length > 1e-9) || Double.isInfinite(length)) return false;
        V moon = direction(timeOfDay);
        if (moon.y() < Math.sin(Math.toRadians(MIN_ELEVATION_DEGREES))) return false;
        return dir.dot(moon) / length >= Math.cos(Math.toRadians(Math.min(degrees, 180)));
    }

    public static boolean shows(int phase) {
        return Math.floorMod(phase, 8) != NEW_MOON;
    }

    // dmz's attack types strong enough to reach the moon: waves, beams, lasers and the bigger ki balls
    public static boolean destroys(String kiType) {
        if (kiType == null) return false;
        return switch (kiType) {
            case "WAVE", "BEAM", "LASER", "GIANT_BALL", "MEDIUM_BALL" -> true;
            default -> false;
        };
    }

    public static long returnsAt(long now, int days) {
        return now + days * DAY;
    }

    public static long minutesLeft(long now, long returnsAt) {
        return Math.max(0, (long) Math.ceil((returnsAt - now) / MINUTE));
    }

    // vanilla's Level.isNight, worked out the way updateSkyBrightness does. a client only runs that once, when its level loads
    public static boolean night(float timeOfDay, float rain, float thunder) {
        double wet = 1.0 - rain * 5.0f / 16.0;
        double storm = 1.0 - thunder * 5.0f / 16.0;
        double sun = 0.5 + 2.0 * Math.max(-0.25, Math.min(0.25, Math.cos(timeOfDay * Math.PI * 2)));
        int darkness = (int) ((1.0 - sun * wet * storm) * 11.0);
        return darkness >= 4;
    }

    // the half-width vanilla draws the moon at: as it is while it's there, swelling for a second when it blows up, then nothing
    public static float size(float vanilla, boolean gone, int blastAge, float partial) {
        if (!gone) return vanilla;
        if (blastAge < 0 || blastAge >= BLAST_TICKS) return 0f;
        float t = Math.min(1f, (blastAge + partial) / BLAST_TICKS);
        float eased = 1f - (1f - t) * (1f - t);
        return vanilla * (1f + (BLAST_SWELL - 1f) * eased);
    }
}
