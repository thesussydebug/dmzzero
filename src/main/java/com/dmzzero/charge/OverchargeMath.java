package com.dmzzero.charge;

// ki boost's overcharge with no minecraft state: how far past full ki goes, how fast it fills while charging, how it
// fades back afterwards, and how the hud writes it
public final class OverchargeMath {
    private OverchargeMath() {
    }

    /** The share past full a Ki Boost level gives, in percent: {@code perLevel} for each level, at most {@code most}. */
    public static double extraPercent(int level, double perLevel, double most) {
        if (level <= 0 || !(perLevel > 0) || !(most > 0)) return 0.0;
        return Math.min(level * perLevel, most);
    }

    /** The ki cap: {@code max} raised by {@code percent}, only during an overcharge. */
    public static float capacity(float max, double percent, boolean overcharging) {
        if (!overcharging || !(percent > 0) || !(max > 0)) return max;
        return (float) Math.min(max * (1.0 + percent / 100.0), Float.MAX_VALUE / 2);
    }

    /** The least ki a tick of charging adds during an overcharge: the whole extra in {@code seconds}, nothing for 0. */
    public static float fillPerTick(float normal, double percent, int seconds) {
        return seconds <= 0 || !(percent > 0) ? 0f : (float) (normal * percent / 100.0 / (seconds * 20.0));
    }

    /**
     * Ki after {@code ticks} of charging during an overcharge, from {@code last} to {@code current} by DMZ's own
     * charging: at least {@code perTick} a tick, never past the cap. Ki that went down (spent) isn't given back.
     */
    public static float topUp(float last, float current, float cap, float perTick, int ticks) {
        float gained = current - last;
        if (current >= cap || gained < 0f || perTick <= 0f) return current;
        float least = perTick * ticks;
        return gained >= least ? current : Math.min(cap, last + least);
    }

    /** How much of the extra fades each tick: the whole extra in {@code seconds}, all at once for 0. */
    public static float fadePerTick(float normal, double percent, int seconds) {
        return seconds <= 0 ? Float.MAX_VALUE : (float) (normal * percent / 100.0 / (seconds * 20.0));
    }

    /** The cap after one fade step: lower by {@code step}, never above the ki held, never under full. */
    public static float fade(float ceiling, float current, float normal, float step) {
        float next = Math.min(ceiling - step, Math.max(current, normal));
        return Math.max(normal, next);
    }

    /** The hud's percent for ki above full, to one decimal ("112.5%"); null at or under full, where DMZ's text stays. */
    public static String percentText(float shown, float max) {
        if (!(max > 0f) || !(shown > max)) return null;
        double tenths = Math.round(shown / (double) max * 1000.0);
        return tenths % 10.0 == 0.0 ? (long) (tenths / 10.0) + "%" : tenths / 10.0 + "%";
    }

    /** Whether a hud bar showing {@code value} of {@code max} is the local player's ki held at full by the client copy. */
    public static boolean heldAtFull(float value, float max, float serverMax) {
        if (!(max > 0f) || !(serverMax > 0f)) return false;
        return value >= max * 0.99f && Math.abs(max - serverMax) <= max * 0.01f;
    }

    /** Whether the client should hear about a new overcharged ki value: a change of at least a tenth of a percent. */
    public static boolean worthSending(float sent, float now, float normal) {
        return Math.abs(now - sent) >= Math.max(1e-3f, normal * 0.001f);
    }
}
