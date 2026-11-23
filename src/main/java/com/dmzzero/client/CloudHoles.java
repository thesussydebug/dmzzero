package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath;
import com.dmzzero.beam.BeamMath.V;
import com.dmzzero.sky.CloudHoleMath;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// a beam through the cloud layer punches a hole in it. the clouds aren't touched: just before they're drawn, a ragged column round
// each hole is drawn into the stencil buffer only, and the clouds skip those pixels. that works the same for vanilla's clouds,
// embeddium's and dmz's own (they're all drawn from LevelRenderer.renderClouds), see LevelRendererCloudsMixin
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class CloudHoles {
    private static final int MAX_HOLES = 6;
    private static final int SEGMENTS = 40;
    // the column reaches a bit past the cloud slab (fancy clouds are 4 blocks thick)
    private static final double BELOW = 2, ABOVE = 6;
    private static final List<AbstractKiProjectile> BEAMS = new ArrayList<>();
    private static final Map<Integer, Hole> HOLES = new HashMap<>();
    private static boolean masked;
    private static int masks;
    private static int seeds;

    private CloudHoles() {
    }

    static final class Hole {
        double x, z;
        final double full;
        final int seed;
        int age;
        int endedAt = -1;
        boolean beamGone;

        Hole(double x, double z, double full, int seed) {
            this.x = x;
            this.z = z;
            this.full = full;
            this.seed = seed;
        }

        double radius() {
            return CloudHoleMath.radius(full, age, endedAt);
        }

        double cx() {
            return x + CloudHoleMath.driftX(age, endedAt);
        }
    }

    private static boolean enabled() {
        try {
            return ZeroConfig.CLIENT.cloudHoles.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof KiWaveEntity || event.getEntity() instanceof KiLaserEntity) BEAMS.add((AbstractKiProjectile) event.getEntity());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !enabled()) {
            clear();
            return;
        }
        if (mc.isPaused()) return;
        float cloudY = mc.level.effects().getCloudHeight();
        // the middle of the slab
        double layer = cloudY + 2;
        Iterator<AbstractKiProjectile> it = BEAMS.iterator();
        while (it.hasNext()) {
            AbstractKiProjectile beam = it.next();
            Hole hole = HOLES.get(beam.getId());
            if (beam.isRemoved() || beam.level() != mc.level) {
                if (hole != null) hole.beamGone = true;
                it.remove();
                continue;
            }
            V dir = BeamMath.direction(beam.getClashPitch(), beam.getClashYaw());
            V at = CloudHoleMath.crossing(new V(beam.getX(), beam.getY(), beam.getZ()), dir, beam.getClashBeamLength(), layer);
            if (at != null) {
                if (hole == null && HOLES.size() < MAX_HOLES && stencil(mc)) {
                    HOLES.put(beam.getId(), new Hole(at.x(), at.z(), CloudHoleMath.fullRadius(beam.getSize()), seeds++));
                } else if (hole != null && hole.endedAt < 0) {
                    hole.x = at.x();
                    hole.z = at.z();
                }
            } else if (hole != null && hole.endedAt < 0) {
                hole.endedAt = hole.age;
            }
        }
        Iterator<Hole> holes = HOLES.values().iterator();
        while (holes.hasNext()) {
            Hole hole = holes.next();
            if (hole.beamGone && hole.endedAt < 0) hole.endedAt = hole.age;
            hole.age++;
            if (CloudHoleMath.done(hole.age, hole.endedAt)) holes.remove();
        }
    }

    // the main framebuffer has no stencil until something asks for one
    private static boolean stencil(Minecraft mc) {
        if (!mc.getMainRenderTarget().isStencilEnabled()) mc.getMainRenderTarget().enableStencil();
        return mc.getMainRenderTarget().isStencilEnabled();
    }

    /** Called just before the clouds draw: masks every hole out of them. */
    public static void beforeClouds(PoseStack pose, Vec3 cam) {
        masked = false;
        Minecraft mc = Minecraft.getInstance();
        if (HOLES.isEmpty() || mc.level == null || !enabled()) return;
        // fabulous draws the clouds into a framebuffer of their own, without a stencil
        if (Minecraft.useShaderTransparency() || !mc.getMainRenderTarget().isStencilEnabled()) return;
        float cloudY = mc.level.effects().getCloudHeight();
        if (!Float.isFinite(cloudY)) return;

        GL11.glEnable(GL11.GL_STENCIL_TEST);
        RenderSystem.stencilMask(0xFF);
        RenderSystem.clearStencil(0);
        RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        // the vertices carry the camera's rotation themselves
        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.setIdentity();
        RenderSystem.applyModelViewMatrix();

        Matrix4f m = pose.last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        float y0 = (float) (cloudY - BELOW - cam.y), y1 = (float) (cloudY + ABOVE - cam.y);
        for (Hole hole : HOLES.values()) {
            double r = hole.radius();
            if (r < 0.5) continue;
            float cx = (float) (hole.cx() - cam.x), cz = (float) (hole.z - cam.z);
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = Math.PI * 2 * i / SEGMENTS, a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
                double r0 = r * CloudHoleMath.edge(a0, hole.seed), r1 = r * CloudHoleMath.edge(a1, hole.seed);
                float x0 = cx + (float) (Math.cos(a0) * r0), z0 = cz + (float) (Math.sin(a0) * r0);
                float x1 = cx + (float) (Math.cos(a1) * r1), z1 = cz + (float) (Math.sin(a1) * r1);
                // side
                buf.vertex(m, x0, y0, z0).endVertex();
                buf.vertex(m, x1, y0, z1).endVertex();
                buf.vertex(m, x1, y1, z1).endVertex();
                buf.vertex(m, x0, y0, z0).endVertex();
                buf.vertex(m, x1, y1, z1).endVertex();
                buf.vertex(m, x0, y1, z0).endVertex();
                // bottom and top
                buf.vertex(m, cx, y0, cz).endVertex();
                buf.vertex(m, x0, y0, z0).endVertex();
                buf.vertex(m, x1, y0, z1).endVertex();
                buf.vertex(m, cx, y1, cz).endVertex();
                buf.vertex(m, x1, y1, z1).endVertex();
                buf.vertex(m, x0, y1, z0).endVertex();
            }
        }
        BufferUploader.drawWithShader(buf.end());

        view.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.stencilMask(0x00);
        RenderSystem.stencilFunc(GL11.GL_NOTEQUAL, 1, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        masked = true;
        masks++;
    }

    /** Called right after the clouds: nothing else is masked. */
    public static void afterClouds() {
        if (!masked) return;
        masked = false;
        RenderSystem.stencilMask(0xFF);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    private static void clear() {
        BEAMS.clear();
        HOLES.clear();
    }

    public static int masks() {
        return masks;
    }

    public static int holes() {
        return HOLES.size();
    }

    /** The biggest open hole's centre and radius, or null. */
    public static double[] biggest() {
        Hole best = null;
        for (Hole h : HOLES.values()) if (best == null || h.radius() > best.radius()) best = h;
        return best == null ? null : new double[]{best.cx(), best.z, best.radius()};
    }
}
