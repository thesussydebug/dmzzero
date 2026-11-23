package com.dmzzero.transmission;

import java.util.Comparator;
import java.util.List;

/** Pure maths for a hand on the shoulder: who goes along, how many the traveller can pay for, and where they land. */
public final class ShoulderMath {
    public static final double MIN_GAP = 0.7;

    private ShoulderMath() {
    }

    /** Someone next to the traveller. ok is false for anyone who can't go (dead, in a duel or battle, riding, blocked). */
    public record Near(int id, double distance, boolean crouching, boolean sameParty, boolean ok) {
    }

    /** Offset from the traveller's arrival, on the ground. */
    public record Spot(double x, double z) {
    }

    /** The ids of those who go along: crouching party members within reach, nearest first, at most most of them. */
    public static List<Integer> pick(List<Near> near, double reach, int most) {
        return near.stream()
                .filter(n -> n.crouching() && n.sameParty() && n.ok() && Double.isFinite(n.distance()) && n.distance() <= reach)
                .sorted(Comparator.comparingDouble(Near::distance))
                .limit(Math.max(0, most))
                .map(Near::id)
                .toList();
    }

    /** Shift is holding on only on foot: while flying or gliding it just takes you down. */
    public static boolean holdsOn(boolean shift, boolean flying, boolean gliding) {
        return shift && !flying && !gliding;
    }

    /** Whether dmz would send this player straight back to its Otherworld after the trip: dead, not back for a while,
     *  not in creative or spectator, going somewhere outside the Otherworld, with the Otherworld switched on. */
    public static boolean keptInOtherworld(boolean otherworldOn, boolean alive, int returnTimer, boolean creativeOrSpectator, boolean toOtherworld) {
        return otherworldOn && !alive && returnTimer <= 0 && !creativeOrSpectator && !toOtherworld;
    }

    /** How many of wanted the traveller's ki pays for at costEach. */
    public static int affordable(int wanted, double ki, double costEach) {
        if (wanted <= 0 || !Double.isFinite(costEach)) return 0;
        if (costEach <= 0) return wanted;
        if (!Double.isFinite(ki) || ki <= 0) return 0;
        return (int) Math.min(wanted, Math.floor(ki / costEach));
    }

    /** Where someone who stood at (dx, dy, dz) from the traveller lands: the same side, no closer than MIN_GAP or farther than reach. */
    public static Spot beside(double dx, double dy, double dz, double reach) {
        double x = Double.isFinite(dx) ? dx : 0, z = Double.isFinite(dz) ? dz : 0;
        double len = Math.hypot(x, z);
        double want = Math.max(MIN_GAP, Math.min(len, Math.max(MIN_GAP, reach)));
        // stood right on them: any side will do
        if (len < 1e-6) return new Spot(want, 0);
        return new Spot(x / len * want, z / len * want);
    }
}
