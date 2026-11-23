package com.dmzzero.mixin;

import com.dmzzero.form.KiMastery;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.KiAttackData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// every ki drain of a ki technique (the cast, the charge, a beam's drain while it fires) comes from this cost.
// overhaul sets the return value at RETURN, which would skip a second injector there, so this changes dmz's last step
@Mixin(value = KiAttackData.class, remap = false)
public abstract class KiAttackCostMixin {
    // dmz's final Math.max(5.0, cost); the first max in the method is a type multiplier's floor
    @Redirect(method = "getCalculatedCost(Lcom/dragonminez/common/stats/StatsData;)D", require = 0,
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(DD)D", ordinal = 1))
    private double dmzzero$mastered(double floor, double cost, StatsData data) {
        return Math.max(floor, cost) * KiMastery.costFactor(data);
    }
}
