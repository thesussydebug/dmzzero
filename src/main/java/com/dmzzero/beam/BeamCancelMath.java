package com.dmzzero.beam;

// a key that ends your own ki wave early
public final class BeamCancelMath {
    /** The beam is still charging: let it go without firing. */
    public static final int DROP = -1;
    public static final int COOLDOWN_TICKS = 10;

    private BeamCancelMath() {
    }

    /** The life to give the beam: a firing beam ends on its next tick, the way DMZ ends it when its time is up. */
    public static int lifeFor(int tickCount, boolean firing) {
        return firing ? tickCount : DROP;
    }

    /** lastUse below zero means never. */
    public static boolean ready(long now, long lastUse) {
        return lastUse < 0 || now - lastUse >= COOLDOWN_TICKS;
    }
}
