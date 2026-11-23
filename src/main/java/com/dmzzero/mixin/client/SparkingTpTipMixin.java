package com.dmzzero.mixin.client;

import com.dmzzero.chamber.ClientChamber;
import com.dmzzero.potential.ClientPotential;
import com.dmzzero.potential.PotentialService;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "com.yami.dmzsparking.client.menu.frame.StatsTexts", remap = false)
public abstract class SparkingTpTipMixin {

    @ModifyArg(method = "tpMultiplier", require = 0, index = 2, at = @At(value = "INVOKE",
            target = "Lcom/yami/dmzsparking/client/menu/frame/StatsTexts$Tip;<init>(Lnet/minecraft/network/chat/Component;Ljava/util/List;Ljava/util/List;I)V"))
    private static List<Component> dmzzero$potentialLine(List<Component> parts) {
        int potential = ClientPotential.get();
        boolean chamber = ClientChamber.tpMultiplier() > 1.0;
        if (potential == 0 && !chamber) return parts;
        List<Component> lines = parts == null ? new ArrayList<>() : new ArrayList<>(parts);
        if (potential != 0) lines.add(PotentialService.tooltipLine(potential));
        if (chamber) lines.add(ClientChamber.tooltipLine());
        return lines;
    }
}
