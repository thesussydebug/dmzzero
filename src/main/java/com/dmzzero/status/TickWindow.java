package com.dmzzero.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

// time each part of dmz zero spent over a run of server ticks; a report shows the last finished run, or the one in progress
public final class TickWindow {
    public record Part(String name, double ms) {
    }

    /** ms are per tick, averaged over the window's ticks. */
    public record Report(int ticks, double totalMs, List<Part> parts) {
    }

    private final List<String> names = new ArrayList<>();
    private long[] current = new long[0];
    private long[] finished = new long[0];
    private int ticks;
    private int finishedTicks;

    // parts register when their class first loads, which can be on the client thread in singleplayer
    public synchronized int id(String name) {
        int at = names.indexOf(name);
        if (at >= 0) return at;
        names.add(name);
        current = Arrays.copyOf(current, names.size());
        return names.size() - 1;
    }

    /** Every part registered so far, in id order. */
    public synchronized List<String> names() {
        return List.copyOf(names);
    }

    public void add(int id, long nanos) {
        long[] now = current;
        if (id >= 0 && id < now.length) now[id] += nanos;
    }

    /** Ends one server tick; once the window holds windowTicks ticks it becomes the finished one. */
    public synchronized void endTick(int windowTicks) {
        ticks++;
        if (ticks < windowTicks) return;
        finished = current;
        finishedTicks = ticks;
        current = new long[names.size()];
        ticks = 0;
    }

    public synchronized Report report() {
        boolean done = finishedTicks > 0;
        long[] nanos = done ? finished : current;
        int over = done ? finishedTicks : ticks;
        List<Part> parts = new ArrayList<>();
        double total = 0;
        for (int i = 0; i < nanos.length && over > 0; i++) {
            if (nanos[i] <= 0) continue;
            double ms = nanos[i] / 1e6 / over;
            parts.add(new Part(names.get(i), ms));
            total += ms;
        }
        parts.sort(Comparator.comparingDouble(Part::ms).reversed());
        return new Report(over, total, List.copyOf(parts));
    }

    public synchronized void clear() {
        current = new long[names.size()];
        finished = new long[0];
        ticks = 0;
        finishedTicks = 0;
    }
}
