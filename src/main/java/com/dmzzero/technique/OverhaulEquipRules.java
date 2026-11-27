package com.dmzzero.technique;

import com.dmzzero.DMZZero;
import com.dragonminez.common.stats.techniques.Techniques;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

// overhaul caps how many advanced and ultimate ki attacks can be equipped, in a mixin on dmz's equip packet. a saved set
// goes into the slots without that packet, so it asks overhaul's rule itself
final class OverhaulEquipRules {
    private static Method rule;
    private static boolean looked;

    private OverhaulEquipRules() {
    }

    /** True when Overhaul wouldn't let this technique into this slot, next to what's equipped now. */
    static boolean blocks(Techniques techniques, int slot, String id) {
        Method m = rule();
        if (m == null) return false;
        try {
            return m.invoke(null, techniques, slot, id) != null;
        } catch (ReflectiveOperationException e) {
            DMZZero.LOG.warn("Overhaul's technique equip rule failed", e);
            return false;
        }
    }

    private static Method rule() {
        if (looked) return rule;
        looked = true;
        if (!ModList.get().isLoaded("dmzrevamp")) return null;
        try {
            rule = Class.forName("com.dmzrevamp.revamp.ki.KiAttackEquipRules")
                    .getMethod("blockedCategory", Techniques.class, int.class, String.class);
        } catch (ReflectiveOperationException e) {
            DMZZero.LOG.warn("Overhaul's technique equip rule not found; technique sets won't apply its limits", e);
        }
        return rule;
    }
}
