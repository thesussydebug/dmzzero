package com.dmzzero.rival;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// friendly fist knockdowns never fire a death event, so remember who hit whom last
public final class KnockdownWatch {
    public static final int WINDOW = 60;
    public static final int SAME_BEATING = 40;

    private record Hit(UUID attacker, long at) {
    }

    private final Map<UUID, Hit> hits = new HashMap<>();
    private final Map<UUID, Long> beatings = new HashMap<>();

    public void hit(UUID victim, UUID attacker, long now) {
        hits.put(victim, new Hit(attacker, now));
    }

    // who knocked the victim down, if someone hit them just before
    public UUID knockedDown(UUID victim, long now) {
        Hit h = hits.remove(victim);
        return h != null && now - h.at <= WINDOW ? h.attacker : null;
    }

    // a knockdown and the death or knockout event behind it are one beating
    public boolean firstBeating(UUID victim, long now) {
        Long last = beatings.get(victim);
        if (last != null && now - last <= SAME_BEATING) return false;
        beatings.put(victim, now);
        return true;
    }

    public void forget(UUID player) {
        hits.remove(player);
        beatings.remove(player);
    }

    public void clear() {
        hits.clear();
        beatings.clear();
    }
}
