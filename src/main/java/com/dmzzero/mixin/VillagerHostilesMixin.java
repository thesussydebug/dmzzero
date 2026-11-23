package com.dmzzero.mixin;

import com.dmzzero.village.Villages;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// villagers see an evil fighter coming the way they see a zombie, and run; enough panicking villagers call a golem
@Mixin(VillagerHostilesSensor.class)
public abstract class VillagerHostilesMixin {
    @Inject(method = "isMatchingEntity", at = @At("HEAD"), cancellable = true)
    private void dmzzero$villain(LivingEntity villager, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (Villages.scaresVillagers(target)) cir.setReturnValue(Villages.closeEnough(villager, target));
    }
}
