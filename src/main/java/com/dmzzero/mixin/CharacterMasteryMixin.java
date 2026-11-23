package com.dmzzero.mixin;

import com.dmzzero.potential.Potential;
import com.dmzzero.potential.PotentialData;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = Character.class, remap = false)
public abstract class CharacterMasteryMixin {

    @Shadow
    private transient StatsData statsData;

    @ModifyVariable(method = "gainMastery", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double dmzzero$potentialMastery(double amount) {
        if (amount <= 0 || statsData == null || !(statsData.getPlayer() instanceof ServerPlayer player)) return amount;
        return amount * Potential.masteryMultiplier(PotentialData.of(player));
    }
}
