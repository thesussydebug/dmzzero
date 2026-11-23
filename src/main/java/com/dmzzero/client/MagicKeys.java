package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.magic.GlindMagic;
import com.dmzzero.magic.Magic;
import com.dmzzero.magic.SpellMath;
import com.dmzzero.network.SpellC2S;
import com.dmzzero.network.ZeroNetwork;
import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.common.racial.RacialRegistry;
import com.dragonminez.common.racial.impl.GlindDivinity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.extras.ActionMode;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

// a glind's spells in racial mode: dmz's Action key casts and Second Function with it picks the next spell, sent the
// moment the key goes down, and a release when the casting key comes back up. dmz's own charge for that key would
// freeze your movement while it's held
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class MagicKeys {
    private static int sent;
    // the key that cast, until it goes up; rock lift keeps pulling blocks while it's down
    private static InputConstants.Key casting;
    // the autotest can't hold a real key down
    static Boolean heldForTest;

    private MagicKeys() {
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) pressed(InputConstants.getKey(event.getKey(), event.getScanCode()));
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Post event) {
        if (event.getAction() == GLFW.GLFW_PRESS) pressed(InputConstants.Type.MOUSE.getOrCreate(event.getButton()));
    }

    // dmz's client sends its action charge from the key's down state at the end of the tick, so let go of it first
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (casting != null && (mc.screen != null || !active(mc) || !down(mc, casting))) {
            casting = null;
            if (connected(mc)) send(SpellMath.RELEASE);
        }
        if (KeyBinds.ACTION_KEY.isDown() && active(mc)) KeyBinds.ACTION_KEY.setDown(false);
    }

    // the physical key, since the Action mapping is let go of every tick above
    private static boolean down(Minecraft mc, InputConstants.Key key) {
        if (heldForTest != null) return heldForTest;
        long window = mc.getWindow().getWindow();
        return switch (key.getType()) {
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            default -> false;
        };
    }

    /** A key or mouse button going down. True when it sent a spell packet. */
    static boolean pressed(InputConstants.Key key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !active(mc)) return false;
        int action = SpellMath.key(key.equals(KeyBinds.ACTION_KEY.getKey()), chord(key), KeyBinds.isSecondFunctionDown());
        if (action == SpellMath.NONE) return false;
        send(action);
        if (action == SpellMath.CAST) casting = key;
        return true;
    }

    // dmz's Second Function in racial mode is its descend chord, Alt and the Action key by default
    private static boolean chord(InputConstants.Key key) {
        KeyModifier modifier = KeyBinds.DESCEND.getKeyModifier();
        return key.equals(KeyBinds.DESCEND.getKey()) && (modifier == KeyModifier.NONE || KeyBinds.isBarModifierActive(modifier));
    }

    /** A Glind in Racial mode with magic on, on a server with DMZ Zero. */
    static boolean active(Minecraft mc) {
        if (mc.player == null || !connected(mc)) return false;
        if (!Magic.on()) return false;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, mc.player).orElse(null);
        return data != null && data.getStatus().getSelectedAction() == ActionMode.RACIAL && GlindDivinity.isActive(data)
                && RacialRegistry.forPlayer(data).filter(a -> a instanceof GlindMagic).isPresent();
    }

    private static boolean connected(Minecraft mc) {
        return mc.getConnection() != null && ZeroNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection());
    }

    static void send(int action) {
        ZeroNetwork.CHANNEL.sendToServer(new SpellC2S(action));
        sent++;
    }

    // for the dev autotest
    static int sent() {
        return sent;
    }
}
