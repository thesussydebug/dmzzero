package com.dmzzero.mixin;

import com.dmzzero.lift.Carries;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// an animal or villager a player carries sits over their head instead of in it. Runs on both sides, so it lines up
@Mixin(Entity.class)
public abstract class CarryOverheadMixin {

    @Inject(method = "getPassengersRidingOffset", at = @At("HEAD"), cancellable = true, require = 0)
    private void dmzzero$overhead(CallbackInfoReturnable<Double> cir) {
        Double y = Carries.overhead((Entity) (Object) this);
        if (y != null) cir.setReturnValue(y);
    }
}
