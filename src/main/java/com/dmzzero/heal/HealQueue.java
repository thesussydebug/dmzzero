package com.dmzzero.heal;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongPredicate;

// the blocks of one dimension waiting to heal: where, what was there first, what the damage left, who did it, and when it's due.
// positions are BlockPos.asLong, states are runtime block state ids, so this knows nothing of minecraft.
// plain arrays, one slot per block, and a heap of slots in due order that knows where each slot sits in it: no boxing,
// nothing allocated per block, and an update moves the block instead of leaving a stale copy behind
public final class HealQueue {
    /** by is the player whose damage it was, or null when nobody is known. */
    public record Entry(long pos, int original, int left, long due, UUID by) {
    }

    /** Blocks waiting, counted by the player behind them, and the ones no player is known for. */
    public record Tally(Map<UUID, Integer> byPlayer, int unknown) {
        public int total() {
            return unknown + byPlayer.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private final Long2IntOpenHashMap slotOf = new Long2IntOpenHashMap();
    private long[] pos = new long[16];
    private int[] original = new int[16];
    private int[] left = new int[16];
    private long[] due = new long[16];
    // order of damage, for blocks due in the same tick
    private long[] seq = new long[16];
    // who did it: 0 for nobody known, else the player's place in owners plus one. each player is listed once, however many blocks
    private int[] who = new int[16];
    private final List<UUID> owners = new ArrayList<>();
    private final Object2IntOpenHashMap<UUID> ownerIndex = new Object2IntOpenHashMap<>();
    // the last player looked up: a carve records hundreds of one player's blocks in a row
    private UUID lastBy;
    private int lastIndex;
    private int[] heapAt = new int[16];
    private int[] heap = new int[16];
    private int size;
    private long nextSeq;

    public HealQueue() {
        slotOf.defaultReturnValue(-1);
    }

    /** Damage to one block, by this player or null. Returns whether it's waiting to heal now. */
    public boolean record(long p, int originalState, int leftState, long dueAt, UUID by) {
        int s = slotOf.get(p);
        if (s >= 0) originalState = original[s];
        if (originalState == leftState) {
            // broken back to what it first was: nothing left to heal
            if (s >= 0) remove(s);
            return false;
        }
        if (s >= 0) {
            left[s] = leftState;
            due[s] = dueAt;
            seq[s] = nextSeq++;
            who[s] = intern(by);
            resift(heapAt[s]);
        } else {
            add(p, originalState, leftState, dueAt, by);
        }
        return true;
    }

    /** The player behind the damage waiting here, or null when nobody is known or nothing waits. */
    public UUID ownerOf(long p) {
        int s = slotOf.get(p);
        return s < 0 ? null : ownerAt(s);
    }

    /** Counts what waits where the test passes, by player. */
    public Tally tally(LongPredicate where) {
        int[] counts = new int[owners.size() + 1];
        for (int s = 0; s < size; s++) {
            if (where.test(pos[s])) counts[who[s]]++;
        }
        Map<UUID, Integer> byPlayer = new HashMap<>();
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > 0) byPlayer.put(owners.get(i - 1), counts[i]);
        }
        return new Tally(byPlayer, counts[0]);
    }

    public boolean pending(long p) {
        return slotOf.containsKey(p);
    }

    public int size() {
        return size;
    }

    /** Takes out up to max entries that are due, earliest first. */
    public List<Entry> due(long now, int max) {
        List<Entry> out = new ArrayList<>();
        while (out.size() < max && size > 0 && due[heap[0]] <= now) {
            int s = heap[0];
            out.add(entry(s));
            remove(s);
        }
        return out;
    }

    /** Takes out the earliest entry if it's due, or null. The rest stay in, so the blocks under it can still be seen. */
    public Entry next(long now) {
        if (size == 0 || due[heap[0]] > now) return null;
        int s = heap[0];
        Entry e = entry(s);
        remove(s);
        return e;
    }

    /** Puts a taken-out entry back to try again later, unless the block was damaged again since. */
    public void postpone(Entry e, long dueAt) {
        if (pending(e.pos())) return;
        add(e.pos(), e.original(), e.left(), dueAt, e.by());
    }

