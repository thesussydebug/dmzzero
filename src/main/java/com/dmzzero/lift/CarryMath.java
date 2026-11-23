package com.dmzzero.lift;

// carrying an animal or a villager over your head
public final class CarryMath {
    /** Sits just above the carrier's head, so it never covers their eyes. */
    public static final double HEAD_GAP = 0.05;

    public enum Kind { ANIMAL, VILLAGER, NONE }

    private CarryMath() {
    }

    public record Ask(boolean animalsOn, boolean villagersOn, Kind kind, boolean ownedByOther, double width, double maxWidth,
                      boolean targetRiding, boolean targetCarrying, boolean carrierRiding, boolean carrierCarrying,
                      double distance, double reach, boolean carrierDown) {
    }

    /** "ok", or why not: kind, off, not_yours, too_big, riding, full, too_far, down. */
    public static String reason(Ask a) {
        if (a.kind() == Kind.NONE) return "kind";
        if (a.kind() == Kind.ANIMAL ? !a.animalsOn() : !a.villagersOn()) return "off";
        if (a.ownedByOther()) return "not_yours";
        if (!Double.isFinite(a.width()) || !Double.isFinite(a.maxWidth()) || a.width() > a.maxWidth()) return "too_big";
        if (a.targetRiding() || a.targetCarrying()) return "riding";
        if (a.carrierRiding() || a.carrierCarrying()) return "full";
        if (!Double.isFinite(a.distance()) || !Double.isFinite(a.reach()) || a.distance() > a.reach()) return "too_far";
        if (a.carrierDown()) return "down";
        return "ok";
    }

    /** Reasons that say nothing and let the click go on to vanilla (a horse's inventory, a villager's trades). */
    public static boolean silent(String why) {
        return "kind".equals(why) || "off".equals(why);
    }

    public static double overhead(double carrierHeight) {
        return carrierHeight + HEAD_GAP;
    }

    /** Room left between the carrier and what they set down. */
    public static final double SET_DOWN_GAP = 0.2;

    /**
     * Where to set it down, as {dx, dz} from the carrier: in front of them and just clear. Vanilla would put it on
     * top of the carrier's head instead.
     */
    public static double[] inFront(float yawDegrees, double carrierWidth, double carriedWidth) {
        if (!Float.isFinite(yawDegrees) || !Double.isFinite(carrierWidth) || !Double.isFinite(carriedWidth)) return new double[]{0, 0};
        double reach = carrierWidth / 2 + carriedWidth / 2 + SET_DOWN_GAP;
        double r = Math.toRadians(yawDegrees);
        return new double[]{-Math.sin(r) * reach, Math.cos(r) * reach};
    }
}
