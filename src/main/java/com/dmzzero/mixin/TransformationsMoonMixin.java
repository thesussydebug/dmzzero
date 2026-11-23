package com.dmzzero.mixin;

import com.dmzzero.moon.MoonLook;
import com.dmzzero.moon.MoonMath;
import com.dmzzero.moon.MoonState;
import com.dragonminez.common.util.TransformationsHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// dmz's oozaru needs a full moon you look straight up at, or a fake moon. dmz zero also takes any moon that shows and
// looking at it where it is; with the moon destroyed only the fake moon is left.
// runs on both sides: the server's check lets the charge happen, the client's starts it
@Mixin(value = TransformationsHelper.class, remap = false)
public abstract class TransformationsMoonMixin {

    @Inject(method = "isLookingAtMoon", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dmzzero$lookingAtMoon(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player == null) return;
        if (MoonState.gone(player.level())) cir.setReturnValue(false);
        else if (MoonLook.lookingAt(player)) cir.setReturnValue(true);
    }

    @Inject(method = "isFullMoon", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dmzzero$anyMoon(Level level, CallbackInfoReturnable<Boolean> cir) {
        if (MoonLook.anyMoon(level)) cir.setReturnValue(true);
    }

    // a client works out vanilla's night only when its level loads, so the full moon never started a charge after joining by day
    @Redirect(method = "shouldAutoChargeOozaru", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isNight()Z", remap = true))
    private static boolean dmzzero$night(Level level) {
        if (!level.isClientSide()) return level.isNight();
        return !level.dimensionType().hasFixedTime()
                && MoonMath.night(level.getTimeOfDay(1f), level.getRainLevel(1f), level.getThunderLevel(1f));
    }
}
