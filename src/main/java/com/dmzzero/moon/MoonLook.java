package com.dmzzero.moon;

import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath.V;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// what dmz zero adds to dmz's oozaru moon check. both sides ask it, so a client never starts a charge the server refuses;
// without dmz zero on the server the fallbacks leave dmz's own rule
public final class MoonLook {
    private MoonLook() {
    }

    /** Tonight's moon counts though it isn't full: any phase that shows, with [world.moon] oozaruAnyMoon on. */
    public static boolean anyMoon(Level level) {
        if (level == null || !MoonState.hasMoon(level)) return false;
        return ZeroConfig.read(ZeroConfig.SERVER.moonOozaruAnyMoon, false) && MoonMath.shows(level.getMoonPhase());
    }

    /** Looking at the moon where it is in the sky, under open sky like dmz's own check. */
    public static boolean lookingAt(Player player) {
        Level level = player.level();
        if (!MoonState.hasMoon(level) || !level.canSeeSky(player.blockPosition())) return false;
        double degrees = ZeroConfig.read(ZeroConfig.SERVER.moonOozaruLookDegrees, 0.0);
        Vec3 look = player.getLookAngle();
        return MoonMath.near(new V(look.x, look.y, look.z), level.getTimeOfDay(1f), degrees);
    }
}