    /**
     * Puts a taken-out entry back while the block under it is still waiting, until that one is due and never sooner than
     * minWait. Returns whether it had to wait.
     */
    public boolean waitBelow(Entry e, long below, long now, int minWait) {
        int under = slotOf.get(below);
        if (under < 0) return false;
        postpone(e, Math.max(now + minWait, due[under]));
        return true;
    }

    /**
     * A falling block landed in this damaged spot: it heals over what landed, keeping its turn. Returns whether the spot was
     * waiting.
     */
    public boolean landed(long p, int leftState) {
        int s = slotOf.get(p);
        if (s < 0) return false;
        if (original[s] == leftState) remove(s);
        else left[s] = leftState;
        return true;
    }

    /** When the block here is due, or Long.MAX_VALUE when it isn't waiting. */
    public long dueOf(long p) {
        int s = slotOf.get(p);
        return s < 0 ? Long.MAX_VALUE : due[s];
    }

    /** Takes out the block here, due or not, or null when it isn't waiting. */
    public Entry take(long p) {
        int s = slotOf.get(p);
        if (s < 0) return null;
        Entry e = entry(s);
        remove(s);
        return e;
    }

    /** Forgets a spot, only if it was this block first. Returns whether it did. */
    public boolean forget(long p, int originalState) {
        int s = slotOf.get(p);
        if (s < 0 || original[s] != originalState) return false;
        remove(s);
        return true;
    }

    /** Makes everything due now, in the order it was waiting. Returns how many. */
    public int hurry(long now) {
        int[] order = sorted();
        for (int s : order) {
            due[s] = Math.min(due[s], now);
            seq[s] = nextSeq++;
        }
        // in due order already, so it's a valid heap as it stands
        for (int i = 0; i < size; i++) place(i, order[i]);
        return size;
    }

    // gives the memory back too: a forgotten 50,000-block list shouldn't stay that big
    public void clear() {
        slotOf.clear();
        slotOf.trim();
        size = 0;
        pos = new long[16];
        original = new int[16];
        left = new int[16];
        due = new long[16];
        seq = new long[16];
        who = new int[16];
        heapAt = new int[16];
        heap = new int[16];
        forgetOwners();
    }

    /** Everything waiting, earliest first, left in place. */
    public List<Entry> all() {
        List<Entry> out = new ArrayList<>(size);
        for (int s : sorted()) out.add(entry(s));
        return out;
    }

    /** A copy of what's waiting, cheap enough for the server thread; it sorts itself wherever it's read. */
    public Snapshot snapshot() {
        return new Snapshot(Arrays.copyOf(pos, size), Arrays.copyOf(original, size), Arrays.copyOf(left, size), Arrays.copyOf(due, size),
                Arrays.copyOf(seq, size), Arrays.copyOf(who, size), List.copyOf(owners));
    }

    /** The list as it stood, slot by slot, for saving it off the server thread. */
    public static final class Snapshot {
        private final long[] pos;
        private final int[] original;
        private final int[] left;
        private final long[] due;
        private final long[] seq;
        private final int[] who;
        private final List<UUID> owners;

        private Snapshot(long[] pos, int[] original, int[] left, long[] due, long[] seq, int[] who, List<UUID> owners) {
            this.pos = pos;
            this.original = original;
            this.left = left;
            this.due = due;
            this.seq = seq;
            this.who = who;
            this.owners = owners;
        }

        public int size() {
            return pos.length;
        }

        /** Every entry, earliest due first and then in the order of damage, as all() gives them. */
        public List<Entry> sorted() {
            int[] order = new int[pos.length];
            for (int i = 0; i < order.length; i++) order[i] = i;
            IntArrays.quickSort(order, (a, b) -> {
                int byDue = Long.compare(due[a], due[b]);
                return byDue != 0 ? byDue : Long.compare(seq[a], seq[b]);
            });
            List<Entry> out = new ArrayList<>(order.length);
            for (int s : order) out.add(new Entry(pos[s], original[s], left[s], due[s], who[s] == 0 ? null : owners.get(who[s] - 1)));
            return out;
        }
    }

