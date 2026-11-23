package com.dmzzero.garden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Pure rules for a planted Tree of Might: its shape from a seed, what each stage shows, and the ground it drains. */
public final class MightTreeMath {
    public static final int ROOT = 0;
    public static final int WOOD = 1;
    public static final int LEAVES = 2;
    public static final int GROWN = 3;

    public record Piece(int dx, int dy, int dz, int kind) {
    }

    private MightTreeMath() {
    }

    /** The whole tree, offsets from the block it was planted in. The same seed always gives the same tree. */
    public static List<Piece> blueprint(long seed) {
        Random r = new Random(seed);
        int h = 8 + r.nextInt(4);
        Map<String, Piece> at = new LinkedHashMap<>();
        for (int y = 0; y < h; y++) put(at, 0, y, 0, WOOD);
        put(at, 1, 0, 0, ROOT);
        put(at, -1, 0, 0, ROOT);
        put(at, 0, 0, 1, ROOT);
        put(at, 0, 0, -1, ROOT);
        for (int[] c : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) if (r.nextBoolean()) put(at, c[0], 0, c[1], ROOT);
        List<int[]> dirs = new ArrayList<>(List.of(new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1}, new int[]{0, -1}));
        Collections.shuffle(dirs, r);
        int branches = 2 + r.nextInt(2);
        for (int i = 0; i < branches; i++) {
            int[] d = dirs.get(i);
            put(at, d[0], h - 3, d[1], WOOD);
            put(at, d[0] * 2, h - 2, d[1] * 2, WOOD);
        }
        int rx = 3 + r.nextInt(2);
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -rx; dx <= rx; dx++) {
                for (int dz = -rx; dz <= rx; dz++) {
                    double e = (dx * dx + dz * dz) / (double) (rx * rx) + dy * dy / 6.25;
                    if (e > 1.0 || e > 0.75 && r.nextInt(3) == 0) continue;
                    put(at, dx, h - 1 + dy, dz, LEAVES);
                }
            }
        }
        return new ArrayList<>(at.values());
    }

    // the first piece at an offset wins, so wood isn't covered by leaves
    private static void put(Map<String, Piece> at, int dx, int dy, int dz, int kind) {
        at.putIfAbsent(dx + "," + dy + "," + dz, new Piece(dx, dy, dz, kind));
    }

    public static int height(List<Piece> tree) {
        int top = -1;
        for (Piece p : tree) if (p.kind() == WOOD && p.dx() == 0 && p.dz() == 0) top = Math.max(top, p.dy());
        return top + 1;
    }

    /** What shows at a stage: a root sprout, then roots and a third of the trunk, then two thirds, then the whole tree. */
    public static List<Piece> part(List<Piece> tree, int stage) {
        if (stage <= 0) return List.of(new Piece(0, 0, 0, ROOT));
        if (stage >= GROWN) return tree;
        int h = height(tree);
        int below = stage == 1 ? (h + 2) / 3 : (2 * h + 2) / 3;
        List<Piece> out = new ArrayList<>();
        for (Piece p : tree) {
            if (p.kind() == ROOT || p.kind() == WOOD && p.dx() == 0 && p.dz() == 0 && p.dy() < below) out.add(p);
        }
        return out;
    }

    /** 0 to 3 by thirds of the growing time. */
    public static int stage(long elapsed, long growTicks) {
        if (elapsed <= 0) return 0;
        if (elapsed >= growTicks) return GROWN;
        return (int) (elapsed * 3 / Math.max(1, growTicks));
    }

    /** Ground the grown tree drains: within the radius, outside its trunk and roots. */
    public static boolean inDrain(int dx, int dz, int radius) {
        int d2 = dx * dx + dz * dz;
        return radius > 0 && d2 > 2 && d2 <= radius * radius;
    }
}
