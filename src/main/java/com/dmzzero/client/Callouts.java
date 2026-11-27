package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.callout.CalloutMath;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// a technique's name shouted over the head of whoever fires it. the name comes with the projectile (TechniqueNames on the server
// puts it in the custom name); dmz's, sparking's and overhaul's own techniques fired without one fall back to their lang names
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class Callouts {
    private static final double RANGE = 64;
    private static final int MAX_SHOWN = 10;
    // dmz spawns a technique's projectile when charging starts and fires it on release, which is when it's shouted;
    // a charge held longer than this is forgotten
    private static final int PENDING_TICKS = 1200;
    private static final int MAX_PENDING = 64;
    private static final float SIZE = 0.032f;
    private static final List<Pending> PENDING = new ArrayList<>();
    // by the id of whoever shouted
    private static final Map<Integer, Callout> SHOWN = new LinkedHashMap<>();
    private static final List<String> RECENT = new ArrayList<>();
    private static int ticks;

    private Callouts() {
    }

    private static final class Pending {
        final AbstractKiProjectile proj;
        final int born;
        // the chant, worked out once the charge has run long enough to start one
        List<String> beats;
        int shown;

        Pending(AbstractKiProjectile proj, int born) {
            this.proj = proj;
            this.born = born;
        }
    }

    static final class Callout {
        final int owner;
        String text;
        int color, outline;
        int age;
        int lastShout;

        Callout(int owner) {
            this.owner = owner;
        }
    }

    private static boolean enabled() {
        try {
            return ZeroConfig.CLIENT.callouts.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof AbstractKiProjectile proj && enabled() && PENDING.size() < MAX_PENDING) {
            PENDING.add(new Pending(proj, ticks));
        }
    }

    // every dmz ki projectile marks itself firing when it's released, except lasers, which just start reaching out
    private static boolean fired(AbstractKiProjectile proj) {
        return proj.isFiring() || proj instanceof KiLaserEntity laser && laser.getBeamLength() > 0.5f;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            PENDING.clear();
            SHOWN.clear();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.isPaused()) return;
        ticks++;
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            // fired first: a shot that hits within the tick it fires is already removed when the client looks
            if (!fired(p.proj)) {
                if (p.proj.isRemoved() || ticks - p.born > PENDING_TICKS) {
                    it.remove();
                    // a charge dropped unfired: its chant shrinks away instead of hanging there
                    if (p.shown > 0) fade(mc, p);
                    continue;
                }
                chant(mc, p);
                continue;
            }
            String clean = CalloutMath.clean(nameOf(p.proj));
            it.remove();
            if (clean == null) continue;
            Entity owner = owner(mc, p.proj);
            if (owner == null) continue;
            List<String> beats = p.beats != null ? p.beats : CalloutMath.beats(clean, stackName(owner));
            String text = p.shown > 0 ? CalloutMath.finale(beats) : CalloutMath.whole(beats, clean);
            if (text != null) shout(mc, owner, text, p.proj.getColor());
        }
        Iterator<Callout> shown = SHOWN.values().iterator();
        while (shown.hasNext()) {
            Callout c = shown.next();
            c.age++;
            if (!CalloutMath.alive(c.age) || mc.level.getEntity(c.owner) == null) shown.remove();
        }
    }

    private static String nameOf(AbstractKiProjectile proj) {
        return readable(proj.hasCustomName() ? proj.getCustomName().getString() : null, proj.getTechniqueId());
    }

    // the words to shout for a name from the server. it can still be a lang key: from a server on 0.17.13 or older, which
    // sent dmz's keys as text and cut them short, or for a technique this client has no translation of
    private static String readable(String name, String techniqueId) {
        if (name == null || name.isEmpty()) return langName(techniqueId);
        if (!CalloutMath.isNameKey(name)) return name;
        if (I18n.exists(name)) return I18n.get(name);
        String byId = langName(techniqueId);
        return byId != null ? byId : CalloutMath.wordsOf(name);
    }

    // a charge held past chantTicks starts the chant, one more part every chantTicks; the last part waits for the release
    private static void chant(Minecraft mc, Pending p) {
        if (!ZeroConfig.CLIENT.chant.get()) return;
        int every = ZeroConfig.CLIENT.chantTicks.get();
        int held = ticks - p.born - every;
        if (held < 0) return;
        Entity owner = owner(mc, p.proj);
        if (owner == null) return;
        if (p.beats == null) p.beats = CalloutMath.beats(nameOf(p.proj), stackName(owner));
        int shown = CalloutMath.beatsShown(held, every, p.beats.size());
        if (shown <= 0) return;
        String text = CalloutMath.chant(p.beats, shown);
        if (shown != p.shown) {
            p.shown = shown;
            showChant(mc, owner, text, p.proj.getColor());
            return;
        }
        // held at full size while the charge goes on
        Callout c = SHOWN.get(owner.getId());
        if (c != null && text.equals(c.text) && c.age >= CalloutMath.POP) c.age = CalloutMath.POP;
    }

    private static void fade(Minecraft mc, Pending p) {
        Entity owner = owner(mc, p.proj);
        Callout c = owner == null ? null : SHOWN.get(owner.getId());
        if (c != null && c.text != null && c.text.endsWith("...")) c.age = Math.max(c.age, CalloutMath.LIFE - 8);
    }

    private static void showChant(Minecraft mc, Entity owner, String text, int rgb) {
        Callout c = SHOWN.get(owner.getId());
        boolean was = c != null && c.text != null && c.text.endsWith("...");
        shout(mc, owner, text, rgb);
        c = SHOWN.get(owner.getId());
        // each new part bumps the callout a little instead of popping it in again
        if (was && c != null && text.equals(c.text)) c.age = CalloutMath.POP - 2;
    }

    // "Kaioken x3" from dmz's own lang names for the stacked form, or null without one
    private static String stackName(Entity owner) {
        if (!ZeroConfig.CLIENT.stackForms.get() || !(owner instanceof Player player)) return null;
        StatsData d = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (d == null) return null;
        String group = d.getCharacter().getActiveStackFormGroup(), form = d.getCharacter().getActiveStackForm();
        if (group == null || group.isEmpty() || form == null || form.isEmpty()) return null;
        String groupKey = "race.dragonminez.stack.group." + group, formKey = "race.dragonminez.stack.form." + group + "." + form;
        if (!I18n.exists(groupKey) || !I18n.exists(formKey)) return null;
        return I18n.get(groupKey) + " " + I18n.get(formKey);
    }

    /** CalloutS2C: a strike attack landed. */
    public static void strike(int entityId, String name, String technique, int rgb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !enabled()) return;
        Entity owner = mc.level.getEntity(entityId);
        if (owner == null) return;
        String clean = CalloutMath.clean(readable(name, technique));
        if (clean != null) shout(mc, owner, CalloutMath.shout(clean), rgb);
    }

    /** Where a fighter's technique is gathering, for GatherEnergy: the charging ball, or null. */
    public static AbstractKiProjectile charging(Entity owner) {
        Minecraft mc = Minecraft.getInstance();
        for (Pending p : PENDING) {
            if (!p.proj.isRemoved() && !fired(p.proj) && owner(mc, p.proj) == owner) return p.proj;
        }
        return null;
    }

    private static void shout(Minecraft mc, Entity owner, String text, int rgb) {
        if (owner == mc.player && !ZeroConfig.CLIENT.ownCallouts.get()) return;
        if (owner.distanceToSqr(mc.player) > RANGE * RANGE) return;
        Callout c = SHOWN.get(owner.getId());
        if (c != null && CalloutMath.repeat(c.text, text, ticks - c.lastShout)) {
            // a callout still popping in finishes its pop; one already up just stays up
            if (c.age >= CalloutMath.POP) c.age = CalloutMath.REFRESHED_AGE;
            c.lastShout = ticks;
            return;
        }
        if (c == null) {
            if (SHOWN.size() >= MAX_SHOWN) {
                Iterator<Integer> oldest = SHOWN.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            c = new Callout(owner.getId());
            SHOWN.put(owner.getId(), c);
        }
        c.text = text;
        c.color = CalloutMath.textColor(rgb);
        c.outline = CalloutMath.outlineColor(rgb);
        c.age = 0;
        c.lastShout = ticks;
        RECENT.add(text);
        if (RECENT.size() > 20) RECENT.remove(0);
    }

    private static Entity owner(Minecraft mc, AbstractKiProjectile proj) {
        Entity owner = proj.getOwner();
        if (owner != null) return owner;
        UUID id = proj.getOwnerUUID();
        return id == null ? null : mc.level.getPlayerByUUID(id);
    }

    private static String langName(String techniqueId) {
        for (String key : CalloutMath.langKeys(techniqueId)) if (I18n.exists(key)) return I18n.get(key);
        return null;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHOWN.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        Font font = mc.font;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Callout c : SHOWN.values()) {
            Entity owner = mc.level.getEntity(c.owner);
            if (owner == null || owner.isInvisibleTo(mc.player)) continue;
            if (owner == camera.getEntity() && !camera.isDetached()) continue;
            float age = c.age + partial;
            float scale = Mth.lerp(partial, CalloutMath.scale(c.age), CalloutMath.scale(c.age + 1));
            if (scale <= 0.01f) continue;
            double x = Mth.lerp(partial, owner.xo, owner.getX());
            double y = Mth.lerp(partial, owner.yo, owner.getY()) + owner.getBbHeight() + 0.85 + CalloutMath.rise((int) age);
            double z = Mth.lerp(partial, owner.zo, owner.getZ());
            FormattedCharSequence seq = Component.literal(c.text).getVisualOrderText();
            pose.pushPose();
            pose.translate(x - cam.x, y - cam.y, z - cam.z);
            pose.mulPose(camera.rotation());
            float s = SIZE * scale;
            pose.scale(-s, -s, s);
            font.drawInBatch8xOutline(seq, -font.width(seq) / 2f, -font.lineHeight / 2f, c.color, c.outline, pose.last().pose(), buffers, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
    }

    // for the dev autotest
    public static List<String> recent() {
        return List.copyOf(RECENT);
    }

    public static String shownOver(Entity e) {
        Callout c = SHOWN.get(e.getId());
        return c == null ? null : c.text;
    }

    public static int shown() {
        return SHOWN.size();
    }
}