    /** When the next block is due, or Long.MAX_VALUE with nothing waiting. */
    public long nextDue() {
        return size == 0 ? Long.MAX_VALUE : due[heap[0]];
    }

    // queued tickets: one per block, since an update moves its block in the heap
    int tickets() {
        return size;
    }

    private Entry entry(int s) {
        return new Entry(pos[s], original[s], left[s], due[s], ownerAt(s));
    }

    private UUID ownerAt(int s) {
        return who[s] == 0 ? null : owners.get(who[s] - 1);
    }

    // the place of this player in owners plus one, listing them the first time; 0 for nobody
    private int intern(UUID by) {
        if (by == null) return 0;
        if (by == lastBy) return lastIndex;
        int index = ownerIndex.getInt(by);
        if (index == 0) {
            owners.add(by);
            index = owners.size();
            ownerIndex.put(by, index);
        }
        lastBy = by;
        lastIndex = index;
        return index;
    }

    // with nothing waiting nobody needs remembering, so the list of players doesn't only ever grow
    private void forgetOwners() {
        owners.clear();
        ownerIndex.clear();
        lastBy = null;
    }

    private void add(long p, int originalState, int leftState, long dueAt, UUID by) {
        if (size == pos.length) grow();
        int s = size++;
        pos[s] = p;
        original[s] = originalState;
        left[s] = leftState;
        due[s] = dueAt;
        seq[s] = nextSeq++;
        who[s] = intern(by);
        slotOf.put(p, s);
        place(s, s);
        up(s);
    }

    // takes slot s out of the heap, then fills its hole with the last slot so the arrays stay packed
    private void remove(int s) {
        int i = heapAt[s], lastAt = size - 1;
        int moved = heap[lastAt];
        size--;
        if (i != lastAt) {
            place(i, moved);
            resift(i);
        }
        slotOf.remove(pos[s]);
        int last = size;
        if (s != last) {
            pos[s] = pos[last];
            original[s] = original[last];
            left[s] = left[last];
            due[s] = due[last];
            seq[s] = seq[last];
            who[s] = who[last];
            place(heapAt[last], s);
            slotOf.put(pos[s], s);
        }
        if (size == 0) forgetOwners();
    }

    private void grow() {
        int n = pos.length * 2;
        pos = Arrays.copyOf(pos, n);
        original = Arrays.copyOf(original, n);
        left = Arrays.copyOf(left, n);
        due = Arrays.copyOf(due, n);
        seq = Arrays.copyOf(seq, n);
        who = Arrays.copyOf(who, n);
        heapAt = Arrays.copyOf(heapAt, n);
        heap = Arrays.copyOf(heap, n);
    }

    // every slot, earliest due first and then in the order of damage
    private int[] sorted() {
        int[] order = new int[size];
        for (int i = 0; i < size; i++) order[i] = i;
        IntArrays.quickSort(order, this::compare);
        return order;
    }

    private int compare(int a, int b) {
        int byDue = Long.compare(due[a], due[b]);
        return byDue != 0 ? byDue : Long.compare(seq[a], seq[b]);
    }

    private void place(int i, int s) {
        heap[i] = s;
        heapAt[s] = i;
    }

    private void resift(int i) {
        if (i > 0 && compare(heap[i], heap[(i - 1) / 2]) < 0) up(i);
        else down(i);
    }

    private void up(int i) {
        int s = heap[i];
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (compare(s, heap[parent]) >= 0) break;
            place(i, heap[parent]);
            i = parent;
        }
        place(i, s);
    }

    private void down(int i) {
        int s = heap[i];
        while (true) {
            int child = 2 * i + 1;
            if (child >= size) break;
            if (child + 1 < size && compare(heap[child + 1], heap[child]) < 0) child++;
            if (compare(heap[child], s) >= 0) break;
            place(i, heap[child]);
            i = child;
        }
        place(i, s);
    }
}
