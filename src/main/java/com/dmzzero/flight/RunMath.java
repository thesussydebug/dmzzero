package com.dmzzero.flight;

import net.minecraft.util.Mth;

/** Pure rules for the dust a fast runner kicks up. */
public final class RunMath {
    // a runner's moves reach the server bunched up (3 ticks of plain sprinting arrived as 0.56 a tick in the dev game), so
    // running speed is averaged over more ticks than flight's
    public static final int SPAN = 8;

    private RunMath() {
    }

    /** 0 below the dust speed, a light puff at it, full at twice it. */
    public static float strength(double speed, double runSpeed) {
        if (!(runSpeed > 0) || !(speed >= runSpeed)) return 0f;
        return (float) Mth.clamp(0.25 + 0.75 * (speed - runSpeed) / runSpeed, 0.0, 1.0);
    }

    /** Particles for one tick of running at this strength. */
    public static int bits(float strength) {
        return strength <= 0f ? 0 : 1 + Math.round(4 * strength);
    }
}
