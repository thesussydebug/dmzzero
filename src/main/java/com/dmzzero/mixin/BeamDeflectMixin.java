package com.dmzzero.mixin;

import com.dmzzero.beam.BeamDeflect;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// a beam reaching someone much stronger who is blocking it is knocked up into the sky instead of hitting
@Mixin(value = AbstractKiProjectile.class, remap = false)
public abstract class BeamDeflectMixin {
    @Inject(method = "applyBeamHit", at = @At("HEAD"), cancellable = true, require = 0)
    private void dmzzero$deflect(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof KiWaveEntity wave && BeamDeflect.blocks(wave, target)) cir.setReturnValue(false);
    }
}
