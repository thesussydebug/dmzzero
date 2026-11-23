package com.dmzzero.duel;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// the rules of a duel, without the world around it
public final class DuelMath {
    public static final int COUNTDOWN = Countdown.TICKS;
    public static final int OUTSIDE_LIMIT = 100;
    public static final int CHALLENGE_TICKS = 1200;
    private static final String ROOT = "dmzzero";
    private static final String DUELS = "duels";

    public enum Phase {COUNTDOWN, FIGHTING}

    public enum Reason {KNOCKDOWN, LEFT_RING, LOGGED_OUT, TIME}

    public enum Result {WIN, LOSS, DRAW}

    public record Outcome(UUID winner, UUID loser, Reason reason) {
        public boolean draw() {
            return winner == null;
        }
    }

    private DuelMath() {
    }

    // a cylinder as tall as it is wide, so flying off counts as leaving
    public static boolean inRing(double dx, double dy, double dz, double radius) {
        return dx * dx + dz * dz <= radius * radius && Math.abs(dy) <= radius;
    }

    public static final class Duel {
        public final UUID a;
        public final UUID b;
        public final long start;
        private final int limit;
        private final int outsideLimit;
        private final long[] outsideSince = {-1, -1};
        private final Countdown countdown;

        public Duel(UUID a, UUID b, long start, int limitTicks) {
            this(a, b, start, limitTicks, OUTSIDE_LIMIT);
        }

        public Duel(UUID a, UUID b, long start, int limitTicks, int outsideLimitTicks) {
            this.a = a;
            this.b = b;
            this.start = start;
            this.limit = limitTicks;
            this.outsideLimit = Math.max(1, outsideLimitTicks);
            this.countdown = new Countdown(start);
        }

        /** Ticks outside the ring before it counts as leaving it. */
        public int outsideLimit() {
            return outsideLimit;
        }

        public Phase phase(long now) {
            return countdown.running(now) ? Phase.COUNTDOWN : Phase.FIGHTING;
        }

        public int count(long now) {
            return countdown.count(now);
        }

        // 3, 2, 1 once each, then 0 for "Fight!"; -1 when there's nothing new to say
        public int announce(long now) {
            return countdown.announce(now);
        }

        public long fightStart() {
            return countdown.end();
        }

        public boolean has(UUID id) {
            return a.equals(id) || b.equals(id);
        }

        public UUID other(UUID id) {
            return a.equals(id) ? b : a;
        }

        public long outside(int side, long now) {
            return outsideSince[side] < 0 ? 0 : now - outsideSince[side];
        }

        public Outcome tick(long now, boolean aIn, boolean bIn, boolean aDown, boolean bDown, boolean aHere, boolean bHere) {
            if (!aHere || !bHere) {
                if (!aHere && !bHere) return new Outcome(null, null, Reason.LOGGED_OUT);
                return aHere ? new Outcome(a, b, Reason.LOGGED_OUT) : new Outcome(b, a, Reason.LOGGED_OUT);
            }
            if (phase(now) == Phase.COUNTDOWN) return null;
            if (aDown || bDown) {
                if (aDown && bDown) return new Outcome(null, null, Reason.KNOCKDOWN);
                return aDown ? new Outcome(b, a, Reason.KNOCKDOWN) : new Outcome(a, b, Reason.KNOCKDOWN);
            }
            track(0, aIn, now);
            track(1, bIn, now);
            boolean aOut = outside(0, now) >= outsideLimit, bOut = outside(1, now) >= outsideLimit;
            if (aOut && bOut) return new Outcome(null, null, Reason.LEFT_RING);
            if (aOut) return new Outcome(b, a, Reason.LEFT_RING);
            if (bOut) return new Outcome(a, b, Reason.LEFT_RING);
            if (now - fightStart() >= limit) return new Outcome(null, null, Reason.TIME);
            return null;
        }

        private void track(int side, boolean in, long now) {
            if (in) outsideSince[side] = -1;
            else if (outsideSince[side] < 0) outsideSince[side] = now;
        }
    }

    public static final class Challenges {
        private record Pending(UUID to, long at) {
        }

        private final Map<UUID, Pending> byChallenger = new HashMap<>();
        private final java.util.function.IntSupplier windowTicks;

        public Challenges() {
            this(() -> CHALLENGE_TICKS);
        }

        // read on every check, so a config change applies to challenges already sent
        public Challenges(java.util.function.IntSupplier windowTicks) {
            this.windowTicks = windowTicks;
        }

        public void challenge(UUID from, UUID to, long now) {
            byChallenger.put(from, new Pending(to, now));
        }

        public boolean pending(UUID from, UUID to, long now) {
            Pending p = byChallenger.get(from);
            return p != null && p.to.equals(to) && now - p.at < windowTicks.getAsInt();
        }

        public boolean take(UUID from, UUID to, long now) {
            if (!pending(from, to, now)) return false;
            byChallenger.remove(from);
            return true;
        }

        public void forget(UUID player) {
            byChallenger.remove(player);
            byChallenger.values().removeIf(p -> p.to.equals(player));
        }

        public void clear() {
            byChallenger.clear();
        }
    }

    public static void record(CompoundTag persisted, Result result) {
        CompoundTag root = persisted.getCompound(ROOT);
        CompoundTag duels = root.getCompound(DUELS);
        String key = switch (result) {
            case WIN -> "wins";
            case LOSS -> "losses";
            case DRAW -> "draws";
        };
        duels.putInt(key, duels.getInt(key) + 1);
        root.put(DUELS, duels);
        persisted.put(ROOT, root);
    }

    // wins, losses, draws
    public static int[] score(CompoundTag persisted) {
        CompoundTag duels = persisted.getCompound(ROOT).getCompound(DUELS);
        return new int[]{duels.getInt("wins"), duels.getInt("losses"), duels.getInt("draws")};
    }
}
