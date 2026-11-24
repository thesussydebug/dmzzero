package com.dmzzero.chamber;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// the rules of Time Chamber training with no minecraft state: opponent ids, the kill log, where copies stand,
// what training pays, the story order of DMZ's sagas and the menu's search
public final class TrainingMath {
    public static final int KEEP = 48;
    public static final String QUEST = "quest";
    public static final String KILL = "kill";

    private TrainingMath() {
    }

    /** One kind of mob a player beat: the strongest one seen, and how often. */
    public record Beaten(String id, double health, double melee, double ki, int times) {
    }

    /** "quest/&lt;quest&gt;/&lt;objective&gt;" for a saga boss, "kill/&lt;entity type&gt;" for a beaten mob. */
    public record Id(String kind, String what, int index) {
        public static Id parse(String text) {
            if (text == null) return null;
            String t = text.trim();
            if (t.startsWith(KILL + "/") && t.length() > 5) return new Id(KILL, t.substring(5), 0);
            if (!t.startsWith(QUEST + "/")) return null;
            int slash = t.lastIndexOf('/');
            if (slash <= 6) return null;
            try {
                int index = Integer.parseInt(t.substring(slash + 1));
                return index < 0 ? null : new Id(QUEST, t.substring(6, slash), index);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public String text() {
            return KILL.equals(kind) ? KILL + "/" + what : QUEST + "/" + what + "/" + index;
        }
    }

    public static List<Beaten> add(List<Beaten> log, Beaten beaten) {
        return add(log, beaten, KEEP);
    }

    /** The log with {@code beaten} first: counted again and kept at its strongest if it was there, at most {@code keep} kinds. */
    public static List<Beaten> add(List<Beaten> log, Beaten beaten, int keep) {
        Beaten merged = beaten;
        for (Beaten e : log) {
            if (!e.id().equals(beaten.id())) continue;
            boolean stronger = beaten.health() >= e.health();
            merged = new Beaten(e.id(), Math.max(e.health(), beaten.health()), stronger ? beaten.melee() : e.melee(),
                    stronger ? beaten.ki() : e.ki(), e.times() + Math.max(1, beaten.times()));
        }
        List<Beaten> out = new ArrayList<>(Math.min(keep, log.size() + 1));
        out.add(merged);
        for (Beaten e : log) {
            if (out.size() >= keep) break;
            if (!e.id().equals(beaten.id())) out.add(e);
        }
        return out;
    }

    /**
     * Where the {@code index}-th of {@code count} copies stands: {@code distance} blocks in front of a player at x/z facing
     * {@code yaw}, the others 3 blocks to either side in turn. Returns {x, z}.
     */
    public static double[] inFront(double x, double z, float yaw, double distance, int index) {
        double rad = Math.toRadians(yaw);
        double side = index == 0 ? 0.0 : ((index + 1) / 2) * (index % 2 == 1 ? -3.0 : 3.0);
        return new double[]{x - Math.sin(rad) * distance + Math.cos(rad) * side, z + Math.cos(rad) * distance + Math.sin(rad) * side};
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    /** {@code gain} cut to {@code percent} of itself, rounded to the nearest. A gain of 0 or less passes as it is. */
    public static int tp(int gain, int percent) {
        if (gain <= 0) return gain;
        return (int) Math.round(gain * clamp(percent) / 100.0);
    }

    /** A form's mastery after a fight took it from {@code before} to {@code after}: only {@code percent} of the rise stays. */
    public static double mastery(double before, double after, int percent) {
        return after <= before ? after : before + (after - before) * clamp(percent) / 100.0;
    }

    /**
     * What to take off each form's mastery so only {@code percent} of what a fight added stays. DMZ feeds linked forms too,
     * so every form is compared, not only the active one.
     */
    public static Map<String, Double> cuts(Map<String, Double> before, Map<String, Double> after, int percent) {
        Map<String, Double> out = new HashMap<>();
        for (Map.Entry<String, Double> e : after.entrySet()) {
            double kept = mastery(before.getOrDefault(e.getKey(), 0.0), e.getValue(), percent);
            if (e.getValue() - kept > 1e-12) out.put(e.getKey(), e.getValue() - kept);
        }
        return out;
    }

    /**
     * DMZ's sagas in story order. Each saga names the one it follows (null or blank for none, or one that isn't there: it
     * starts a story of its own). The longest story comes first, the others after it by length then name. Of sagas that
     * follow the same one, the shortest story comes first, so a side story (Future after the Androids) comes before the
     * long story that goes on from there.
     */
    public static List<String> story(Map<String, String> follows) {
        Map<String, List<String>> next = new HashMap<>();
        List<String> starts = new ArrayList<>();
        for (Map.Entry<String, String> e : follows.entrySet()) {
            String before = e.getValue();
            if (before == null || before.isBlank() || before.equals(e.getKey()) || !follows.containsKey(before)) starts.add(e.getKey());
            else next.computeIfAbsent(before, k -> new ArrayList<>()).add(e.getKey());
        }
        Map<String, Integer> length = new HashMap<>();
        for (String saga : follows.keySet()) length.put(saga, walk(saga, next, new HashSet<>(), new ArrayList<>()).size());
        next.values().forEach(list -> list.sort(Comparator.<String>comparingInt(length::get).thenComparing(Comparator.naturalOrder())));
        starts.sort(Comparator.<String>comparingInt(length::get).reversed().thenComparing(Comparator.naturalOrder()));
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        for (String start : starts) walk(start, next, seen, out);
        // sagas that follow each other in a loop have no start; they still get listed
        follows.keySet().stream().filter(s -> !seen.contains(s)).sorted().forEach(s -> walk(s, next, seen, out));
        return out;
    }

    private static List<String> walk(String saga, Map<String, List<String>> next, Set<String> seen, List<String> out) {
        if (seen.add(saga)) {
            out.add(saga);
            for (String after : next.getOrDefault(saga, List.of())) walk(after, next, seen, out);
        }
        return out;
    }

    /** One saga's share of a list, or the rest ({@code saga} null). */
    public record Group<T>(String saga, List<T> items) {
    }

    /** {@code items} grouped by saga in {@code order}, each group in the items' own order; items of no listed saga last. */
    public static <T> List<Group<T>> group(List<T> items, Function<T, String> sagaOf, List<String> order) {
        Map<String, List<T>> by = new LinkedHashMap<>();
        order.forEach(s -> by.put(s, new ArrayList<>()));
        List<T> rest = new ArrayList<>();
        for (T item : items) {
            String saga = sagaOf.apply(item);
            List<T> into = saga == null ? null : by.get(saga);
            (into == null ? rest : into).add(item);
        }
        List<Group<T>> out = new ArrayList<>();
        by.forEach((saga, list) -> {
            if (!list.isEmpty()) out.add(new Group<>(saga, List.copyOf(list)));
        });
        if (!rest.isEmpty()) out.add(new Group<>(null, List.copyOf(rest)));
        return out;
    }

    /** A section of the menu with the opponents in it that a search left. */
    public record Shown<S, F>(S section, List<F> foes) {
    }

    /**
     * The menu's search: every word of {@code query} must be in an opponent's text, as the player reads it. A matching
     * opponent shows under its section, and a section whose own header matches shows whole. A blank search shows everything.
     */
    public static <S, F> List<Shown<S, F>> filter(List<S> sections, Function<S, List<F>> foesOf, Function<S, String> header,
                                                  Function<F, String> text, String query) {
        String[] words = query == null ? new String[0] : query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        boolean all = words.length == 0 || words[0].isEmpty();
        List<Shown<S, F>> out = new ArrayList<>();
        for (S section : sections) {
            List<F> foes = foesOf.apply(section);
            if (all || matches(header.apply(section), words)) {
                out.add(new Shown<>(section, foes));
                continue;
            }
            List<F> found = new ArrayList<>();
            for (F foe : foes) if (matches(text.apply(foe), words)) found.add(foe);
            if (!found.isEmpty()) out.add(new Shown<>(section, found));
        }
        return out;
    }

    private static boolean matches(String text, String[] words) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String word : words) if (!t.contains(word)) return false;
        return true;
    }
}
