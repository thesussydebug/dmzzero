package com.dmzzero.passive;

import com.dmzzero.ZeroConfig;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.stats.StatsData;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

// human racial passive: faster stamina regen, gone once dr. gero makes you an android
public final class TenaciousBody {
    public static final String PART_ID = "racial_human.tenacious_body";
    public static final int DEFAULT_BONUS = 25;
    // overhaul swaps dmz's "human" racial for its own "humanrevamp"
    private static final Set<String> HUMAN_SKILLS = Set.of("human", "humanrevamp");
    private static final String AFTER = "racial_human.adrenaline";

    private TenaciousBody() {
    }

    public static boolean humanSkill(String racialSkill) {
        return racialSkill != null && HUMAN_SKILLS.contains(racialSkill.toLowerCase(Locale.ROOT));
    }

    public static boolean applies(String racialSkill, String race, boolean android) {
        if (android) return false;
        return humanSkill(racialSkill) || "human".equalsIgnoreCase(race);
    }

    public static double multiplier(boolean enabled, int bonusPercent, boolean applies) {
        return enabled && applies && bonusPercent > 0 ? 1.0 + bonusPercent / 100.0 : 1.0;
    }

    public static double boost(double amount, boolean enabled, int bonusPercent, boolean applies) {
        return amount * multiplier(enabled, bonusPercent, applies);
    }

    // race selection shows every variant, the player's own list drops it for androids
    public static boolean listed(boolean android, boolean allVariants) {
        return !android || allVariants;
    }

    public static <T> void insert(List<T> parts, T part, Function<T, String> id) {
        String added = id.apply(part);
        int at = parts.size();
        for (int i = 0; i < parts.size(); i++) {
            String current = id.apply(parts.get(i));
            if (added.equals(current)) return;
            if (AFTER.equals(current)) at = i + 1;
        }
        parts.add(at, part);
    }

    public static boolean enabled() {
        return ZeroConfig.read(ZeroConfig.SERVER.tenaciousBody, true);
    }

    public static int bonus() {
        return ZeroConfig.read(ZeroConfig.SERVER.tenaciousBodyBonus, DEFAULT_BONUS);
    }

    public static boolean applies(StatsData data) {
        String race = data.getCharacter().getRaceName();
        RaceCharacterConfig config = race == null ? null : ConfigManager.getRaceCharacter(race.toLowerCase(Locale.ROOT));
        return applies(config == null ? null : config.getRacialSkill(), race, data.getStatus().isAndroidUpgraded());
    }

    public static double multiplier(StatsData data) {
        return data == null ? 1.0 : multiplier(enabled(), bonus(), applies(data));
    }
}
