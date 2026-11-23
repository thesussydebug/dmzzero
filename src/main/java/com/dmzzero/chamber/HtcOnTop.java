package com.dmzzero.chamber;

import com.dmzzero.ZeroConfig;

import java.util.function.Supplier;

// whether dmz's own time chamber tp bonus multiplies over its other tp bonuses (StatsDataHtcTpMixin) instead of being added
public final class HtcOnTop {
    private static final ThreadLocal<Boolean> DMZS_OWN = ThreadLocal.withInitial(() -> false);

    private HtcOnTop() {
    }

    // a client on a server without dmz zero gets the fallback, which matches what that server pays
    public static boolean on() {
        return !DMZS_OWN.get() && ZeroConfig.read(ZeroConfig.SERVER.htcOnTop, false);
    }

    // the self-test reads dmz's own sum next to ours
    public static <T> T dmzsOwn(Supplier<T> read) {
        DMZS_OWN.set(true);
        try {
            return read.get();
        } finally {
            DMZS_OWN.set(false);
        }
    }
}
