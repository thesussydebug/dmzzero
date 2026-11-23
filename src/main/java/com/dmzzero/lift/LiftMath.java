package com.dmzzero.lift;

// giving a friend a lift: they ride on your back while you fly
public final class LiftMath {
    public static final double REACH = 4.0;
    /** After the crouch-click, how long you have to stand back up and climb on. */
    public static final int WAIT_TICKS = 60;
    /** How long the carrier crouches on the ground to set you down. */
    public static final int SET_DOWN_TICKS = 10;

    private LiftMath() {
    }

    public record Ask(boolean enabled, boolean sameParty, boolean clickerRiding, boolean carrierCarrying, boolean carrierRiding,
                      double distance, boolean clickerDown, boolean carrierDown) {
    }

    public static String reason(Ask a) {
        return reason(a, REACH);
    }

    /** "ok", or why not: off, not_party, riding, full, too_far, down. */
    public static String reason(Ask a, double reach) {
        if (!a.enabled()) return "off";
        if (!a.sameParty()) return "not_party";
        if (a.clickerRiding()) return "riding";
        if (a.carrierCarrying() || a.carrierRiding()) return "full";
        if (!Double.isFinite(a.distance()) || !Double.isFinite(reach) || a.distance() > reach) return "too_far";
        if (a.clickerDown() || a.carrierDown()) return "down";
        return "ok";
    }

    public static boolean stillWaiting(long askedAt, long now) {
        return now - askedAt <= WAIT_TICKS;
    }

    public static boolean setsDown(boolean onGround, int crouchTicks) {
        return onGround && crouchTicks >= SET_DOWN_TICKS;
    }

    public static boolean needsSoftLanding(boolean onGround, double heightAboveGround) {
        return !onGround;
    }
}
