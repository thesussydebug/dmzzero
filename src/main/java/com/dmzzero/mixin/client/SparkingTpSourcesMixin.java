package com.dmzzero.mixin.client;

import com.dmzzero.chamber.ClientChamber;
import com.dmzzero.potential.ClientPotential;
import com.dmzzero.potential.Potential;
import com.dmzzero.potential.PotentialService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "com.yami.dmzsparking.client.menu.frame.StatsPage", remap = false)
public abstract class SparkingTpSourcesMixin {

    @ModifyArg(method = "tpSources", require = 0, index = 0, at = @At(value = "INVOKE",
            target = "Lcom/yami/dmzsparking/client/menu/MenuPaint;inFont(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"))
    private Component dmzzero$potentialSource(Component line) {
        int potential = ClientPotential.get();
        double chamber = ClientChamber.tpMultiplier();
        if ((potential <= 1 && chamber <= 1.0) || !(line instanceof MutableComponent mutable)) return line;
        if (potential > 1) mutable.append(Component.translatableWithFallback("gui.dmzzero.stats.tp_source.potential", "Potential")
                .append(" x" + PotentialService.times(Potential.tpMultiplier(potential)) + "  "));
        if (chamber > 1.0) mutable.append(ClientChamber.sourceLabel().copy().append(" x" + PotentialService.times(chamber) + "  "));
        return mutable;
    }
}
