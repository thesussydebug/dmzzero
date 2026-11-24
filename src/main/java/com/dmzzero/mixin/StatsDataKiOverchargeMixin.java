package com.dmzzero.mixin;

import com.dmzzero.charge.KiOvercharge;
import com.dragonminez.common.stats.StatsData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// dmz's max ki, raised by ki boost during the ki overcharge and while the extra fades. dmz and overhaul both cap ki at it.
// on dmz's own last step, min(total, float max), not at the return, so another mod setting the return value still runs
@Mixin(value = StatsData.class, remap = false)
public abstract class StatsDataKiOverchargeMixin {
    @Redirect(method = "getMaxEnergy", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F"))
    private float dmzzero$overcharge(float total, float limit) {
        return KiOvercharge.capacity((StatsData) (Object) this, Math.min(total, limit));
    }
}
