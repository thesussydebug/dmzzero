package com.dmzzero.mixin.client;

import com.dmzzero.client.AuraHexagon;
import com.dragonminez.client.gui.character.CharacterStatsScreen;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// overhaul reorders the hexagon but still draws it through this method
@Mixin(value = CharacterStatsScreen.class, remap = false)
public abstract class StatsScreenHexagonMixin {
    @Shadow
    private StatsData statsData;

    @Redirect(method = "drawHexagon", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/common/stats/character/Character;getAuraColor()Ljava/lang/String;"))
    private String dmzzero$formColor(Character character) {
        return AuraHexagon.color(statsData, character);
    }
}
