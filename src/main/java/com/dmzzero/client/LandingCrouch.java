package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.flight.TouchdownMath;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// a hard landing drops you into a crouch for a moment. holding sneak sends it to the server, so everyone sees it.
// worked out on your own client, which knows exactly when your feet hit the ground
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class LandingCrouch {
    private static TouchdownMath.Feet feet = new TouchdownMath.Feet();
    private static long ticks;
    private static int crouch;
    private static int landed;

    private LandingCrouch() {
    }

    private static boolean enabled() {
        return ZeroConfig.CLIENT.landingCrouch.get() && ZeroConfig.read(ZeroConfig.SERVER.landings, true);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !enabled()) {
            crouch = 0;
            return;
        }
        ticks++;
        StatsData d = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
        boolean fly = d != null && d.getSkills().isSkillActive("fly");
        if (!TouchdownMath.tracks(p.isSpectator(), p.isPassenger(), p.isInWater(), p.getAbilities().flying, fly)) {
            feet = new TouchdownMath.Feet();
            crouch = 0;
            return;
        }
        double landingSpeed = ZeroConfig.read(ZeroConfig.SERVER.landingSpeed, 0.9);
        TouchdownMath.Event e = feet.step(p.getX(), p.getY(), p.getZ(), p.onGround(), fly, ticks,
                ZeroConfig.read(ZeroConfig.SERVER.takeoffSpeed, 0.5), landingSpeed);
        if (e == TouchdownMath.Event.LANDING) {
            crouch = TouchdownMath.crouchTicks(TouchdownMath.strength(feet.speed(), landingSpeed));
            landed++;
        } else if (crouch > 0) {
            crouch--;
        }
    }

    // before dmz's own handler, which clears the input while charging or posing and should win then
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInput(MovementInputUpdateEvent event) {
        if (crouch > 0 && event.getEntity() == Minecraft.getInstance().player) event.getInput().shiftKeyDown = true;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        feet = new TouchdownMath.Feet();
        crouch = 0;
    }

    public static int landed() {
        return landed;
    }

    public static int crouching() {
        return crouch;
    }
}
