package com.dmzzero.mixin;

import com.dmzzero.impact.ClashHooked;
import com.dmzzero.impact.ClashImpacts;
import com.dragonminez.common.combat.clash.BeamClash;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// overhaul tunes these same clashes, so one hook covers both
@Mixin(value = BeamClash.class, remap = false)
public abstract class BeamClashMixin implements ClashHooked {

    @Inject(method = "tick", at = @At("RETURN"))
    private void dmzzero$tick(CallbackInfoReturnable<?> cir) {
        BeamClash self = (BeamClash) (Object) this;
        if (self.level() instanceof ServerLevel level) {
            ClashImpacts.tick(this, level, self.clashPoint(), () -> List.of(self.a().owner(), self.b().owner()),
                    () -> new int[]{self.a().beam().getColor() & 0xFFFFFF, self.b().beam().getColor() & 0xFFFFFF});
        }
    }

    @Inject(method = "resolve", at = @At("HEAD"))
    private void dmzzero$resolve(CallbackInfo ci) {
        ClashImpacts.end(this, true);
    }

    @Inject(method = "dissolve", at = @At("HEAD"))
    private void dmzzero$dissolve(CallbackInfo ci) {
        ClashImpacts.end(this, false);
    }
}
