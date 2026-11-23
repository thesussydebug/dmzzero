package com.dmzzero.beam;

import com.dmzzero.beam.BeamMath.V;

// how hard a beam pushes the one firing it, back along the way it went
public final class RecoilMath {
    /** Blocks a tick a normal-sized, fully charged wave pushes you: fired straight down it lifts you about 5 blocks. */
    public static final double BASE = 0.9;
    public static final double MIN_SIZE_FACTOR = 0.5;
    public static final double MAX_SIZE_FACTOR = 2.5;
    // standing on the ground you slide back this share of what you'd fly in the air
    public static final double GROUND_GRIP = 0.45;
    public static final double MAX_UP = 1.8;
    public static final double MAX_DOWN = 1.2;
    public static final double MAX_SIDE = 2.5;

    private RecoilMath() {
    }

    /** How much a ki attack type kicks back, by DMZ's KiType name: waves fully, beams and lasers less, balls and the rest not at all. */
    public static double kick(String kiType) {
        if (kiType == null) return 0;
        return switch (kiType) {
            case "WAVE" -> 1.0;
            case "BEAM" -> 0.8;
            case "LASER" -> 0.35;
            default -> 0;
        };
    }

    public static double sizeFactor(float size, float defaultSize) {
        if (!(defaultSize > 0) || !Float.isFinite(size)) return 1.0;
        return Math.max(MIN_SIZE_FACTOR, Math.min(MAX_SIZE_FACTOR, size / (double) defaultSize));
    }

    /** DMZ's charge multiplier is the charge percent / 100; an instant shot still kicks a little, an overcharge more. */
    public static double chargeFactor(float charge) {
        if (!Float.isFinite(charge)) return 1.0;
        double c = Math.max(0, Math.min(2, charge));
        return 0.4 + 0.6 * c;
    }

    public static double strength(String kiType, float size, float defaultSize, float charge, double configStrength) {
        if (!(configStrength > 0)) return 0;
        return BASE * kick(kiType) * sizeFactor(size, defaultSize) * chargeFactor(charge) * configStrength;
    }

    /** The push for a beam fired along look. On the ground it can't push you down, and your feet hold you back. */
    public static V push(V look, double strength, boolean onGround) {
        if (!(strength > 0) || !finite(look)) return new V(0, 0, 0);
        V dir = look.normalize();
        if (dir.length() < 0.5) return new V(0, 0, 0);
        double x = -dir.x() * strength, y = -dir.y() * strength, z = -dir.z() * strength;
        if (onGround) {
            y = Math.max(0, y);
            x *= GROUND_GRIP;
            z *= GROUND_GRIP;
        }
        y = Math.max(-MAX_DOWN, Math.min(MAX_UP, y));
        double side = Math.hypot(x, z);
        if (side > MAX_SIDE) {
            x *= MAX_SIDE / side;
            z *= MAX_SIDE / side;
        }
        return new V(x, y, z);
    }

    private static boolean finite(V v) {
        return Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z());
    }
}
