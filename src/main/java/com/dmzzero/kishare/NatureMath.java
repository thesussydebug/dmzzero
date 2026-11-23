package com.dmzzero.kishare;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

// what the land around a Spirit Bomb lends it, in ki bars of the caster per second
public final class NatureMath {
    public static final int MAX_ANIMALS = 40;
    public static final int MAX_VILLAGERS = 10;
    public static final int MAX_PLANTS = 600;
    private static final double ANIMAL = 0.005;
    private static final double VILLAGER = 0.012;
    private static final double PLANT = 0.0002;

    private NatureMath() {
    }

    public static double perSecond(int animals, int villagers, int plants) {
        return Math.min(animals, MAX_ANIMALS) * ANIMAL
                + Math.min(villagers, MAX_VILLAGERS) * VILLAGER
                + Math.min(plants, MAX_PLANTS) * PLANT;
    }

    public static double give(double budgetLeft, double perSecond) {
        return Math.max(0, Math.min(budgetLeft, perSecond));
    }

    public static String timeLeft(long ticks) {
        long seconds = Math.max(1, (ticks + 19) / 20);
        long m = seconds / 60, s = seconds % 60;
        if (m == 0) return s + "s";
        return s == 0 ? m + "m" : m + "m " + s + "s";
    }

    // spots that already gave, and until when the land around them is resting
    public static final class AreaCooldowns {
        private record Area(int x, int z, long until) {
        }

        private final List<Area> areas = new ArrayList<>();

        public void mark(int x, int z, long now, long ticks) {
            areas.add(new Area(x, z, now + ticks));
        }

        public long remaining(int x, int z, long now, int radius) {
            long best = 0;
            long r2 = (long) radius * radius;
            for (Area a : areas) {
                long dx = a.x - x, dz = a.z - z;
                if (dx * dx + dz * dz <= r2) best = Math.max(best, a.until - now);
            }
            return Math.max(0, best);
        }

        public boolean prune(long now) {
            return areas.removeIf(a -> a.until <= now);
        }

        public void forget(int x, int z) {
            areas.removeIf(a -> a.x == x && a.z == z);
        }

        public int size() {
            return areas.size();
        }

        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (Area a : areas) {
                CompoundTag t = new CompoundTag();
                t.putInt("x", a.x);
                t.putInt("z", a.z);
                t.putLong("until", a.until);
                list.add(t);
            }
            tag.put("areas", list);
            return tag;
        }

        public static AreaCooldowns load(CompoundTag tag) {
            AreaCooldowns c = new AreaCooldowns();
            ListTag list = tag.getList("areas", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                c.areas.add(new Area(t.getInt("x"), t.getInt("z"), t.getLong("until")));
            }
            return c;
        }
    }
}
