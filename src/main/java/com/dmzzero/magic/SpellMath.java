package com.dmzzero.magic;

import java.util.List;
import java.util.function.Predicate;

/** Pure rules for a glind's spells: which one is picked, the next one, and their ki cost. */
public final class SpellMath {
    /** What a key press asks for, as the spell packet carries it. */
    public static final int NONE = -1;
    public static final int CAST = 0;
    public static final int NEXT = 1;
    /** The Action key going up after a cast. */
    public static final int RELEASE = 2;
    /** Ticks between two casts, or two switches, from one player: only a second one in the same tick is dropped. */
    public static final int PRESS_GAP = 1;

    private SpellMath() {
    }

    /** A key press in Racial mode: Second Function with Action picks the next spell, Action alone casts. */
    public static int key(boolean action, boolean nextChord, boolean secondFunction) {
        if (nextChord) return NEXT;
        return action && !secondFunction ? CAST : NONE;
    }

    /** The saved spell while it's on, else the first spell that is, else null. */
    public static String selected(List<String> ids, Predicate<String> on, String saved) {
        if (saved != null && ids.contains(saved) && on.test(saved)) return saved;
        for (String id : ids) if (on.test(id)) return id;
        return null;
    }

    /** The next spell that's on after current, wrapping round; null when none is. */
    public static String next(List<String> ids, Predicate<String> on, String current) {
        int start = current == null ? -1 : ids.indexOf(current);
        for (int i = 1; i <= ids.size(); i++) {
            String id = ids.get(Math.floorMod(start + i, ids.size()));
            if (on.test(id)) return id;
        }
        return null;
    }

    /** A share of max ki. Broken numbers cost nothing rather than NaN. */
    public static float cost(float maxKi, double share) {
        if (!Float.isFinite(maxKi) || !Double.isFinite(share) || maxKi <= 0 || share <= 0) return 0f;
        return (float) (maxKi * share);
    }

    public static boolean affords(float current, float cost) {
        return Float.isFinite(current) && current >= cost;
    }

    /** A press this soon after the last one is a repeat, or a client flooding packets. */
    public static boolean samePress(Long last, long now, int gapTicks) {
        return last != null && now >= last && now - last < gapTicks;
    }
}
