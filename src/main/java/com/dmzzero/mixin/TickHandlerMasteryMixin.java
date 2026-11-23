package com.dmzzero.mixin;

import com.dmzzero.chamber.ChamberMastery;
import com.dragonminez.server.events.players.TickHandler;
import com.dragonminez.server.util.PotionEffectHelper;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// regenerateEnergy runs this only on dmz's passive form mastery, once for the form and once for the stack form
@Mixin(value = TickHandler.class, remap = false)
public abstract class TickHandlerMasteryMixin {
    @Redirect(method = "regenerateEnergy", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/server/util/PotionEffectHelper;applyMasteryGainMultiplier(Lnet/minecraft/world/entity/LivingEntity;D)D"))
    private static double dmzzero$chamberMastery(LivingEntity entity, double amount) {
        return ChamberMastery.passive(entity, PotionEffectHelper.applyMasteryGainMultiplier(entity, amount));
    }
}
