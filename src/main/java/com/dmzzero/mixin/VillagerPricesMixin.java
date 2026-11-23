package com.dmzzero.mixin;

import com.dmzzero.village.Villages;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// hero or villain: after vanilla's own reputation and Hero of the Village discounts, dmz's alignment changes the price
@Mixin(Villager.class)
public abstract class VillagerPricesMixin {
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void dmzzero$alignmentPrices(Player player, CallbackInfo ci) {
        Villages.adjustPrices((Villager) (Object) this, player);
    }
}
