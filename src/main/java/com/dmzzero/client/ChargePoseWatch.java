package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationProcessor;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

// a player reported dmz's charge pose dropping to standing a second into charging, which neither dmz alone nor the dev
// game reproduces; when your own character is charging but dmz's player controller shows anything but a charge pose
// for half a second, this writes one line with everything dmz's animation code decides on to latest.log. the same goes
// for a technique: once its held "_cast" pose has shown, losing it before release is written down too, with the charge
// (past 100% is overcharging) and the last animation packet dmz sent about you
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class ChargePoseWatch {
    private static final int GRACE = 10;
    private static final long QUIET_MS = 60_000;
    private static final Map<String, Field> FIELDS = new HashMap<>();

    private static int chargingTicks;
    private static int wrongTicks;
    private static boolean reported;
    private static long lastReport;
    private static int reports;
    private static int castTicks;
    private static int castWrong;
    private static String castAnim;
    private static boolean castReported;
    private static int techniqueReports;
    private static volatile String lastPacket = "none";
    private static volatile long lastPacketAt;

    private ChargePoseWatch() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer p = mc.player;
        if (p == null || mc.isPaused() || !on()) return;
        StatsData d = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
        technique(mc, p, d);
        // dmz draws the body in first person too (DMZPOVPlayerRenderer), so every camera counts; the line says which
        boolean watching = d != null && d.getStatus().isChargingKi();
        if (!watching) {
            chargingTicks = wrongTicks = 0;
            reported = false;
            return;
        }
        chargingTicks++;
        String anim = playing(p, "controller");
        if (anim.startsWith("base.ki_")) {
            wrongTicks = 0;
            return;
        }
        wrongTicks++;
        long now = System.currentTimeMillis();
        if (chargingTicks > GRACE && wrongTicks >= GRACE && !reported && now - lastReport > QUIET_MS) {
            reported = true;
            lastReport = now;
            reports++;
            DMZZero.LOG.warn("[DMZ Zero] charge pose lost after {} ticks of charging: {}", chargingTicks, describe(mc, p, d, anim));
        }
    }

    private static boolean on() {
        try {
            return ZeroConfig.CLIENT.chargePoseLog.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    static int reports() {
        return reports;
    }

    static int techniqueReports() {
        return techniqueReports;
    }

    // ClientPacketHandlerAnimationMixin: what dmz last told this client to play or stop on its own player
    public static void packet(java.util.UUID player, String type, int arg, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getUUID().equals(player)) return;
        lastPacket = type + (name == null || name.isEmpty() ? "" : " " + name) + " (" + arg + ")";
        lastPacketAt = System.currentTimeMillis();
    }

    static String lastPacket() {
        return lastPacket;
    }

    private static void technique(Minecraft mc, AbstractClientPlayer p, StatsData d) {
        var t = d == null ? null : d.getTechniques();
        if (t == null || !(t.isTechniqueCharging() || t.isTechniqueChargeActive())) {
            castTicks = castWrong = 0;
            castAnim = null;
            castReported = false;
            return;
        }
        castTicks++;
        Object held = field(p, "currentKiAnim");
        String kiAnim = held instanceof String s ? s : null;
        // a laser has no cast pose, so only a charge that showed one can lose it
        if (kiAnim != null && kiAnim.endsWith("_cast")) {
            castAnim = kiAnim;
            if (controllers(p).contains(":" + kiAnim)) {
                castWrong = 0;
                return;
            }
        }
        if (castAnim == null) return;
        castWrong++;
        long now = System.currentTimeMillis();
        if (castWrong >= GRACE && !castReported && now - lastReport > QUIET_MS) {
            castReported = true;
            lastReport = now;
            techniqueReports++;
            DMZZero.LOG.warn("[DMZ Zero] technique pose lost after {} ticks of charging {} at {}% (holding {}): was {}, last dmz animation packet {} {} ms ago: {}",
                    castTicks, t.getChargingTechniqueId(), Math.round(t.getTechniqueChargePercent()), t.isChargeHolding(), castAnim,
                    lastPacket, lastPacketAt == 0 ? "-" : now - lastPacketAt, describe(mc, p, d, playing(p, "controller")));
        }
    }

    // the dev autotest's forced loss mustn't wait out the once-a-minute limit
    static void forgetLastReport() {
        lastReport = 0;
    }

    static String describe(Minecraft mc, AbstractClientPlayer p, StatsData d, String anim) {
        StringJoiner s = new StringJoiner(" ");
        s.add("race=" + d.getCharacter().getRaceName()).add("form=" + d.getCharacter().getActiveForm());
        s.add("anim=" + anim).add("controllers=" + controllers(p));
        s.add("armed=" + d.getStatus().isKiBurstArmed()).add("surge=" + d.getStatus().isSurgeActive());
        s.add("action=" + d.getStatus().isActionCharging()).add("blocking=" + d.getStatus().isBlocking());
        s.add("down=" + d.getStatus().isKnockedDown()).add("stun=" + d.getStatus().isStunEffect());
        s.add("descending=" + d.getStatus().isDescending()).add("ritual=" + d.getStatus().isInGodRitual());
        s.add("draining=" + d.getCooldowns().hasCooldown("DrainActive")).add("fly=" + d.getSkills().isSkillActive("fly"));
        s.add("moving=" + field(p, "isMovingState")).add("speed=" + field(p, "horizSpeed"));
        s.add("dash=" + field(p, "dashAnimTicks")).add("kiAnim=" + field(p, "currentKiAnim")).add("hold=" + field(p, "kiAnimHold"));
        s.add("ground=" + p.onGround()).add("riding=" + p.isPassenger()).add("swimming=" + p.isSwimming());
        s.add("camera=" + mc.options.getCameraType());
        return s.toString();
    }

    // every controller on the player and what it plays, in the order geckolib applies them
    private static String controllers(AbstractClientPlayer p) {
        if (!(p instanceof GeoAnimatable geo)) return "not geo";
        try {
            AnimatableManager<GeoAnimatable> manager = geo.getAnimatableInstanceCache().getManagerForId(p.getId());
            StringJoiner s = new StringJoiner(",", "[", "]");
            manager.getAnimationControllers().forEach((name, c) -> {
                AnimationProcessor.QueuedAnimation q = c.getCurrentAnimation();
                if (q != null && c.getAnimationState() != AnimationController.State.STOPPED) s.add(name + ":" + q.animation().name());
            });
            return s.toString();
        } catch (Exception e) {
            return "err " + e.getClass().getSimpleName();
        }
    }

    static String playing(AbstractClientPlayer p, String controller) {
        if (!(p instanceof GeoAnimatable geo)) return "not geo";
        try {
            AnimatableManager<GeoAnimatable> manager = geo.getAnimatableInstanceCache().getManagerForId(p.getId());
            AnimationController<?> c = manager.getAnimationControllers().get(controller);
            if (c == null) return "none";
            AnimationProcessor.QueuedAnimation q = c.getCurrentAnimation();
            // a stopped controller keeps its last animation, but no longer plays it
            if (q == null || c.getAnimationState() == AnimationController.State.STOPPED) return "stopped" + (q == null ? "" : ":" + q.animation().name());
            return q.animation().name();
        } catch (Exception e) {
            return "err " + e.getClass().getSimpleName();
        }
    }

    // dmz keeps its animation state in mixin fields merged into the player class
    private static Object field(AbstractClientPlayer p, String suffix) {
        Field f = FIELDS.computeIfAbsent(suffix, k -> {
            for (Class<?> c = p.getClass(); c != null; c = c.getSuperclass()) {
                for (Field each : c.getDeclaredFields()) {
                    if (each.getName().endsWith(k)) {
                        each.setAccessible(true);
                        return each;
                    }
                }
            }
            return null;
        });
        if (f == null) return "?";
        try {
            return f.get(p);
        } catch (Exception e) {
            return "err";
        }
    }
}
