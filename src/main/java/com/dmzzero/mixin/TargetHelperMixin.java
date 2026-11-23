package com.dmzzero.mixin;

import com.dmzzero.duel.Duels;
import com.dmzzero.realm.Battles;
import com.dragonminez.common.combat.logic.player.TargetHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// dmz treats party members as friends, so their ki goes straight through each other; not in a duel or a battle
@Mixin(value = TargetHelper.class, remap = false)
public abstract class TargetHelperMixin {

    @Inject(method = "getRelation", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dmzzero$fightersAreFoes(Player attacker, Entity target, CallbackInfoReturnable<TargetHelper.Relation> cir) {
        if (!(attacker instanceof ServerPlayer a) || !(target instanceof ServerPlayer t) || a == t) return;
        if (Battles.foes(a, t) || Duels.opponents(a, t)) cir.setReturnValue(TargetHelper.Relation.HOSTILE);
    }
}
