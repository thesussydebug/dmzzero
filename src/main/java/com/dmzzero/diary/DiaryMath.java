package com.dmzzero.diary;

import java.util.Arrays;
import java.util.Locale;

// the training diary: where a tp gain came from, and the two days it keeps
public final class DiaryMath {
    public enum Kind {
        FIGHTS("fights"), TRAINING("training"), CHAMBER("chamber"), EVERYDAY("everyday"), QUESTS("quests"), PARTY("party"), OTHER("other");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Kind byKey(String key) {
            for (Kind k : values()) if (k.key.equals(key)) return k;
            return null;
        }
    }

    public record Frame(String cls, String method) {
    }

    // dmz's tp gain event doesn't say where the tp came from, so the first caller below these frames decides
    private static final String[] SKIP_PREFIX = {"java.", "jdk.", "sun.", "net.minecraftforge.eventbus.",
            "net.minecraftforge.common.util.LazyOptional", "com.dmzzero.diary."};
    private static final String[] SKIP_CLASS = {"com.dragonminez.common.stats.character.Resources", "com.dragonminez.common.stats.StatsData"};
    private static final String[] FIGHT = {"onentityhit", "onentitydeath", "combat", "raid", "tournament", "bioandroidevolution",
            "rival", "underdog", "duel", "realm", "battle", "fight"};
    private static final String[] TRAIN = {"minigame", "training", "plowing", "mentor", "tutor", "gravity"};
    private static final String[] EVERY = {"onplayertick", "onblockbreak", "onitemcrafted"};
    private static final String[] QUEST = {"quest", "reward", "dialogue", "wish", "story"};
    private static final int KINDS = Kind.values().length;

    private DiaryMath() {
    }

    public static boolean skips(String cls) {
        for (String p : SKIP_PREFIX) if (cls.startsWith(p)) return true;
        for (String c : SKIP_CLASS) if (cls.equals(c)) return true;
        return false;
    }

    public static Kind kind(Iterable<Frame> frames) {
        for (Frame f : frames) {
            if (f == null || f.cls() == null || skips(f.cls())) continue;
            return classify(f.cls(), f.method());
        }
        return Kind.OTHER;
    }

    static Kind classify(String cls, String method) {
        String m = method == null ? "" : method.toLowerCase(Locale.ROOT);
        if (m.contains("sharewith")) return Kind.PARTY;
        String all = cls.toLowerCase(Locale.ROOT) + "#" + m;
        if (any(all, FIGHT)) return Kind.FIGHTS;
        if (any(all, TRAIN)) return Kind.TRAINING;
        if (any(all, EVERY)) return Kind.EVERYDAY;
        if (any(all, QUEST)) return Kind.QUESTS;
        return Kind.OTHER;
    }

    private static boolean any(String s, String[] words) {
        for (String w : words) if (s.contains(w)) return true;
        return false;
    }

    /** Anything earned by your own effort inside the Time Chamber counts as the Chamber's. */
    public static Kind place(Kind kind, boolean inChamber) {
        if (!inChamber || kind == Kind.QUESTS || kind == Kind.PARTY) return kind;
        return Kind.CHAMBER;
    }

    public static long total(long[] amounts) {
        long sum = 0;
        for (long a : amounts) sum = add(sum, a);
        return sum;
    }

    private static long add(long a, long b) {
        if (b <= 0) return a;
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    /** Today and yesterday, by kind. Days are counted on the server's clock. */
    public static final class Days {
        private long day;
        private long[] today;
        private long[] yesterday;

        public Days() {
            this(0, null, null);
        }

        public Days(long day, long[] today, long[] yesterday) {
            this.day = day;
            this.today = clean(today);
            this.yesterday = clean(yesterday);
        }

        private static long[] clean(long[] in) {
            long[] out = new long[KINDS];
            if (in == null) return out;
            for (int i = 0; i < Math.min(in.length, KINDS); i++) out[i] = Math.max(0, in[i]);
            return out;
        }

        /** Moves on to a later day. A clock that went back keeps the current day. */
        public void roll(long now) {
            if (now <= day) return;
            yesterday = now == day + 1 ? today : new long[KINDS];
            today = new long[KINDS];
            day = now;
        }

        public void add(long now, Kind kind, long amount) {
            if (amount <= 0) return;
            roll(now);
            today[kind.ordinal()] = DiaryMath.add(today[kind.ordinal()], amount);
        }

        public long day() {
            return day;
        }

        public long[] today() {
            return Arrays.copyOf(today, KINDS);
        }

        public long[] yesterday() {
            return Arrays.copyOf(yesterday, KINDS);
        }
    }
}
