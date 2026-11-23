package com.dmzzero.mixin.client;

import com.dmzzero.client.AuraHexagon;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.yami.dmzsparking.client.menu.frame.StatsPage", remap = false)
public abstract class SparkingHexagonMixin {
    @Shadow
    private StatsData stats;

    @Redirect(method = "hexagon", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/common/stats/character/Character;getAuraColor()Ljava/lang/String;"))
    private String dmzzero$formColor(Character character) {
        return AuraHexagon.color(stats, character);
    }
}
