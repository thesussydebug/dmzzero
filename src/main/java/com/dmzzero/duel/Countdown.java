package com.dmzzero.duel;

// three, two, one, fight: shared by duels and null realm battles
public final class Countdown {
    public static final int TICKS = 60;
    private final long start;
    private int announced = 4;

    public Countdown(long start) {
        this.start = start;
    }

    public boolean running(long now) {
        return now - start < TICKS;
    }

    public int count(long now) {
        return (int) (3 - (now - start) / 20);
    }

    public long end() {
        return start + TICKS;
    }

    // 3, 2, 1 once each, then 0 for "Fight!"; -1 when there's nothing new to say
    public int announce(long now) {
        if (!running(now)) {
            if (announced == 0) return -1;
            announced = 0;
            return 0;
        }
        int c = count(now);
        if (c >= announced) return -1;
        announced = c;
        return c;
    }
}
