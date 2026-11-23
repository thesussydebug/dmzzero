package com.dmzzero.callout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

// a technique's name shouted over the head of whoever fires it
public final class CalloutMath {
    public static final int MAX_LENGTH = 32;
    /** Ticks a callout stays up, the pop-in, and the shrink at the end. */
    public static final int LIFE = 40;
    public static final int POP = 5;
    private static final int SHRINK = 8;
    /** The same shout from the same fighter within this many ticks keeps the callout up instead of popping a new one. */
    public static final int REPEAT_TICKS = 60;
    /** A repeat starts the callout's life again just past the pop, so it stays up without bouncing. */
    public static final int REFRESHED_AGE = POP;
    private static final Pattern CODES = Pattern.compile("§.?");
    private static final Pattern SPACE = Pattern.compile("[\\s\\p{Cntrl}]+");
    private static final Pattern PLAIN_ID = Pattern.compile("[a-z0-9_.\\-]+");

    private CalloutMath() {
    }

    /** A player-given technique name, safe to show: no formatting codes, no line breaks, not too long. Null if nothing's left. */
    public static String clean(String name) {
        if (name == null) return null;
        String s = SPACE.matcher(CODES.matcher(name).replaceAll("")).replaceAll(" ").trim();
        if (s.length() > MAX_LENGTH) s = s.substring(0, MAX_LENGTH).trim();
        return s.isEmpty() ? null : s;
    }

    public static String shout(String name) {
        if (name.endsWith("!") || name.endsWith("?") || name.endsWith(".")) return name;
        return name + "!";
    }

    /** The parts of a name chanted while it charges, a stacked form (Kaioken x3) first. "Kamehameha" goes by syllable. */
    public static List<String> beats(String name, String stack) {
        List<String> out = new ArrayList<>();
        String clean = clean(name);
        if (clean == null) return out;
        String prefix = clean(stack);
        if (prefix != null) out.add(prefix);
        String[] words = clean.split("[ \\-]+");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.isEmpty()) continue;
            String lower = w.toLowerCase(Locale.ROOT);
            if (lower.equals("kamehameha")) {
                for (int j = 0; j < 10; j += 2) out.add(w.substring(j, j + 2));
            } else {
                out.add(w);
                // gohan shouts "masenko... ha!"
                if (lower.equals("masenko") && i == words.length - 1) out.add("ha");
            }
        }
        return out;
    }

    /** How many beats show after holding the charge this long. The last beat is saved for the release. */
    public static int beatsShown(int heldTicks, int beatTicks, int beatCount) {
        if (beatCount < 2) return 0;
        if (beatTicks <= 0) return beatCount - 1;
        return Math.min(beatCount - 1, 1 + Math.max(0, heldTicks) / beatTicks);
    }

    public static String chant(List<String> beats, int shown) {
        int n = Math.min(shown, beats.size() - 1);
        if (n <= 0) return null;
        return String.join("... ", beats.subList(0, n)) + "...";
    }

    /** What a chanted technique shouts on release: its last beat. */
    public static String finale(List<String> beats) {
        if (beats.size() < 2) return null;
        return beats.get(beats.size() - 1).toUpperCase(Locale.ROOT) + "!";
    }

    /** What a technique fired before any chant shouts: the whole name, with the stacked form in front. */
    public static String whole(List<String> beats, String name) {
        String clean = clean(name);
        if (clean == null) return null;
        String prefix = beats.size() > beats(clean, null).size() ? beats.get(0) + " " : "";
        return shout(prefix + clean);
    }

    public static boolean alive(int age) {
        return age >= 0 && age < LIFE;
    }

    /** Size of the callout by age: pops in with an overshoot, holds, then shrinks away. */
    public static float scale(int age) {
        if (!alive(age)) return 0f;
        if (age < POP) {
            float t = (age + 1) / (float) POP;
            // ease out with a bump past full size
            return (float) (1 + 0.25 * Math.sin(Math.PI * t) - (1 - t) * (1 - t) * 0.9);
        }
        int left = LIFE - age;
        if (left < SHRINK) return left / (float) SHRINK * 0.9f;
        return 1f;
    }

    /** How far above its start the callout has floated, in blocks. */
    public static float rise(int age) {
        return 0.4f * Math.max(0, Math.min(age, LIFE)) / LIFE;
    }

    public static boolean repeat(String shown, String text, int ticksSince) {
        return shown != null && shown.equals(text) && ticksSince <= REPEAT_TICKS;
    }

    public static int textColor(int rgb) {
        return 0xFF000000 | mix(rgb, 0xFFFFFF, 0.55);
    }

    public static int outlineColor(int rgb) {
        return 0xFF000000 | mix(rgb, 0x000000, 0.65);
    }

    /** Lang keys to try for a technique fired without a given name (DMZ's and Sparking's own techniques). */
    public static List<String> langKeys(String techniqueId) {
        if (techniqueId == null || !PLAIN_ID.matcher(techniqueId).matches() || techniqueId.contains("..")) return List.of();
        return List.of("technique.dragonminez." + techniqueId, "technique.dmzsparking." + techniqueId);
    }

    private static int mix(int rgb, int toward, double share) {
        int r = (int) Math.round((rgb >> 16 & 0xFF) * (1 - share) + (toward >> 16 & 0xFF) * share);
        int g = (int) Math.round((rgb >> 8 & 0xFF) * (1 - share) + (toward >> 8 & 0xFF) * share);
        int b = (int) Math.round((rgb & 0xFF) * (1 - share) + (toward & 0xFF) * share);
        return r << 16 | g << 8 | b;
    }
}
