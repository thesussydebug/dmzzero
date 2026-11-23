package com.dmzzero.mixin;

import com.dmzzero.form.Giants;
import com.dmzzero.smash.Smashes;
import com.dragonminez.server.events.players.combat.KnockbackHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// every heavy knockback in dmz goes through here: momentum punches, strike attacks, combos, the ki burst, world bosses
@Mixin(value = KnockbackHelper.class, remap = false)
public abstract class KnockbackHelperMixin {
    // a giant shrugs off a much smaller fighter's knockback
    @ModifyVariable(method = "apply", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static Vec3 dmzzero$giantSteady(Vec3 knock, LivingEntity target) {
        return Giants.steady(target, knock);
    }

    @Inject(method = "apply", at = @At("TAIL"), require = 0)
    private static void dmzzero$smash(LivingEntity target, Vec3 knock, CallbackInfo ci) {
        Smashes.launched(target, knock);
    }
}
