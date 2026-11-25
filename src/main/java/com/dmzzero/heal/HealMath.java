package com.dmzzero.heal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HealMath {
    private static final int TICKS_PER_MINUTE = 1200;

    private HealMath() {
    }

    /**
     * Whether a block goes back: only while it's still what the damage left (or loose water that flowed in),
     * so nothing built there since is ever overwritten.
     */
    public static boolean restore(int current, int left, int original, boolean currentLooseFluid) {
        if (current == original) return false;
        return current == left || currentLooseFluid;
    }

    public static int perTick(int perSecond) {
        return Math.max(1, (perSecond + 19) / 20);
    }

    /** Blocks per tick: a wished healing runs at the wish rate, never slower than usual. */
    public static int rate(int perSecond, int wishPerSecond, boolean wished) {
        int normal = perTick(perSecond);
        return wished ? Math.max(normal, perTick(wishPerSecond)) : normal;
    }

    public static long due(long now, int minutes) {
        return now + (long) Math.max(0, minutes) * TICKS_PER_MINUTE;
    }

    /** Room for this damage: the list isn't full, or the block is already in it. The one place the limit is decided. */
    public static boolean room(boolean pending, int size, int max) {
        return pending || size < max;
    }

    /**
     * When a wish's fast healing ends: twice the ticks its blocks need at the wish rate, plus a minute, so blocks that can't
     * come back yet don't keep the dimension at the wish rate for good.
     */
    public static long wishEnds(long now, int blocks, int perTick) {
        int rate = Math.max(1, perTick);
        return now + 2L * ((blocks + rate - 1) / rate) + TICKS_PER_MINUTE;
    }

    /** Whether one more block, at its usual cost, would end past the time allowed. */
    public static boolean late(long elapsedNanos, long blockNanos, long allowedNanos) {
        return elapsedNanos + blockNanos >= allowedNanos;
    }

    /** A block's usual cost, moved an eighth of the way to the latest one. */
    public static long blockAverage(long average, long latest) {
        return average + (latest - average) / 8;
    }

    /** Players with the most damaged blocks first. A tie goes by id, so the list reads the same every time. */
    public static List<Map.Entry<UUID, Integer>> ranked(Map<UUID, Integer> counts) {
        List<Map.Entry<UUID, Integer>> out = new ArrayList<>(counts.entrySet());
        out.sort(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()));
        return out;
    }

    /** A wish still bringing blocks back keeps its rate until a minute after the latest, even past its first end. */
    public static long wishKeeps(long until, long now, int back) {
        return back > 0 ? Math.max(until, now + TICKS_PER_MINUTE) : until;
    }

    /**
     * Nanoseconds one dimension may heal for this tick: an equal share of the tick's budget between the dimensions that had
     * work last tick, and never more than is left.
     */
    public static long allowance(long budgetNanos, int activeLastTick, long spentNanos) {
        long share = budgetNanos / Math.max(1, activeLastTick);
        return Math.max(0, Math.min(share, budgetNanos - spentNanos));
    }
}
