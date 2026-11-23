package com.dmzzero.pattern;

import java.util.List;

/** Pure rules for Shenron's clothing pattern wish: which items are patterns, the picker's pages, the picks owed. */
public final class PatternMath {
    public static final int MAX_PICKS = 100;

    private PatternMath() {
    }

    /** DMZ's clothing patterns: dragonminez:pattern_*, not the Pattern Station or the blanks. */
    public static boolean isPattern(String namespace, String path) {
        return "dragonminez".equals(namespace) && path.startsWith("pattern_") && !"pattern_station".equals(path);
    }

    public static int pages(int count, int perPage) {
        return Math.max(1, (count + perPage - 1) / perPage);
    }

    /** One page of the list; a page out of range shows the nearest one. */
    public static <T> List<T> page(List<T> all, int page, int perPage) {
        int p = Math.max(1, Math.min(page, pages(all.size(), perPage)));
        int from = (p - 1) * perPage;
        return all.subList(Math.min(from, all.size()), Math.min(from + perPage, all.size()));
    }

    public static int addPicks(int have, int add) {
        long sum = (long) Math.max(0, have) + Math.max(0, add);
        return (int) Math.min(MAX_PICKS, sum);
    }
}
