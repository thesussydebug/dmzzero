package com.dmzzero.impact;

import com.dmzzero.ZeroConfig;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// ties dmz form data to impact profiles, works on both sides (dmz syncs form configs to clients)
public final class ImpactLookup {
    private static final Map<String, ImpactProfile> CACHE = new ConcurrentHashMap<>();

    private ImpactLookup() {
    }

    public static ImpactProfile profile(String race, String group, FormConfig.FormData form, boolean stack) {
        if (form == null) return null;
        String name = form.getName();
        if (name == null || name.isEmpty()) return null;
        String key = (stack ? "s|" : "f|") + race + "|" + group + "|" + name;
        return CACHE.computeIfAbsent(key, k -> ImpactProfiles.lookup(race, group, name, stack,
                index(race, group, name, stack), Boolean.TRUE.equals(form.getHasLightnings())));
    }

    public static ImpactProfile profile(String race, String group, String form, boolean stack) {
        if (form == null || form.isEmpty()) return null;
        FormConfig config = config(race, group, stack);
        FormConfig.FormData data = config != null ? find(config, form) : null;
        if (data != null) return profile(race, group, data, stack);
        return ImpactProfiles.lookup(race, group, form, stack, 0, false);
    }

    // the first of the race's form groups that has this form
    public static String groupWith(String race, String form) {
        Map<String, FormConfig> groups = ConfigManager.getAllFormsForRace(race);
        if (groups == null) return null;
        for (Map.Entry<String, FormConfig> e : groups.entrySet()) {
            if (e.getValue() != null && find(e.getValue(), form) != null) return e.getKey();
        }
        return null;
    }

    public static FormConfig.FormData formData(String race, String group, String form, boolean stack) {
        FormConfig config = config(race, group, stack);
        return config != null && form != null ? find(config, form) : null;
    }

    // what the player's current look hits like, base form included, tamed by how well they know it
    public static ImpactProfile current(StatsData data) {
        Character c = data.getCharacter();
        ImpactProfile form = forPlayer(data, c.getActiveFormGroup(), activeFormData(c), false);
        ImpactProfile stack = c.hasActiveStackForm()
                ? forPlayer(data, c.getActiveStackFormGroup(), c.getActiveStackFormData(), true) : null;
        ImpactProfile both = ImpactProfile.strongest(form, stack);
        return both != null ? both : ImpactProfiles.BASE;
    }

    public static ImpactProfile forPlayer(StatsData data, String group, FormConfig.FormData form, boolean stack) {
        return calmed(data, profile(data.getCharacter().getRaceName(), group, form, stack), group, form, stack);
    }

    public static ImpactProfile forPlayer(StatsData data, String group, String form, boolean stack) {
        String race = data.getCharacter().getRaceName();
        FormConfig.FormData found = formData(race, group, form, stack);
        return found != null ? forPlayer(data, group, found, stack) : profile(race, group, form, stack);
    }

    public static ImpactProfile calmed(StatsData data, ImpactProfile p, String group, FormConfig.FormData form, boolean stack) {
        if (p == null || form == null || !ZeroConfig.read(ZeroConfig.SERVER.masteryCalms, true)) return p;
        return ImpactProfiles.calm(p, mastery(data, group, form, stack),
                ZeroConfig.read(ZeroConfig.SERVER.smoothAt, 0.5), ZeroConfig.read(ZeroConfig.SERVER.calmAt, 1.0));
    }

    // share of the form's max mastery, synced to every client that sees the player
    public static double mastery(StatsData data, String group, FormConfig.FormData form, boolean stack) {
        return ImpactMath.masteryRatio(rawMastery(data, group, form == null ? null : form.getName(), stack),
                form == null ? null : form.getMaxMastery());
    }

    public static double rawMastery(StatsData data, String group, String form, boolean stack) {
        if (group == null || form == null) return 0;
        Character c = data.getCharacter();
        return (stack ? c.getStackFormMasteries() : c.getFormMasteries()).getMastery(group, form);
    }

    private static FormConfig.FormData activeFormData(Character c) {
        String form = c.getActiveForm();
        if (form == null || form.isEmpty() || "base".equalsIgnoreCase(form)) return null;
        return c.getActiveFormData();
    }

    // a reverting player also fires a form change, only going up counts
    public static boolean isAscent(String race, String oldGroup, String oldForm, String newGroup, String newForm, boolean stack) {
        if (newForm == null || newForm.isEmpty()) return false;
        if (oldForm == null || oldForm.isEmpty() || "base".equalsIgnoreCase(oldForm)) return true;
        if (oldGroup == null || !oldGroup.equalsIgnoreCase(newGroup)) return true;
        return index(race, newGroup, newForm, stack) > index(race, oldGroup, oldForm, stack);
    }

    static int index(String race, String group, String form, boolean stack) {
        FormConfig config = config(race, group, stack);
        if (config == null || form == null) return 0;
        int i = 0;
        for (String key : config.getForms().keySet()) {
            if (key.equalsIgnoreCase(form)) return i;
            i++;
        }
        return 0;
    }

    private static FormConfig config(String race, String group, boolean stack) {
        if (group == null || group.isEmpty()) return null;
        try {
            return stack ? ConfigManager.getStackFormGroup(group) : ConfigManager.getFormGroup(race, group);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static FormConfig.FormData find(FormConfig config, String form) {
        for (Map.Entry<String, FormConfig.FormData> e : config.getForms().entrySet()) {
            if (e.getKey().equalsIgnoreCase(form)) return e.getValue();
        }
        return null;
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
