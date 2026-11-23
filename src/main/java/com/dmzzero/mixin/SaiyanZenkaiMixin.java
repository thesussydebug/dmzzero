package com.dmzzero.mixin;

import com.dmzzero.potential.ZenkaiPotential;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.racial.impl.SaiyanZenkai;
import com.dragonminez.common.stats.StatsData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// dmz's own zenkai: both the lethal-blow one and the temporary one from its trigger command
@Mixin(value = SaiyanZenkai.class, remap = false)
public abstract class SaiyanZenkaiMixin {

    @ModifyArg(method = {"trigger", "applyTemporaryBuff"}, at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/common/racial/impl/SaiyanZenkai;applyBuff(Lcom/dragonminez/common/stats/StatsData;Lcom/dragonminez/common/config/GeneralServerConfig$SaiyanRacialConfig;DZDJ)V"),
            index = 2)
    private static double dmzzero$potential(StatsData data, GeneralServerConfig.SaiyanRacialConfig config, double buffPct,
                                            boolean isMaxBuff, double potency, long gameTime) {
        return ZenkaiPotential.scale(data, buffPct, "dmz");
    }
}
