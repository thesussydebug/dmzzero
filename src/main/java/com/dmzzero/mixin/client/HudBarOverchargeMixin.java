package com.dmzzero.mixin.client;

import com.dmzzero.charge.OverchargeMath;
import com.dmzzero.client.ClientOvercharge;
import com.dragonminez.client.gui.hud.HudBar;
import com.dragonminez.client.gui.hud.HudSmoother;
import com.dragonminez.client.gui.hud.HudStatNumberAnimator;
import com.dragonminez.common.config.ConfigManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// dmz's ki bar during ki boost's overcharge: the number reads past 100% while the bar itself stays full. the client's
// own ki is clamped to full, so the value above it comes from the server (ClientOvercharge)
@Mixin(value = HudBar.class, remap = false)
public abstract class HudBarOverchargeMixin {
    @Shadow
    private float max;
    @Shadow
    @Final
    private HudSmoother number;
    @Unique
    private boolean dmzzero$ki;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void dmzzero$kind(HudStatNumberAnimator.StatKind kind, CallbackInfo ci) {
        dmzzero$ki = kind == HudStatNumberAnimator.StatKind.KI;
    }

    // the number's smoother, the second one update() feeds; the bar's fill keeps dmz's clamped value
    @Redirect(method = "update", require = 0, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/dragonminez/client/gui/hud/HudSmoother;update(F)F"))
    private float dmzzero$number(HudSmoother smoother, float value) {
        float over = ClientOvercharge.energy();
        if (dmzzero$ki && over > 0f && OverchargeMath.heldAtFull(value, max, ClientOvercharge.max()) && ClientOvercharge.ownBar(this)) {
            value = over / ClientOvercharge.max() * max;
        }
        return smoother.update(value);
    }

    @ModifyVariable(method = "drawValue(Lnet/minecraft/client/gui/GuiGraphics;FFFFFZ)V", at = @At("STORE"), ordinal = 0, require = 0)
    private String dmzzero$percent(String text) {
        if (!dmzzero$ki || !Boolean.TRUE.equals(ConfigManager.getUserConfig().getAdvancedDescriptionPercentage())) return text;
        String over = OverchargeMath.percentText(number.value(), max);
        return over != null ? over : text;
    }
}
