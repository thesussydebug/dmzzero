package com.dmzzero.mixin;

import com.dmzzero.beam.CannonDrill;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// a special beam cannon that reaches a block bores into it instead of bursting there
@Mixin(value = KiLaserEntity.class, remap = false)
public abstract class KiLaserMixin {
    @Inject(method = "explodeAndDie", at = @At("HEAD"), cancellable = true, require = 0)
    private void dmzzero$drill(Vec3 at, CallbackInfo ci) {
        if (CannonDrill.onBlockHit((KiLaserEntity) (Object) this, at)) ci.cancel();
    }
}
