package com.dmzzero.mixin;

import com.dmzzero.lift.Carries;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// a mob a player carries rides them, and vanilla saves no rider on its own: it expects the vehicle to save it, which a
// player never does. for the world's save it counts as standing on its own, at the carrier's spot (saveWithoutId)
@Mixin(Entity.class)
public abstract class EntityCarriedSaveMixin {
    @Redirect(method = {"shouldBeSaved", "save"}, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isPassenger()Z"))
    private boolean dmzzero$riding(Entity self) {
        return self.isPassenger() && !Carries.heldByPlayer(self);
    }
}
