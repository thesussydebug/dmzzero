package com.dmzzero.mixin;

import com.dmzzero.impact.ClashHooked;
import com.dmzzero.impact.ClashImpacts;
import com.dragonminez.common.combat.clash.StrikeClash;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StrikeClash.class, remap = false)
public abstract class StrikeClashMixin implements ClashHooked {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    private Vec3 clashPoint() {
        throw new AssertionError();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void dmzzero$tick(CallbackInfoReturnable<?> cir) {
        Vec3 point = clashPoint();
        // the two fighters are the ones dmz counts as clashing right at the clash point
        ClashImpacts.tick(this, level, point, () -> level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(4.0),
                e -> StrikeClash.isClashing(e.getUUID())));
    }

    @Inject(method = "conclude", at = @At("HEAD"))
    private void dmzzero$conclude(CallbackInfo ci) {
        ClashImpacts.end(this, true);
    }
}
