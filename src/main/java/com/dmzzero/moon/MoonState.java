package com.dmzzero.moon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;

// whether a level's moon is gone, on either side: the server reads the saved state, the client what the server last sent.
// common code, so dmz's moon check (run on both sides) can ask it without loading a client class
public final class MoonState {
    private static volatile boolean clientGone;

    private MoonState() {
    }

    public static boolean gone(Level level) {
        if (level == null) return false;
        if (level.isClientSide()) return clientGone;
        return level instanceof ServerLevel server && hasMoon(server) && MoonSave.get(server).gone();
    }

    // a sky with vanilla's moon in it: the overworld's sky, with day and night (dmz's own dimensions have fixed time)
    public static boolean hasMoon(Level level) {
        DimensionType type = level.dimensionType();
        return type.hasSkyLight() && !type.hasFixedTime() && BuiltinDimensionTypes.OVERWORLD_EFFECTS.equals(type.effectsLocation());
    }

    public static void setClient(boolean gone) {
        clientGone = gone;
    }

    public static boolean clientGone() {
        return clientGone;
    }
}
