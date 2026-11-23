package com.dmzzero.diet;

import java.util.Locale;

/** Pure food maths for the racial diets: Namekians live on water, Majins on sweets. */
public final class DietMath {
    public enum Diet { NONE, NAMEKIAN, MAJIN }

    /** Hunger and saturation modifier, the way vanilla food carries them (saturation gained = hunger x modifier x 2). */
    public record Food(int hunger, float saturationModifier) {
    }

    private DietMath() {
    }

    /** The race, or a custom race with that race's racial skill (Overhaul's "revamp" racials included). */
    public static Diet dietOf(String racialSkill, String race, boolean namekianOn, boolean majinOn) {
        if (namekianOn && (is(race, "namekian") || is(racialSkill, "namekian") || is(racialSkill, "namekianrevamp"))) return Diet.NAMEKIAN;
        if (majinOn && (is(race, "majin") || is(racialSkill, "majin") || is(racialSkill, "majinrevamp"))) return Diet.MAJIN;
        return Diet.NONE;
    }

    private static boolean is(String value, String wanted) {
        return value != null && value.toLowerCase(Locale.ROOT).equals(wanted);
    }

    /** Whether eating something (a sweet or not) changes for this diet at all. */
    public static boolean changes(Diet diet, boolean sweet) {
        return diet == Diet.NAMEKIAN || (diet == Diet.MAJIN && sweet);
    }

    public static Food adjust(Diet diet, boolean sweet, int hunger, float saturationModifier, double namekianFactor, double sweetsFactor) {
        if (!changes(diet, sweet) || hunger <= 0) return new Food(hunger, saturationModifier);
        double factor = diet == Diet.NAMEKIAN ? namekianFactor : sweetsFactor;
        return scale(hunger, saturationModifier, factor);
    }

    /** Hunger scaled and rounded (at least 1), and the modifier set so the saturation gained scales exactly. */
    static Food scale(int hunger, float saturationModifier, double factor) {
        int scaled = Math.max(1, (int) Math.round(hunger * factor));
        float modifier = (float) (saturationModifier * factor * hunger / scaled);
        return new Food(scaled, modifier);
    }

    /** What a water bottle feeds this diet, or null if it feeds nothing. */
    /** When a senzu bean eaten now stops keeping you fed, in game ticks. */
    public static long senzuUntil(long now, int days) {
        return days <= 0 ? now : now + days * 24000L;
    }

    public static boolean fed(long now, long until) {
        return now < until;
    }

    public static long later(long a, long b) {
        return Math.max(a, b);
    }

    public static Food water(Diet diet, int hunger, double saturationModifier) {
        if (diet != Diet.NAMEKIAN || hunger <= 0) return null;
        return new Food(hunger, (float) saturationModifier);
    }
}
