package com.dmzzero.punch;

import java.util.Locale;

// the punch machine remembers your best hit and your best total over a short window
public final class PunchMath {

    private PunchMath() {
    }

    /** Hits on one machine since start, in server ticks. */
    public record Window(int machine, long start, double sum) {
    }

    /** Adds a hit. A hit on another machine, or after the window closed, starts a new one. */
    public static Window add(Window w, int machine, long now, double damage, int windowTicks) {
        if (!Double.isFinite(damage) || damage <= 0) return w;
        if (w == null || w.machine() != machine || closed(w, now, windowTicks)) return new Window(machine, now, damage);
        return new Window(machine, w.start(), w.sum() + damage);
    }

    public static boolean closed(Window w, long now, int windowTicks) {
        return w != null && now - w.start() >= windowTicks;
    }

    public static boolean better(double best, double value) {
        return Double.isFinite(value) && value > 0 && value > best;
    }

    public static String format(double v) {
        if (!Double.isFinite(v) || v < 0) return "0";
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }
}
