package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.impact.ImpactLookup;
import com.dmzzero.impact.ImpactMath;
import com.dmzzero.impact.ImpactProfile;
import com.dmzzero.network.ImpactPulseS2C;
import com.dragonminez.client.systems.FormVisualTransition;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.FormTransition;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.dmzzero.impact.ImpactProfile.*;

@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class ImpactClient {
    private static final double RANGE = 48.0;
    private static final int DARK_TINT = 0x140818;
    private static final Map<Integer, Watch> WATCHED = new HashMap<>();

    // set by FormFlickerMixin right before dmz blends a player's colors
    public static StatsData colorStats;

    private static final double STORM_RANGE = 96.0;
    private static final int STORM_HOLD_TICKS = 200;

    private static float shake;
    private static float impulse;
    private static float flash;
    private static float glow;
    private static float fovKick;
    private static int flashColor = 0xFFFFFF;
    private static int glowColor = 0xFFFFFF;
    private static int bursts;
    private static int pulses;
    // storm over the sky, only on this screen
    private static float storm;
    private static float stormTarget;
    private static float thunder;
    private static int stormTier;
    private static int targetTier;
    private static int stormHold;
    private static float heldStorm;
    private static int heldTier;

    private ImpactClient() {
    }

    private static final class Watch {
        String group, form, stackGroup, stack;
        boolean seen;
        int auraTicks;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        if (mc.isPaused()) return;
        impulse *= 0.86f;
        flash *= 0.82f;
        fovKick *= 0.88f;
        shake = 0f;
        glow = 0f;
        stormTarget = 0f;
        targetTier = 0;
        if (stormHold > 0) {
            stormHold--;
            stormTarget = heldStorm * Math.min(1f, stormHold / 60f);
            targetTier = heldTier;
        }
        Set<Integer> present = new HashSet<>();
        for (Player player : mc.level.players()) {
            double dist = player.distanceTo(mc.player);
            if (dist > STORM_RANGE) continue;
            StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            if (stats == null || !stats.getStatus().isHasCreatedCharacter()) continue;
            present.add(player.getId());
            observe(stats, WATCHED.computeIfAbsent(player.getId(), id -> new Watch()), dist);
        }
        WATCHED.keySet().retainAll(present);
        if (!ZeroConfig.CLIENT.sky.get()) stormTarget = 0f;
        if (targetTier > 0) stormTier = targetTier;
        storm = ImpactMath.easeStorm(storm, stormTarget);
        thunder = ImpactMath.thunder(storm, stormTier);
    }

    // a storm only gathers over forms that are wild, a mastered one keeps the sky clear
    private static float gather(ImpactProfile p, float factor, double dist) {
        if (!p.destructive()) return 0f;
        float reach = dist <= STORM_RANGE * 0.5 ? 1f : (float) Math.max(0, 2 - 2 * dist / STORM_RANGE);
        float target = ImpactMath.stormTarget(p.tier(), factor) * reach;
        if (target > stormTarget) {
            stormTarget = target;
            targetTier = p.tier();
        }
        return target;
    }

    private static void observe(StatsData stats, Watch w, double dist) {
        Character c = stats.getCharacter();
        String race = c.getRaceName();
        String group = c.getActiveFormGroup(), form = c.getActiveForm();
        String stackGroup = c.getActiveStackFormGroup(), stack = c.getActiveStackForm();
        if (w.seen) {
            if (!Objects.equals(form, w.form) || !Objects.equals(group, w.group)) reached(stats, w.group, w.form, group, form, false, dist);
            if (!Objects.equals(stack, w.stack) || !Objects.equals(stackGroup, w.stackGroup)) reached(stats, w.stackGroup, w.stack, stackGroup, stack, true, dist);
        }
        w.group = group;
        w.form = form;
        w.stackGroup = stackGroup;
        w.stack = stack;
        w.seen = true;

        FormTransition.Charge charge = FormVisualTransition.charge(stats);
        if (charge != null) {
            w.auraTicks = 0;
            ImpactProfile p = ImpactLookup.forPlayer(stats, charge.group(), charge.target(), charge.stack());
            if (p == null) return;
            float factor = charge.factor();
            gather(p, factor, dist);
            if (p.has(QUAKE)) shake = Math.max(shake, ImpactMath.quakeAmplitude(p.tier()) * factor * ImpactMath.falloff(dist, 32));
            if (p.has(LIGHT) && factor >= 0.8f) {
                float g = 0.14f * (factor - 0.8f) / 0.2f * ImpactMath.falloff(dist, 24);
                if (g > glow) {
                    glow = g;
                    glowColor = lightColor(charge.target());
                }
            }
        } else if (stats.getStatus().isChargingKi() && !stats.getStatus().isDescending()) {
            w.auraTicks++;
            ImpactProfile p = ImpactLookup.current(stats);
            gather(p, 0.6f * ImpactMath.auraIntensity(w.auraTicks), dist);
            if (p.has(QUAKE) && p.tier() >= 3) {
                shake = Math.max(shake, ImpactMath.quakeAmplitude(p.tier()) * 0.5f
                        * ImpactMath.auraIntensity(w.auraTicks) * ImpactMath.falloff(dist, 32));
            }
        } else {
            w.auraTicks = 0;
        }
    }

    private static void reached(StatsData stats, String oldGroup, String oldForm, String newGroup, String newForm, boolean stack, double dist) {
        String race = stats.getCharacter().getRaceName();
        if (!ImpactLookup.isAscent(race, oldGroup, oldForm, newGroup, newForm, stack)) return;
        ImpactProfile p = ImpactLookup.forPlayer(stats, newGroup, newForm, stack);
        if (p == null) return;
        bursts++;
        int tier = p.tier();
        float held = gather(p, 1f, dist);
        if (held > 0f && held >= heldStorm * Math.min(1f, stormHold / 60f)) {
            heldStorm = held;
            heldTier = tier;
            stormHold = STORM_HOLD_TICKS;
        }
        if (dist > RANGE) return;
        if (p.has(QUAKE)) impulse = Math.max(impulse, (1.0f + 0.5f * tier) * ImpactMath.falloff(dist, 40));
        else if (p.has(SHOCKWAVE)) impulse = Math.max(impulse, (0.3f + 0.15f * tier) * ImpactMath.falloff(dist, 32));
        if (p.has(LIGHT)) {
            float f = 0.55f * ImpactMath.falloff(dist, 32);
            if (f > flash) {
                flash = f;
                flashColor = lightColor(ImpactLookup.formData(race, newGroup, newForm, stack));
            }
        } else if (p.has(DARK)) {
            float f = 0.35f * ImpactMath.falloff(dist, 24);
            if (f > flash) {
                flash = f;
                flashColor = DARK_TINT;
            }
        }
    }

    // the form's aura color washed toward white
    private static int lightColor(FormConfig.FormData form) {
        float[] rgb = form != null ? form.getRgbAuraColor() : null;
        if (rgb == null || rgb.length < 3) return 0xFFFFFF;
        int r = (int) (255 * (0.4f * rgb[0] + 0.6f));
        int g = (int) (255 * (0.4f * rgb[1] + 0.6f));
        int b = (int) (255 * (0.4f * rgb[2] + 0.6f));
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    // a jolt the server felt somewhere nearby: a clash, or a first transformation
    public static void pulse(ImpactPulseS2C msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        pulses++;
        float f = ImpactMath.falloff(Math.sqrt(mc.player.distanceToSqr(msg.x(), msg.y(), msg.z())), msg.range());
        if (f <= 0f) return;
        impulse = Math.max(impulse, msg.shake() * f);
        fovKick = Math.max(fovKick, msg.fovKick() * f);
        if (msg.flash() * f > flash) {
            flash = msg.flash() * f;
            flashColor = msg.flashColor();
        }
    }

    // a quick zoom in that eases back out
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (fovKick < 0.005f || !ZeroConfig.CLIENT.shake.get()) return;
        event.setFOV(event.getFOV() * (1.0 - Math.min(0.3f, fovKick)));
    }

    // read by the weather mixin; the world's real weather is never touched
    public static float storm() {
        return storm;
    }

    public static float thunder() {
        return thunder;
    }

    public static int pulseCount() {
        return pulses;
    }

    public static float fovKickNow() {
        return fovKick;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!ZeroConfig.CLIENT.shake.get()) return;
        float amount = (float) (Math.max(shake, impulse) * ZeroConfig.CLIENT.shakeStrength.get());
        if (amount < 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        double t = (mc.player != null ? mc.player.tickCount : 0) + event.getPartialTick();
        event.setPitch(event.getPitch() + amount * (float) (Math.sin(t * 1.9) * 0.6 + Math.sin(t * 4.3 + 1.3) * 0.4));
        event.setYaw(event.getYaw() + amount * (float) (Math.sin(t * 2.3 + 0.7) * 0.6 + Math.sin(t * 5.1 + 2.1) * 0.4));
        event.setRoll(event.getRoll() + amount * 0.5f * (float) Math.sin(t * 3.7 + 0.4));
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ZeroConfig.CLIENT.flashes.get()) return;
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.player != null ? mc.player.tickCount : 0) + event.getPartialTick();
        float pulse = glow * (0.6f + 0.4f * (float) Math.sin(t * 0.9f));
        float alpha = Math.max(flash, pulse);
        if (alpha < 0.01f) return;
        int color = flash >= pulse ? flashColor : glowColor;
        int a = (int) (Math.min(0.75f, alpha) * 255);
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        event.getGuiGraphics().fill(0, 0, w, h, (a << 24) | (color & 0xFFFFFF));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        ImpactLookup.clearCache();
    }

    private static void reset() {
        WATCHED.clear();
        shake = impulse = flash = glow = fovKick = 0f;
        storm = stormTarget = thunder = heldStorm = 0f;
        stormHold = stormTier = 0;
        colorStats = null;
    }

    public static float flicker(FormTransition.Charge charge, StatsData stats) {
        float factor = charge.factor();
        if (stats == null || !ZeroConfig.CLIENT.hairFlicker.get()) return factor;
        ImpactProfile p = ImpactLookup.forPlayer(stats, charge.group(), charge.target(), charge.stack());
        if (p == null || !p.has(FLICKER)) return factor;
        Minecraft mc = Minecraft.getInstance();
        double time = mc.level != null ? mc.level.getGameTime() + mc.getFrameTime() : 0;
        // offset per player so two people powering up don't flash in step
        if (stats.getPlayer() != null) time += stats.getPlayer().getId() * 7.3;
        return ImpactMath.flicker(factor, time);
    }

    public static float shakeNow() {
        return Math.max(shake, impulse);
    }

    public static float flashNow() {
        return Math.max(flash, glow);
    }

    public static int burstCount() {
        return bursts;
    }
}
