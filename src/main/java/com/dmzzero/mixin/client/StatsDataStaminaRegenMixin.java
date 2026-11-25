package com.dmzzero.mixin.client;

import com.dmzzero.client.TenaciousBodyClient;
import com.dmzzero.passive.TenaciousBody;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// only menus read this on the client; the server's regen gets the bonus through dmz's regen event
@Mixin(value = StatsData.class, remap = false)
public abstract class StatsDataStaminaRegenMixin {

    @Inject(method = "getStaminaRegenPerSecond", at = @At("RETURN"), cancellable = true, require = 0)
    private void dmzzero$tenaciousBody(CallbackInfoReturnable<Double> cir) {
        StatsData data = (StatsData) (Object) this;
        Player player = data.getPlayer();
        if (player == null || !player.level().isClientSide() || !TenaciousBodyClient.active()) return;
        double multiplier = TenaciousBody.multiplier(data);
        if (multiplier != 1.0) cir.setReturnValue(cir.getReturnValueD() * multiplier);
    }
}
