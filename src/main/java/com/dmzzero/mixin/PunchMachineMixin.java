package com.dmzzero.mixin;

import com.dmzzero.punch.PunchRecords;
import com.dragonminez.common.init.entities.PunchMachineEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// every hit dmz scores on its punch machine goes to the records too
@Mixin(value = PunchMachineEntity.class, remap = false)
public abstract class PunchMachineMixin {
    @Inject(method = "processHit", at = @At("HEAD"), require = 0)
    private void dmzzero$record(float damage, Player player, CallbackInfo ci) {
        PunchRecords.onHit((PunchMachineEntity) (Object) this, player, damage);
    }
}
