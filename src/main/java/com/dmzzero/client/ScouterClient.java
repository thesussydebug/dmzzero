package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.network.ScouterPingC2S;
import com.dmzzero.network.ScouterPingS2C;
import com.dmzzero.network.ZeroNetwork;
import com.dmzzero.scouter.ScouterMath;
import com.dmzzero.scouter.Scouters;
import com.dmzzero.sense.PowerSense;
import com.dragonminez.common.util.CuriosUtil;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

// the scouter ping key (registered in ZeroKeys), and the marks pings and power spikes leave on screen
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class ScouterClient {
    public static final KeyMapping PING = new KeyMapping("key.dmzzero.scouter_ping", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.dmzzero");
    private static final double PICK_RANGE = 128;
    private static final double AIM_TOLERANCE = 0.6;
    private static final int PING_LIFE = 200;
    private static final int SPIKE_LIFE = 120;
    private static final int MAX_MARKS = 12;
    private static final float OFF_SCREEN = 50f;
    private static final double LABEL_SCALE = 0.032;
    private static final List<Mark> MARKS = new ArrayList<>();
    private static int sent;
    private static int lastSentId = -2;
    private static int received;
    private static int drawn;
    private static String lastText = "";

    private ScouterClient() {
    }

    private static final class Mark {
        final ScouterPingS2C msg;
        final int life;
        // marks belong to the world they were made in
        final ResourceKey<Level> world;
        int age;

        Mark(ScouterPingS2C msg, ResourceKey<Level> world) {
            this.msg = msg;
            this.life = msg.kind() == ScouterPingS2C.SPIKE ? SPIKE_LIFE : PING_LIFE;
            this.world = world;
        }

        Vec3 position(Minecraft mc, float partial) {
            if (msg.entityId() >= 0 && mc.level != null) {
                Entity e = mc.level.getEntity(msg.entityId());
                if (e != null) return e.getPosition(partial).add(0, e.getBbHeight() + 0.4, 0);
            }
            return new Vec3(msg.x(), msg.y() + 0.4, msg.z());
        }
    }

    public static void received(ScouterPingS2C msg) {
        received++;
        MARKS.removeIf(m -> m.msg.kind() == msg.kind() && (msg.entityId() >= 0 ? m.msg.entityId() == msg.entityId()
                : Math.abs(m.msg.x() - msg.x()) < 1.5 && Math.abs(m.msg.z() - msg.z()) < 1.5));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MARKS.add(new Mark(msg, mc.level.dimension()));
        while (MARKS.size() > MAX_MARKS) MARKS.remove(0);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            MARKS.clear();
            return;
        }
        while (PING.consumeClick()) ping(mc);
        if (mc.level != null) MARKS.removeIf(m -> !m.world.equals(mc.level.dimension()));
        if (mc.isPaused()) return;
        for (Iterator<Mark> it = MARKS.iterator(); it.hasNext(); ) {
            Mark m = it.next();
            if (++m.age > m.life) it.remove();
        }
    }

    static boolean ping(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || !ZeroNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection())) return false;
        if (CuriosUtil.getFirstStackForItem(player, "head_tech", "scouter").isEmpty()) {
            player.displayClientMessage(Component.translatableWithFallback("message.dmzzero.scouter.no_scouter", "Put a scouter on first.")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        Entity camera = mc.getCameraEntity() != null ? mc.getCameraEntity() : player;
        Vec3 eye = camera.getEyePosition(1f);
        Vec3 end = eye.add(camera.getViewVector(1f).scale(PICK_RANGE));
        HitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, camera));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        LivingEntity target = pickEntity(mc, camera, eye, end);
        if (target != null) {
            send(target.getId(), target.position());
            return true;
        }
        if (block.getType() == HitResult.Type.MISS) return false;
        send(-1, block.getLocation());
        return true;
    }

    private static LivingEntity pickEntity(Minecraft mc, Entity camera, Vec3 eye, Vec3 end) {
        AABB area = new AABB(eye, end).inflate(AIM_TOLERANCE + 1.0);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity living : mc.level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != camera && e != mc.player && e.isAlive() && !e.isSpectator())) {
            AABB bounds = living.getBoundingBox().inflate(Math.max(AIM_TOLERANCE, living.getPickRadius()));
            Optional<Vec3> crossing = bounds.contains(eye) ? Optional.of(eye) : bounds.clip(eye, end);
            if (crossing.isEmpty()) continue;
            double d = eye.distanceToSqr(crossing.get());
            if (d < bestDist) {
                bestDist = d;
                best = living;
            }
        }
        return best;
    }

    static void send(int entityId, Vec3 at) {
        sent++;
        lastSentId = entityId;
        ZeroNetwork.CHANNEL.sendToServer(new ScouterPingC2S(entityId, at.x, at.y, at.z));
    }

    static String text(ScouterPingS2C msg, double dist, String self) {
        StringBuilder out = new StringBuilder();
        if (msg.kind() == ScouterPingS2C.SPIKE) {
            out.append("⚠ ").append(msg.label()).append(' ').append(ScouterMath.reading(msg.from())).append(" → ").append(ScouterMath.reading(msg.bp()));
        } else {
            out.append("◆ ").append(msg.label());
            if (msg.bp() >= 0) out.append(" · ").append(ScouterMath.reading(msg.bp()));
            else if (msg.bp() == Scouters.HIDDEN) out.append(" · ??");
        }
        out.append(" · ").append(Math.round(dist)).append(" m");
        if (msg.kind() == ScouterPingS2C.PING && !msg.sender().isEmpty() && !msg.sender().equals(self)) out.append(" · ").append(msg.sender());
        return out.toString();
    }

    private static int color(ScouterPingS2C msg) {
        if (msg.kind() == ScouterPingS2C.SPIKE) return 0xFF5555;
        ScouterMath.Channel[] channels = ScouterMath.Channel.values();
        return msg.channel() >= 0 && msg.channel() < channels.length ? channels[msg.channel()].color : 0xFFFFFF;
    }

    private static float fade(Mark m, float partial) {
        float t = m.age + partial;
        return Math.max(0f, Math.min(1f, t / 4f) * Math.min(1f, (m.life - t) / 30f));
    }

    // labels over the target, drawn through walls like a nametag
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || MARKS.isEmpty() || !ZeroConfig.CLIENT.pingLabels.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Camera cam = event.getCamera();
        Vec3 cp = cam.getPosition();
        float partial = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        Font font = mc.font;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        String self = mc.player.getGameProfile().getName();
        for (Mark m : MARKS) {
            int alpha = (int) (255 * fade(m, partial));
            if (alpha < 8) continue;
            Vec3 at = m.position(mc, partial);
            double dist = at.distanceTo(cp);
            String text = text(m.msg, mc.player.position().distanceTo(at), self);
            lastText = text;
            drawn++;
            float scale = (float) (LABEL_SCALE * Mth.clamp(dist / 6.0, 1.0, 20.0));
            ps.pushPose();
            ps.translate(at.x - cp.x, at.y - cp.y, at.z - cp.z);
            ps.mulPose(cam.rotation());
            ps.scale(-scale, -scale, scale);
            Matrix4f matrix = ps.last().pose();
            float x = -font.width(text) / 2f;
            // a spike sits a line above a ping on the same target
            float y = m.msg.kind() == ScouterPingS2C.SPIKE ? -font.lineHeight - 2 : 0;
            int argb = (alpha << 24) | (color(m.msg) & 0xFFFFFF);
            font.drawInBatch(text, x, y, argb, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, (alpha / 2) << 24, 0xF000F0);
            ps.popPose();
        }
        buffers.endBatch();
    }

    // an arrow at the edge of the screen for marks behind or beside you
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (MARKS.isEmpty() || !ZeroConfig.CLIENT.pingLabels.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        float partial = event.getPartialTick();
        for (Mark m : MARKS) {
            Vec3 at = m.position(mc, partial);
            float angle = PowerSense.screenAngle(mc.player.getViewYRot(partial), at.x - mc.player.getX(), at.z - mc.player.getZ());
            if (Math.abs(angle) < OFF_SCREEN) continue;
            int alpha = (int) (255 * fade(m, partial));
            if (alpha < 8) continue;
            double rad = Math.toRadians(angle);
            float cx = (float) (w / 2.0 + Math.sin(rad) * (w / 2.0 - 22));
            float cy = (float) (h / 2.0 - Math.cos(rad) * (h / 2.0 - 22));
            SenseClient.arrow(g, cx, cy, angle, 5, (alpha << 24) | (color(m.msg) & 0xFFFFFF));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MARKS.clear();
    }

    public static int sent() {
        return sent;
    }

    public static int lastSentId() {
        return lastSentId;
    }

    public static int received() {
        return received;
    }

    public static int active() {
        return MARKS.size();
    }

    public static int drawn() {
        return drawn;
    }

    public static String lastText() {
        return lastText;
    }
}
