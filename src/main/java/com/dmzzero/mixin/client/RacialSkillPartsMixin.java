package com.dmzzero.mixin.client;

import com.dmzzero.client.DietClient;
import com.dmzzero.client.TenaciousBodyClient;
import com.dragonminez.client.gui.character.util.RacialSkillParts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

// dmz's skills menu, its race selection and sparking's skills page all list racials from here
@Mixin(value = RacialSkillParts.class, remap = false)
public abstract class RacialSkillPartsMixin {

    @Inject(method = "build", at = @At("RETURN"), cancellable = true)
    private static void dmzzero$tenaciousBody(String racialSkill, boolean android, boolean allVariants,
                                             CallbackInfoReturnable<List<RacialSkillParts.Part>> cir) {
        List<RacialSkillParts.Part> parts = new ArrayList<>(cir.getReturnValue());
        int before = parts.size();
        TenaciousBodyClient.addPart(parts, racialSkill, android, allVariants);
        DietClient.addParts(parts, racialSkill);
        if (parts.size() != before) cir.setReturnValue(parts);
    }
}
