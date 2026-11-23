package com.dmzzero.realm;

import com.dmzzero.duel.Countdown;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// the rules of a null realm free-for-all, without the world around it
public final class BattleMath {
    public static final int MIN_FIGHTERS = 2;
    public static final int RING_OUT_DROP = 2;
    public static final int SLOT_SPACING = 4096;
    public static final double START_SHARE = 0.7;
    private static final String ROOT = "dmzzero";
    private static final String BATTLES = "battles";

    public enum Join {OK, ALREADY, FULL, CLOSED}

    public enum Out {KNOCKDOWN, RING_OUT, LEFT, LOGGED_OUT, FORFEIT}

    public record Result(UUID winner, boolean timeUp, List<UUID> survivors) {
        public boolean draw() {
            return winner == null;
        }
    }

    public record Spot(double x, double z, float yaw) {
    }

    private BattleMath() {
    }

    // an open battle waiting for people to join
    public static final class Lobby {
        public final UUID opener;
        public final long opened;
        private final int window;
        private final int max;
        private final List<UUID> joined = new ArrayList<>();

        public Lobby(UUID opener, long opened, int windowTicks, int max) {
            this.opener = opener;
            this.opened = opened;
            this.window = windowTicks;
            this.max = max;
            joined.add(opener);
        }

        public Join join(UUID id, long now) {
            if (expired(now)) return Join.CLOSED;
            if (joined.contains(id)) return Join.ALREADY;
            if (joined.size() >= max) return Join.FULL;
            joined.add(id);
            return Join.OK;
        }

        public boolean leave(UUID id) {
            return joined.remove(id);
        }

        public boolean expired(long now) {
            return now - opened >= window;
        }

        public boolean canStart() {
            return joined.size() >= MIN_FIGHTERS;
        }

        public boolean has(UUID id) {
            return joined.contains(id);
        }

        public int size() {
            return joined.size();
        }

        public List<UUID> fighters() {
            return List.copyOf(joined);
        }
    }

    // everyone against everyone; the last one standing wins
    public static final class Fight {
        private final List<UUID> fighters;
        private final Countdown countdown;
        private final int limit;
        private final Map<UUID, Out> out = new LinkedHashMap<>();
        private final List<UUID> order = new ArrayList<>();
        private final Map<UUID, UUID> credit = new HashMap<>();
        private final Map<UUID, Integer> kos = new HashMap<>();
        private Result result;

        public Fight(List<UUID> fighters, long start, int limitTicks) {
            this.fighters = List.copyOf(fighters);
            this.countdown = new Countdown(start);
            this.limit = limitTicks;
        }

        public Countdown countdown() {
            return countdown;
        }

        public boolean fighting(long now) {
            return !countdown.running(now);
        }

        public List<UUID> fighters() {
            return fighters;
        }

        public boolean has(UUID id) {
            return fighters.contains(id);
        }

        public boolean standing(UUID id) {
            return fighters.contains(id) && !out.containsKey(id);
        }

        public List<UUID> standing() {
            List<UUID> left = new ArrayList<>();
            for (UUID id : fighters) if (!out.containsKey(id)) left.add(id);
            return left;
        }

        public boolean eliminate(UUID id, Out reason, UUID by) {
            if (!fighters.contains(id) || out.containsKey(id)) return false;
            out.put(id, reason);
            order.add(id);
            if (by != null && !by.equals(id) && fighters.contains(by)) {
                credit.put(id, by);
                kos.merge(by, 1, Integer::sum);
            }
            return true;
        }

        public Out reason(UUID id) {
            return out.get(id);
        }

        public UUID by(UUID id) {
            return credit.get(id);
        }

        public int kos(UUID id) {
            return kos.getOrDefault(id, 0);
        }

        // decided once: one or nobody left, or time up with everyone still standing drawing
        public Result result(long now) {
            if (result != null) return result;
            List<UUID> left = standing();
            if (left.size() <= 1) {
                result = new Result(left.isEmpty() ? null : left.get(0), false, left);
            } else if (fighting(now) && now - countdown.end() >= limit) {
                result = new Result(null, true, left);
            }
            return result;
        }

        // first out is last place; everyone still standing shares first
        public int place(UUID id) {
            int i = order.indexOf(id);
            return i < 0 ? 1 : fighters.size() - i;
        }
    }

    public static boolean inDisc(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius;
    }

    public static boolean rim(int dx, int dz, int radius) {
        return inDisc(dx, dz, radius) && !inDisc(dx, dz, radius - 1);
    }

    // standY is the height you stand at on the stage
    public static boolean ringOut(double y, int standY) {
        return y < standY - RING_OUT_DROP;
    }

    // evenly round the stage, each one facing the middle
    public static List<Spot> spread(int n, double radius) {
        List<Spot> spots = new ArrayList<>();
        double r = radius * START_SHARE;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            double x = Math.cos(a) * r, z = Math.sin(a) * r;
            spots.add(new Spot(x, z, (float) Math.toDegrees(Math.atan2(x, -z))));
        }
        return spots;
    }

    public static int slot(Set<Integer> used) {
        int i = 0;
        while (used.contains(i)) i++;
        return i;
    }

    public static int slotX(int slot) {
        return slot * SLOT_SPACING;
    }

    /** How many chunks from the chunk at blockX a square must reach to hold every block within reach of it, on x and on z around 0. */
    public static int chunkReach(int blockX, int reach) {
        int c = blockX >> 4;
        return Math.max(Math.max(c - ((blockX - reach) >> 4), ((blockX + reach) >> 4) - c), Math.max(-((-reach) >> 4), reach >> 4));
    }

    public static void record(CompoundTag persisted, boolean won, int kos) {
        CompoundTag root = persisted.getCompound(ROOT);
        CompoundTag battles = root.getCompound(BATTLES);
        battles.putInt("played", battles.getInt("played") + 1);
        if (won) battles.putInt("wins", battles.getInt("wins") + 1);
        battles.putInt("kos", battles.getInt("kos") + kos);
        root.put(BATTLES, battles);
        persisted.put(ROOT, root);
    }

    // played, wins, knockouts
    public static int[] score(CompoundTag persisted) {
        CompoundTag battles = persisted.getCompound(ROOT).getCompound(BATTLES);
        return new int[]{battles.getInt("played"), battles.getInt("wins"), battles.getInt("kos")};
    }

    public static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) return n + "th";
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }
}
