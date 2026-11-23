package com.dmzzero.mixin;

import com.dmzzero.chamber.ChamberMath;
import com.dmzzero.chamber.ClientChamber;
import com.dmzzero.chamber.HtcOnTop;
import com.dmzzero.potential.ClientPotential;
import com.dmzzero.potential.Potential;
import com.dragonminez.common.config.TpSource;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.server.world.dimension.HTCDimension;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// dmz adds its time chamber bonus into one sum with class, gravity, weights and the rest. this leaves it out of the sum
// and multiplies the sum by it instead: in getTpSourceMultiplier (what every real tp gain uses) and in getTpTotalMultiplier
// (the menus, and overhaul's growth xp). getTpAdditiveMultiplier is only called by getTpTotalMultiplier, so its sum
// leaving the chamber out is put right there.
// the multiplying happens on dmz's own last step, max(0, sum), not at the return: overhaul sets the return value of both
// methods for its prestige bonus, and once one injector sets it, no other injector at that return runs.
// only inside the chamber: sparking 1.5.1 returns its whis' staff bonus through the same getTpHTCMultiplier, meant to be added
@Mixin(value = StatsData.class, remap = false)
public abstract class StatsDataHtcTpMixin {
    @Unique
    private double dmzzero$sourceHtc = 1.0;
    @Unique
    private double dmzzero$totalHtc = 1.0;

    @Inject(method = "getTpSourceMultiplier", at = @At("HEAD"), require = 0)
    private void dmzzero$sourceStart(TpSource source, CallbackInfoReturnable<Double> cir) {
        dmzzero$sourceHtc = 1.0;
    }

    @Redirect(method = "getTpSourceMultiplier", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/common/stats/StatsData;getTpHTCMultiplier()D"))
    private double dmzzero$leaveHtcOutOfSource(StatsData data) {
        double htc = data.getTpHTCMultiplier();
        if (htc == 1.0 || !HtcOnTop.on() || !data.getPlayer().level().dimension().equals(HTCDimension.HTC_KEY)) return htc;
        dmzzero$sourceHtc = htc;
        return 1.0;
    }

    @Redirect(method = "getTpSourceMultiplier", require = 0, at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(DD)D"))
    private double dmzzero$sourceOnTop(double floor, double sum) {
        double value = Math.max(floor, sum);
        return dmzzero$sourceHtc != 1.0 ? ChamberMath.htcOnTop(value, dmzzero$sourceHtc) : value;
    }

    @Inject(method = "getTpTotalMultiplier", at = @At("HEAD"), require = 0)
    private void dmzzero$totalStart(CallbackInfoReturnable<Double> cir) {
        dmzzero$totalHtc = 1.0;
    }

    @Redirect(method = "getTpAdditiveMultiplier", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/common/stats/StatsData;getTpHTCMultiplier()D"))
    private double dmzzero$leaveHtcOutOfTotal(StatsData data) {
        double htc = data.getTpHTCMultiplier();
        if (htc == 1.0 || !HtcOnTop.on() || !data.getPlayer().level().dimension().equals(HTCDimension.HTC_KEY)) return htc;
        dmzzero$totalHtc = htc;
        return 1.0;
    }

    // the menus' total also counts potential and the chamber's depth, on the client copy only: on the server overhaul's
    // growth xp reads this total, and the real tp gains get both through the TPGainEvent
    @Redirect(method = "getTpTotalMultiplier", require = 0, at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(DD)D"))
    private double dmzzero$totalOnTop(double floor, double sum) {
        double value = Math.max(floor, sum);
        if (dmzzero$totalHtc != 1.0) value = ChamberMath.htcOnTop(value, dmzzero$totalHtc);
        Player player = ((StatsData) (Object) this).getPlayer();
        if (player != null && player.level().isClientSide()) value *= Potential.tpMultiplier(ClientPotential.get()) * ClientChamber.tpMultiplier();
        return value;
    }
}
