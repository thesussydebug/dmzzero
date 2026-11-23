package com.dmzzero.sense;

import java.util.Locale;
import java.util.regex.Pattern;

// which creatures give off ki you can sense, by entity id. androids and machines have none, as 17 and 18 couldn't be sensed
public final class CreatureKi {
    // dmz's androids: saga_a8, saga_a17, saga_super_a13
    private static final Pattern ANDROID = Pattern.compile("(^|_)(super_)?a\\d+($|_)");

    private CreatureKi() {
    }

    public static boolean hidden(String namespace, String path) {
        if (path == null) return false;
        String p = path.toLowerCase(Locale.ROOT);
        return p.contains("android") || p.contains("robot") || p.contains("gero") || ANDROID.matcher(p).find();
    }

    /** DMZ's saga fighters and world bosses: the ones whose arrival is felt. */
    public static boolean arrival(String namespace, String path) {
        if (!"dragonminez".equals(namespace) || path == null) return false;
        return (path.startsWith("saga_") || path.startsWith("worldboss_")) && !hidden(namespace, path);
    }
}
