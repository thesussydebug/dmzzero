package com.dmzzero.settings;

import java.math.BigDecimal;
import java.math.RoundingMode;

// the settings screen's words and numbers: names from config keys, typed numbers checked against their range
public final class SettingsText {
    private SettingsText() {
    }

    /** "screenShakeStrength" reads "Screen shake strength". */
    public static String label(String key) {
        if (key == null || key.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c) && i > 0) out.append(' ').append(Character.toLowerCase(c));
            else out.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return out.toString();
    }

    /** The lang key for a setting's path ("fighting.impacts.screenShake") or a section ("impacts"). */
    public static String key(String path) {
        return "dmzzero.config." + path;
    }

    /** The typed number in the setting's type, or null when it isn't one or is out of range. */
    public static Object parse(String text, Class<?> type, Object min, Object max) {
        if (text == null) return null;
        String s = text.trim();
        if (s.isEmpty()) return null;
        try {
            Comparable<?> value;
            if (type == Integer.class) value = Integer.parseInt(s);
            else if (type == Long.class) value = Long.parseLong(s);
            else if (type == Double.class) {
                double d = Double.parseDouble(s);
                if (!Double.isFinite(d)) return null;
                value = d;
            } else return null;
            if (min != null && compare(value, min) < 0) return null;
            if (max != null && compare(value, max) > 0) return null;
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A value as the screen shows it: 1.0 as "1", 0.25 as "0.25". */
    public static String show(Object value) {
        if (value instanceof Double d && Double.isFinite(d)) return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        return String.valueOf(value);
    }

    /**
     * One click of the screen's - or + button, kept in range. The step is fixed per setting, so - always undoes +:
     * a tenth of the default as a round number, or a hundredth of the range when the default is 0.
     * Anything that isn't a number comes back unchanged.
     */
    public static Object nudge(Object value, Class<?> type, Object min, Object max, Object defaultValue, int direction) {
        if (direction == 0 || !(value instanceof Number n)) return value;
        boolean whole = type == Integer.class || type == Long.class;
        if (!whole && type != Double.class) return value;
        if (!whole && !Double.isFinite(n.doubleValue())) return value;
        BigDecimal v = whole ? BigDecimal.valueOf(n.longValue()) : BigDecimal.valueOf(n.doubleValue());
        BigDecimal lo = bound(min), hi = bound(max), def = bound(defaultValue);
        BigDecimal step = def == null ? BigDecimal.ZERO : roundDown(def.abs().divide(BigDecimal.TEN));
        if (step.signum() <= 0 && lo != null && hi != null) step = roundDown(hi.subtract(lo).divide(BigDecimal.valueOf(100)));
        if (whole) step = step.setScale(0, RoundingMode.FLOOR).max(BigDecimal.ONE);
        else if (step.signum() <= 0) step = new BigDecimal("0.01");
        BigDecimal next = v.add(direction > 0 ? step : step.negate());
        if (lo != null && next.compareTo(lo) < 0) next = lo;
        if (hi != null && next.compareTo(hi) > 0) next = hi;
        if (type == Integer.class) return next.intValue();
        if (type == Long.class) return next.longValue();
        return next.doubleValue();
    }

    private static BigDecimal bound(Object b) {
        if (b instanceof Integer || b instanceof Long) return BigDecimal.valueOf(((Number) b).longValue());
        if (b instanceof Double d && Double.isFinite(d)) return BigDecimal.valueOf(d);
        return null;
    }

    // the largest 1, 2 or 5 times a power of ten that is at most x
    private static BigDecimal roundDown(BigDecimal x) {
        if (x.signum() <= 0) return BigDecimal.ZERO;
        x = x.stripTrailingZeros();
        int exponent = x.precision() - x.scale() - 1;
        BigDecimal leading = x.scaleByPowerOfTen(-exponent);
        int digit = leading.compareTo(BigDecimal.valueOf(5)) >= 0 ? 5 : leading.compareTo(BigDecimal.valueOf(2)) >= 0 ? 2 : 1;
        return BigDecimal.valueOf(digit).scaleByPowerOfTen(exponent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Comparable value, Object bound) {
        return value.compareTo(bound);
    }
}
