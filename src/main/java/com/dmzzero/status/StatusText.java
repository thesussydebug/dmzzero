package com.dmzzero.status;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;

// numbers as /dmzzero status prints them
public final class StatusText {
    private StatusText() {
    }

    /** 0.4249 reads "0.42"; anything above 0 but below 0.005 reads "<0.01" so it doesn't look free. */
    public static String ms(double ms) {
        if (ms <= 0) return "0";
        if (ms < 0.005) return "<0.01";
        return String.format(Locale.ROOT, "%.2f", ms);
    }

    public static String percent(double part, double whole) {
        if (part <= 0 || whole <= 0) return "0%";
        long p = Math.round(part / whole * 100);
        return p < 1 ? "<1%" : p + "%";
    }

    /** 20 ticks a second: 1200 reads "60", 30 reads "1.5". */
    public static String seconds(int ticks) {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
    }

    /** Every name, sorted without regard to case, joined with commas. */
    public static String names(Collection<String> names) {
        return names.stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", "));
    }
}
