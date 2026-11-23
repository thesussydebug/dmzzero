package com.dmzzero.impact;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.dmzzero.impact.ImpactProfile.*;

public final class ImpactProfiles {
    public static final ImpactProfile BASE = new ImpactProfile(1, CRACKS);

    private static final Map<String, ImpactProfile> FORMS = new HashMap<>();
    private static final Map<String, ImpactProfile> STACKS = new HashMap<>();
    // dmz sparking 1.5.1 turns dmz's majin line around (evil, super, ultra, kid): each place keeps dmz's numbers and
    // takes the next form's name and look, so these four hit by their place in the line, not by their name
    private static final List<String> MAJIN_LINE = List.of("kid", "evil", "super", "ultra");

    static {
        form("saiyan", "supersaiyan", 2, FLICKER | LIGHTNING | SHOCKWAVE);
        form("saiyan", "falsesupersaiyan", 2, FLICKER | LIGHTNING | CRACKS | SHOCKWAVE);
        form("saiyan", "supersaiyanmastered", 2, FLICKER | SHOCKWAVE | CALM);
        form("saiyan", "supersaiyangrade2", 3, FLICKER | CRACKS | QUAKE | SHOCKWAVE);
        form("saiyan", "supersaiyangrade3", 3, FLICKER | CRACKS | QUAKE | SHOCKWAVE);
        form("saiyan", "supersaiyan2", 3, FLICKER | LIGHTNING | CRACKS | LIGHT | SHOCKWAVE);
        form("saiyan", "supersaiyan3", 4, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("saiyan", "supersaiyan4", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("saiyan", "oozaru", 3, CRACKS | QUAKE | SHOCKWAVE);
        form("saiyan", "goldenoozaru", 4, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("saiyan", "supersaiyangodritual", 4, FLICKER | LIGHT | SHOCKWAVE | CALM);
        form("saiyan", "ikari", 2, LIGHTNING | CRACKS | SHOCKWAVE);
        form("saiyan", "ssjhybrid", 4, FLICKER | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("saiyan", "ssjfullpower", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);

        form("human", "buffed", 1, CRACKS | SHOCKWAVE);
        form("human", "fullpower", 2, CRACKS | QUAKE | SHOCKWAVE);
        form("human", "overdrive", 3, LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("human", "solaris", 4, FLICKER | CRACKS | LIGHT | SHOCKWAVE);
        form("human", "androidbase", 1, LIGHTNING | SHOCKWAVE);
        form("human", "superandroid", 2, LIGHTNING | CRACKS | SHOCKWAVE);
        form("human", "fusedandroid", 3, LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("human", "superfusedandroid", 4, LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("human", "shiyoken", 2, FLICKER | LIGHT | SHOCKWAVE);
        form("human", "shin_shiyoken", 3, FLICKER | CRACKS | LIGHT | SHOCKWAVE);
        form("human", "chou_shiyoken", 4, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);

        form("namekian", "giant", 3, CRACKS | QUAKE | SHOCKWAVE);
        form("namekian", "fullpower", 2, CRACKS | SHOCKWAVE);
        form("namekian", "supernamekian", 3, FLICKER | LIGHTNING | CRACKS | LIGHT | SHOCKWAVE);
        form("namekian", "supernamekian2", 4, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("namekian", "ultranamekian", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("namekian", "evilnamek", 2, DARK | CRACKS | SHOCKWAVE);
        form("namekian", "evilgiantnamek", 3, DARK | CRACKS | QUAKE | SHOCKWAVE);
        form("namekian", "buffednamek", 3, CRACKS | QUAKE | SHOCKWAVE);

        form("frostdemon", "second", 2, CRACKS | QUAKE | SHOCKWAVE);
        form("frostdemon", "third", 3, CRACKS | QUAKE | SHOCKWAVE);
        form("frostdemon", "final", 3, FLICKER | LIGHT | SHOCKWAVE);
        form("frostdemon", "fullpower", 4, LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("frostdemon", "fifth", 5, DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("frostdemon", "mecha", 2, LIGHTNING | SHOCKWAVE);
        form("frostdemon", "metal", 3, FLICKER | LIGHTNING | SHOCKWAVE);
        form("frostdemon", "metalcore", 4, LIGHTNING | CRACKS | LIGHT | SHOCKWAVE);

        form("bioandroid", "semiperfect", 2, CRACKS | SHOCKWAVE);
        form("bioandroid", "perfect", 3, FLICKER | LIGHT | SHOCKWAVE);
        form("bioandroid", "superperfect", 4, LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("bioandroid", "ultraperfect", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("bioandroid", "xeno", 3, DARK | CRACKS | SHOCKWAVE);
        form("bioandroid", "xenofp", 4, DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("bioandroid", "xenomax", 5, DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);

        form("majin", "kid", 2, DARK | SHOCKWAVE);
        form("majin", "evil", 2, DARK | LIGHTNING | CRACKS | SHOCKWAVE);
        form("majin", "super", 3, DARK | CRACKS | QUAKE | SHOCKWAVE);
        form("majin", "ultra", 4, DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("majin", "innocencedemon", 3, DARK | CRACKS | SHOCKWAVE);
        form("majin", "giantinnocencedemon", 4, DARK | CRACKS | QUAKE | SHOCKWAVE);
        form("majin", "superdemon", 5, DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);

        form("glind", "buffed", 1, CRACKS | SHOCKWAVE);
        form("glind", "outofcontrol", 3, LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("glind", "super", 3, FLICKER | LIGHTNING | CRACKS | SHOCKWAVE);
        form("glind", "absorption", 3, DARK | CRACKS | SHOCKWAVE);
        form("glind", "trueform", 4, FLICKER | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("glind", "transcended", 4, FLICKER | LIGHT | SHOCKWAVE | CALM);
        form("glind", "darkking", 4, DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("glind", "timepower_darkking", 5, DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("glind", "timepower_realmoflight", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);

        // forms dmz sparking writes into dmz's config
        form("saiyan", "supersaiyangod", 4, FLICKER | LIGHT | SHOCKWAVE | CALM);
        form("saiyan", "supersaiyanblue", 4, FLICKER | CRACKS | LIGHT | SHOCKWAVE);
        form("saiyan", "supersaiyanblueevolved", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("saiyan", "supersaiyanblueperfected", 5, FLICKER | LIGHT | SHOCKWAVE | CALM);
        form("saiyan", "supersaiyanrose", 4, FLICKER | DARK | LIGHTNING | CRACKS | SHOCKWAVE);
        form("saiyan", "supersaiyanroseevolved", 5, FLICKER | DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("saiyan", "supersaiyanrose3", 5, FLICKER | DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("saiyan", "supersaiyanrosefullpower", 5, FLICKER | DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("saiyan", "supersaiyan4fp", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("saiyan", "supersaiyan4lb", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("saiyan", "ssjlegendary3", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("saiyan", "ssjlegendary4", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("saiyan", "ssj5", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("saiyan", "beast", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("human", "awakened", 4, FLICKER | LIGHTNING | CRACKS | LIGHT | SHOCKWAVE);
        form("namekian", "potentialunleashed", 4, LIGHT | SHOCKWAVE | CALM);
        form("namekian", "golden", 4, FLICKER | CRACKS | LIGHT | SHOCKWAVE);
        form("namekian", "awakenedgoldengiant", 5, FLICKER | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("namekian", "supernamekiangod", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("bioandroid", "perfectgod", 4, FLICKER | LIGHT | SHOCKWAVE | CALM);
        form("bioandroid", "perfectblue", 5, FLICKER | LIGHTNING | CRACKS | LIGHT | SHOCKWAVE);
        form("bioandroid", "corruptedgiant", 5, DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        // new in dmz sparking 1.5.1: the divine lines stay calm, the android lines tear things up
        form("glind", "consecrated", 4, LIGHT | SHOCKWAVE | CALM);
        form("glind", "kibitokai", 5, LIGHT | SHOCKWAVE | CALM);
        form("glind", "halo", 5, FLICKER | LIGHTNING | LIGHT | SHOCKWAVE | CALM);
        form("human", "godclass", 4, FLICKER | LIGHT | SHOCKWAVE | CALM);
        form("human", "supergodclass", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("human", "hellfusion", 4, DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("human", "furtherevolution", 5, DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("human", "ultimateandroid", 5, DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("human", "trueform", 4, FLICKER | CRACKS | QUAKE | SHOCKWAVE);
        form("human", "ravenous", 4, DARK | CRACKS | QUAKE | SHOCKWAVE);
        form("human", "connoisseur", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("human", "soullink", 5, FLICKER | LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        form("human", "perfectfeast", 5, DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        form("majin", "shapeup", 2, CRACKS | SHOCKWAVE);
        form("majin", "kaiawakening", 4, LIGHT | SHOCKWAVE | CALM);
        form("majin", "grandsupremekai", 5, FLICKER | LIGHT | SHOCKWAVE | CALM);

        stack("kaioken", "x2", 1, CRACKS | SHOCKWAVE);
        stack("kaioken", "x3", 2, CRACKS | SHOCKWAVE);
        stack("kaioken", "x4", 2, CRACKS | QUAKE | SHOCKWAVE);
        stack("kaioken", "x10", 3, CRACKS | QUAKE | SHOCKWAVE);
        stack("kaioken", "x20", 4, LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        stack("kaioken", "x100", 5, LIGHTNING | CRACKS | QUAKE | LIGHT | SHOCKWAVE);
        stack("ultrainstinct", "sign", 3, LIGHT | SHOCKWAVE | CALM);
        stack("ultrainstinct", "mastered", 4, FLICKER | LIGHT | SHOCKWAVE | CALM);
        stack("ultraego", "sign", 3, DARK | CRACKS | QUAKE | SHOCKWAVE);
        stack("ultraego", "mastered", 4, FLICKER | DARK | LIGHTNING | CRACKS | QUAKE | SHOCKWAVE);
        stack("ultimate", "ultimate", 3, LIGHT | SHOCKWAVE | CALM);
        // dmz sparking's stacks: the god ki combinations add little power, the giant namekian is a giant
        stack("godkienhancement", "controlled", 1, LIGHT | SHOCKWAVE | CALM);
        stack("godkienhancement", "rage", 2, LIGHTNING | SHOCKWAVE);
        stack("godkienhancement", "primalgod", 2, LIGHTNING | SHOCKWAVE);
        stack("godkienhancement", "awakenedheir", 2, LIGHTNING | LIGHT | SHOCKWAVE);
        stack("godkienhancement", "stolendivinity", 2, DARK | LIGHTNING | SHOCKWAVE);
        stack("giant", "giant", 3, CRACKS | QUAKE | SHOCKWAVE);
        stack("mimicry", "mimicry", 2, LIGHTNING | SHOCKWAVE);
        stack("overclock", "overclock", 2, LIGHTNING | CRACKS | SHOCKWAVE);
    }

    private ImpactProfiles() {
    }

    private static void form(String race, String form, int tier, int flags) {
        FORMS.put(race + ":" + form, new ImpactProfile(tier, flags));
    }

    private static void stack(String group, String form, int tier, int flags) {
        STACKS.put(group + ":" + form, new ImpactProfile(tier, flags));
    }

    private static String key(String a, String b) {
        return (a == null ? "" : a.toLowerCase(Locale.ROOT)) + ":" + (b == null ? "" : b.toLowerCase(Locale.ROOT));
    }

    public static boolean known(String race, String form) {
        return FORMS.containsKey(key(race, form));
    }

    public static boolean knownStack(String group, String form) {
        return STACKS.containsKey(key(group, form));
    }

    // index is the form's place in its group, used for forms added by configs or other mods
    public static ImpactProfile lookup(String race, String group, String form, boolean stack, int index, boolean hasLightnings) {
        ImpactProfile found = stack ? STACKS.get(key(group, form)) : FORMS.get(key(race, majinPlace(race, group, form, index)));
        return found != null ? found : fallback(index, hasLightnings);
    }

    static String majinPlace(String race, String group, String form, int index) {
        if (!"majin".equalsIgnoreCase(race) || !"pureforms".equalsIgnoreCase(group) || form == null) return form;
        boolean inLine = MAJIN_LINE.contains(form.toLowerCase(Locale.ROOT));
        return inLine && index >= 0 && index < MAJIN_LINE.size() ? MAJIN_LINE.get(index) : form;
    }

    // mastering a form tames it: first it stops flickering, then it stops breaking things.
    // lightning and darkness are part of the form's look, so a mastered form keeps them
    public static ImpactProfile calm(ImpactProfile p, double mastery, double flickerAt, double calmAt) {
        if (p == null) return null;
        if (mastery >= calmAt) return new ImpactProfile(p.tier(), (p.flags() & (LIGHT | SHOCKWAVE | LIGHTNING | DARK)) | CALM);
        if (mastery >= flickerAt && p.has(FLICKER)) return new ImpactProfile(p.tier(), p.flags() & ~FLICKER);
        return p;
    }

    static ImpactProfile fallback(int index, boolean hasLightnings) {
        int tier = Math.max(1, Math.min(5, index + 1));
        int flags = FLICKER | CRACKS | SHOCKWAVE;
        if (hasLightnings) flags |= LIGHTNING;
        if (tier >= 3) flags |= QUAKE;
        if (tier >= 4) flags |= LIGHT;
        return new ImpactProfile(tier, flags);
    }
}
