package com.dmzzero.charge;

import com.dmzzero.beam.BeamMath.V;

// overcharging a technique pulls dust and specks of light in toward your hands
public final class GatherMath {
    /** DMZ holds a technique's charge up to 175%. */
    public static final float FULL_AT = 175f;

    private GatherMath() {
    }

    public static float strength(float chargePercent, float startAt) {
        if (!Float.isFinite(chargePercent) || chargePercent <= startAt) return 0f;
        float span = Math.max(1f, FULL_AT - startAt);
        return Math.min(1f, (chargePercent - startAt) / span);
    }

    /** Specks spawned a tick. */
    public static int specks(float strength) {
        if (!(strength > 0)) return 0;
        return 1 + Math.round(3 * Math.min(1f, strength));
    }

    public static V from(V hands, V direction, double radius) {
        double len = direction.length();
        V unit = len < 1e-9 ? new V(0, 1, 0) : direction.scale(1 / len);
        return hands.add(unit.scale(radius));
    }

    public static V velocity(V from, V to, int ticks) {
        if (ticks <= 0) return new V(0, 0, 0);
        return to.sub(from).scale(1.0 / ticks);
    }
}
