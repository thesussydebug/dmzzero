package com.dmzzero.garden;

/** Pure rules for a senzu bush: how long a bean takes, the bush's stage on the way, and when it's ripe. */
public final class SenzuMath {
    public static final long DAY = 24_000;
    private static final long HOUR = 1_000;
    public static final int RIPE = 3;

    private SenzuMath() {
    }

    public static long ripenTicks(double days) {
        if (!Double.isFinite(days) || days <= 0) return DAY;
        return Math.max(20, Math.round(days * DAY));
    }

    // near korin it grows faster; a speed under 1 or a broken one leaves the normal time
    public static long ripenTicks(double days, double speed) {
        long normal = ripenTicks(days);
        if (!Double.isFinite(speed) || speed <= 1) return normal;
        return Math.max(20, Math.round(normal / speed));
    }

    /** A sweet berry bush's age, 0 to 3. A picked bush starts again at 1, its first leaves. */
    public static int age(long now, long since, long ripen, boolean picked) {
        if (ripe(now, since, ripen)) return RIPE;
        long elapsed = Math.max(0, now - since);
        int stage = (int) Math.min(2, elapsed * 3 / Math.max(1, ripen));
        return Math.max(picked ? 1 : 0, stage);
    }

    public static boolean ripe(long now, long since, long ripen) {
        return now >= since && now - since >= ripen;
    }

    /** True for the first {@code every} ticks after ripening, so a check that often sees each ripening once. */
    public static boolean justRipened(long now, long since, long ripen, long every) {
        long past = now - since - ripen;
        return past >= 0 && past < every;
    }

    /** In-game hours until it's ripe, rounded up; 0 once it is. */
    public static long hoursLeft(long now, long since, long ripen) {
        return ripe(now, since, ripen) ? 0 : hours(ripen - Math.max(0, now - since));
    }

    /** Ticks as in-game hours, rounded up. */
    public static long hours(long ticks) {
        return ticks <= 0 ? 0 : Math.max(1, (ticks + HOUR - 1) / HOUR);
    }

    /** Ready by now, and not yet when its owner left at {@code left}. */
    public static boolean becameReady(long readyAt, long left, long now) {
        return readyAt > left && readyAt <= now;
    }
}
