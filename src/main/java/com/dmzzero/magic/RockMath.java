package com.dmzzero.magic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure rules for rock lift: which blocks can be torn out, how the cluster grows, its shape and how hard it hits. */
public final class RockMath {
    /** How many layers above and below the first hole the cluster pulls from. */
    public static final int DEPTH = 2;
    private static final int SLOT_REACH = 3;
    private static final List<Off> SLOTS = ball();

    /** A block offset. */
    public record Off(int x, int y, int z) {
        int len2() {
            return x * x + y * y + z * z;
        }
    }

    private RockMath() {
    }

    /** A top block, not too hard, with nothing inside it. Open air above keeps torches and plants from popping off. */
    public static boolean liftable(float hardness, double maxHardness, boolean blockEntity, boolean air, boolean fluid, boolean openAbove) {
        if (air || fluid || blockEntity || !openAbove) return false;
        if (!Float.isFinite(hardness) || !Double.isFinite(maxHardness)) return false;
        return hardness >= 0 && hardness <= maxHardness;
    }

    /** Rocks the cluster should have after the key has been down that long: one more every everyTicks, up to max. */
    public static int wanted(long heldTicks, int max, int everyTicks) {
        if (max <= 1 || everyTicks <= 0 || heldTicks <= 0) return 1;
        return (int) Math.min(max, 1 + heldTicks / everyTicks);
    }

    /** Most rocks a cluster holds: the base number, more for each tier of the caster's form (0 in base form), never past cap. */
    public static int most(int base, int tier, int perTier, int cap) {
        long n = (long) base + (long) Math.max(0, perTier) * Math.max(0, tier);
        return (int) Math.max(1, Math.min(cap, n));
    }

    /** Offsets from the first hole in the order blocks are pulled: nearest first, spreading wide before digging deep. */
    public static List<Off> pullOrder(int radius) {
        int r = Math.max(0, Math.min(radius, 6));
        List<Off> out = new ArrayList<>();
        for (int y = -DEPTH; y <= DEPTH; y++)
            for (int x = -r; x <= r; x++)
                for (int z = -r; z <= r; z++)
                    if (x * x + z * z <= r * r && (x != 0 || y != 0 || z != 0)) out.add(new Off(x, y, z));
        out.sort(Comparator.comparingInt((Off o) -> o.x * o.x + o.z * o.z + 4 * o.y * o.y)
                .thenComparing(Comparator.comparingInt(Off::y).reversed()).thenComparingInt(Off::x).thenComparingInt(Off::z));
        return out;
    }

    /** Where each rock floats, from the cluster's middle: the middle, its faces, the edges, the corners, then outward. */
    public static List<Off> slots(int count) {
        return count <= 0 ? List.of() : SLOTS.subList(0, Math.min(count, SLOTS.size()));
    }

    /** From the cluster's middle to its farthest rock. */
    public static double radius(int count) {
        List<Off> s = slots(count);
        return s.isEmpty() ? 0 : Math.sqrt(s.get(s.size() - 1).len2());
    }

    /** How much harder the cluster hits than one rock: 1 for one, full at max, in a straight line between. */
    public static double share(int count, int max, double full) {
        if (!Double.isFinite(full) || full <= 1 || max <= 1 || count <= 1) return 1;
        return 1 + (full - 1) * (Math.min(count, max) - 1) / (double) (max - 1);
    }

    private static List<Off> ball() {
        List<Off> out = new ArrayList<>();
        int r = SLOT_REACH;
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++)
                    if (x * x + y * y + z * z <= r * r) out.add(new Off(x, y, z));
        out.sort(Comparator.comparingInt(Off::len2).thenComparing(Comparator.comparingInt(Off::y).reversed())
                .thenComparingInt(Off::x).thenComparingInt(Off::z));
        return List.copyOf(out);
    }
}
