package com.dmzzero.scouter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// the rules of the scouter network, without the world around it
public final class ScouterMath {
    public static final int SPIKE_WINDOW = 60;
    public static final int HEAR_AGAIN = 600;
    public static final int PING_COOLDOWN = 40;
    private static final int FORGET_AFTER = 200;
    private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private ScouterMath() {
    }

    // the four scouter colours are four frequencies
    public enum Channel {
        RED(0xFF5555), BLUE(0x55AAFF), GREEN(0x55FF55), PURPLE(0xC77DFF);

        public final int color;

        Channel(int color) {
            this.color = color;
        }

        public static Channel of(String itemPath) {
            if (itemPath == null) return null;
            for (Channel c : values()) {
                if (itemPath.equals(c.name().toLowerCase(Locale.ROOT) + "_scouter")) return c;
            }
            return null;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Spike(double from, double to) {
    }

    private record Sample(long at, double bp) {
    }

    // a reading that jumps by ratio within three seconds; once it's said, the new level is the baseline
    public static final class SpikeWatch {
        private final Map<UUID, Deque<Sample>> history = new HashMap<>();

        public Spike sample(UUID target, long now, double bp, double ratio, double min) {
            Deque<Sample> h = history.computeIfAbsent(target, k -> new ArrayDeque<>());
            while (!h.isEmpty() && h.peekFirst().at < now - SPIKE_WINDOW) h.pollFirst();
            Spike spike = null;
            if (!h.isEmpty() && bp >= min) {
                double base = Double.MAX_VALUE;
                for (Sample s : h) base = Math.min(base, s.bp);
                if (bp >= base * ratio && bp > base) spike = new Spike(base, bp);
            }
            if (spike != null) h.clear();
            h.addLast(new Sample(now, bp));
            return spike;
        }

        public void forget(UUID target) {
            history.remove(target);
        }

        public void prune(long now) {
            history.values().removeIf(h -> h.isEmpty() || h.peekLast().at < now - FORGET_AFTER);
        }

        public int size() {
            return history.size();
        }

        public void clear() {
            history.clear();
        }
    }

    // one alert per wearer per target every thirty seconds
    public static final class Heard {
        private record Key(UUID wearer, UUID target) {
        }

        private final Map<Key, Long> last = new HashMap<>();

        public boolean tryHear(UUID wearer, UUID target, long now) {
            Key key = new Key(wearer, target);
            Long at = last.get(key);
            if (at != null && now - at < HEAR_AGAIN) return false;
            last.put(key, now);
            return true;
        }

        public void prune(long now) {
            for (Iterator<Long> it = last.values().iterator(); it.hasNext(); ) {
                if (now - it.next() >= HEAR_AGAIN) it.remove();
            }
        }

        public void forget(UUID player) {
            last.keySet().removeIf(k -> Objects.equals(k.wearer, player) || Objects.equals(k.target, player));
        }

        public void clear() {
            last.clear();
        }
    }

    public static final class PingCooldowns {
        private final Map<UUID, Long> last = new HashMap<>();

        public boolean tryPing(UUID sender, long now) {
            Long at = last.get(sender);
            if (at != null && now - at < PING_COOLDOWN) return false;
            last.put(sender, now);
            return true;
        }

        public void forget(UUID sender) {
            last.remove(sender);
        }

        public void clear() {
            last.clear();
        }
    }

    // north is towards -z in minecraft
    public static String compass(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int index = Math.floorMod((int) Math.round(angle / 45.0), 8);
        return POINTS[index];
    }

    // a position from the client: NaN would get past every range check
    public static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public static String reading(double bp) {
        return String.format(Locale.US, "%,d", Math.max(0L, Math.round(bp)));
    }
}
