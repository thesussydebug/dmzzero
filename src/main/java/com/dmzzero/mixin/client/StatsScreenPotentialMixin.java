package com.dmzzero.mixin.client;

import com.dmzzero.chamber.ClientChamber;
import com.dmzzero.potential.ClientPotential;
import com.dmzzero.potential.Potential;
import com.dmzzero.potential.PotentialService;
import com.dragonminez.client.gui.character.CharacterStatsScreen;
import com.dragonminez.common.config.TpSource;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = CharacterStatsScreen.class, remap = false)
public abstract class StatsScreenPotentialMixin {

    @Redirect(method = "renderTpMultiplierInfo", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/common/stats/StatsData;getTpSourceMultiplier(Lcom/dragonminez/common/config/TpSource;)D"))
    private double dmzzero$totalWithPotential(StatsData data, TpSource source) {
        return data.getTpSourceMultiplier(source) * Potential.tpMultiplier(ClientPotential.get()) * ClientChamber.tpMultiplier();
    }

    @ModifyArg(method = "renderTpMultiplierInfo", require = 0, index = 8, at = @At(value = "INVOKE",
            target = "Lcom/dragonminez/client/util/TextUtil;renderAdvancedTooltip(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIILnet/minecraft/network/chat/Component;Ljava/util/List;Ljava/util/List;I)V"))
    private List<Component> dmzzero$potentialLine(List<Component> extras) {
        int potential = ClientPotential.get();
        boolean chamber = ClientChamber.tpMultiplier() > 1.0;
        if (potential == 0 && !chamber) return extras;
        List<Component> lines = extras == null ? new ArrayList<>() : new ArrayList<>(extras);
        if (potential != 0) lines.add(PotentialService.tooltipLine(potential));
        if (chamber) lines.add(ClientChamber.tooltipLine());
        return lines;
    }
}
