package com.dmzzero.chamber;

// two days in the Time Chamber per character, and more for each wish. times are in ticks; a day is 24000
public final class TimeLimit {
    public static final long NO_LIMIT = -1;
    public static final long DAY = 24000;
    public static final long HOUR = 1000;
    // twelve, six and one in-game hours left
    private static final long[] MARKS = {12 * HOUR, 6 * HOUR, HOUR};

    private TimeLimit() {
    }

    public static long limit(int days, int wishedDays) {
        if (days <= 0) return NO_LIMIT;
        return ((long) days + Math.max(0, wishedDays)) * DAY;
    }

    public static long left(long limit, long used) {
        if (limit == NO_LIMIT) return NO_LIMIT;
        return Math.max(0, limit - Math.max(0, used));
    }

    public static boolean out(long limit, long used) {
        return limit != NO_LIMIT && used >= limit;
    }

    /** The mark just passed going from one time left to the next, the closest one if several, or 0. */
    public static long warning(long leftBefore, long leftAfter) {
        if (leftBefore == NO_LIMIT || leftAfter == NO_LIMIT) return 0;
        long passed = 0;
        for (long mark : MARKS) if (leftBefore > mark && leftAfter <= mark) passed = mark;
        return passed;
    }

    public static long[] daysHours(long ticks) {
        long t = Math.max(0, ticks);
        return new long[]{t / DAY, t % DAY / HOUR};
    }
}
