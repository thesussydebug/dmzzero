package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.moon.MoonMath;
import com.dmzzero.moon.MoonState;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// the moon in this client's sky: there, blowing up, or gone. vanilla's sky draws it at the size this hands back
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class MoonClient {
    private static int blastAge = -1;
    private static float largest;
    private static int blasts;

    private MoonClient() {
    }

    public static void received(boolean gone, boolean blast) {
        MoonState.setClient(gone);
        blastAge = gone && blast ? 0 : -1;
        if (blastAge == 0) blasts++;
    }

    public static float moonSize(float vanilla) {
        float size = MoonMath.size(vanilla, MoonState.clientGone(), blastAge, Minecraft.getInstance().getFrameTime());
        if (size > largest) largest = size;
        return size;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || blastAge < 0) return;
        if (Minecraft.getInstance().isPaused()) return;
        if (++blastAge >= MoonMath.BLAST_TICKS) blastAge = -1;
    }

    // a new level is a new sky; the server says right after whether its moon is gone
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) reset();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        MoonState.setClient(false);
        blastAge = -1;
    }

    // read by the dev-game autotest
    public static boolean blasting() {
        return blastAge >= 0;
    }

    public static int blasts() {
        return blasts;
    }

    public static float largestDrawn() {
        return largest;
    }

    public static void forgetLargest() {
        largest = 0f;
    }
}
