package com.dmzzero.form;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.impact.ImpactLookup;
import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// a fully mastered transformation: ki techniques cost less (KiAttackCostMixin, on both sides so dmz's cost display
// matches) and ki blasts hit harder
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class KiMastery {

    private KiMastery() {
    }

    /** Transformed, and every active form and stack form is at its max mastery. */
    public static boolean mastered(StatsData data) {
        if (data == null) return false;
        Character c = data.getCharacter();
        String form = c.getActiveForm();
        boolean hasForm = c.hasActiveForm() && form != null && !form.isEmpty() && !"base".equalsIgnoreCase(form) && c.getActiveFormData() != null;
        boolean hasStack = c.hasActiveStackForm() && c.getActiveStackFormData() != null;
        double formShare = hasForm ? ImpactLookup.mastery(data, c.getActiveFormGroup(), c.getActiveFormData(), false) : 0;
        double stackShare = hasStack ? ImpactLookup.mastery(data, c.getActiveStackFormGroup(), c.getActiveStackFormData(), true) : 0;
        return KiMasteryMath.mastered(hasForm, formShare, hasStack, stackShare);
    }

    /** What DMZ's cost of a ki technique is multiplied by. Read the server's way, so the client's display agrees. */
    public static double costFactor(StatsData data) {
        if (!ZeroConfig.read(ZeroConfig.SERVER.kiMastery, false)) return 1.0;
        return KiMasteryMath.costFactor(mastered(data), ZeroConfig.read(ZeroConfig.SERVER.kiMasteryCost, 1.0));
    }

    // after dmz's own hurt handling (HIGH), so the bonus is on the damage that lands
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getAmount() <= 0 || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (!MainDamageTypes.isKiblastDamage(event.getSource()) || !ZeroConfig.SERVER.kiMastery.get()) return;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, attacker).orElse(null);
        if (!mastered(data)) return;
        event.setAmount(KiMasteryMath.damage(event.getAmount(), true, ZeroConfig.SERVER.kiMasteryDamage.get()));
    }
}
