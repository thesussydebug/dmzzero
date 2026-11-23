package com.dmzzero.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.core.animation.AnimationController;

// geckolib 4.8.4 moves a controller on to the next step of a queued animation (base.ki_overcharge_start, then
// base.ki_overcharge) without noting that it took a step off the queue at this frame time. when the same entity is
// processed again in that frame (dmz draws saga npcs and afterimages with the player's model), the transition takes
// another step off the now empty queue, and the controller stops with nothing to play. dmz keeps asking for the same
// animation, which geckolib thinks is already set, so the pose stays lost until the player lets go. noting the time
// here is what geckolib's own first take already does, so the second pass leaves the queue alone
@Mixin(value = AnimationController.class, remap = false)
public abstract class GeckoStagePollMixin {
    @Shadow
    protected double lastPollTime;

    @Inject(method = "processCurrentAnimation", require = 0,
            at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;", ordinal = 0, shift = At.Shift.AFTER))
    private void dmzzero$noteStepTaken(double adjustedTick, double seekTime, boolean crashWhenCantFindBone, CallbackInfo ci) {
        this.lastPollTime = seekTime;
    }
}
