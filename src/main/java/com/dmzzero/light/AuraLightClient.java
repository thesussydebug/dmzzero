package com.dmzzero.light;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.beam.BeamMath;
import com.dmzzero.impact.ImpactLookup;
import com.dmzzero.impact.ImpactProfile;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Status;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// auras light the world around them on this screen: the light level is mixed into what blocks, entities
// and particles are drawn with, and the chunks around a moving aura are rebuilt so the ground shows it
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class AuraLightClient {
    private static final double RANGE = 96;
    private static final float[] NONE = new float[0];
    // read from the chunk builder threads, so it is only ever swapped, never changed in place
    private static volatile float[] sources = NONE;
    private static int rebuilds;
    // ki blasts and beams light their surroundings too: every ki projectile this client knows of
    private static final List<AbstractKiProjectile> KI = new ArrayList<>();
    // the nearest ones only; each moving light rebuilds the chunks round it
    private static final int MAX_KI = 16;
    private static final int BEAM_POINTS = 6;
    private static final double BEAM_SPACING = 8;
    // and they move every other tick, not every tick
    private static final int KI_EVERY = 2;
    private static float[] kiSnapshot = NONE;
    private static int kiTick;

    private AuraLightClient() {
    }

    public static int lightAt(int x, int y, int z) {
        float[] s = sources;
        return s.length == 0 ? 0 : AuraLight.lightAt(s, x, y, z);
    }

    public static int sourceCount() {
        return sources.length / AuraLight.STRIDE;
    }

    public static int rebuilds() {
        return rebuilds;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        float[] next = mc.level == null || mc.player == null ? NONE : collect(mc);
        float[] before = sources;
        if (AuraLight.same(before, next)) return;
        sources = next;
        if (mc.levelRenderer != null && mc.level != null) rebuild(mc, before, next);
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        sources = NONE;
        KI.clear();
        kiSnapshot = NONE;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof AbstractKiProjectile ki) KI.add(ki);
    }

    public static int kiSourceCount() {
        return kiSnapshot.length / AuraLight.STRIDE;
    }

    /** The brightest ki light right now, for the dev test. */
    public static int brightestKi() {
        int best = 0;
        for (int i = 3; i < kiSnapshot.length; i += AuraLight.STRIDE) best = Math.max(best, (int) kiSnapshot[i]);
        return best;
    }

    // dev tests light a spot with no aura on screen to get in the way
    private static float[] testSource;

    public static void setTestSource(float[] source) {
        testSource = source;
    }

    private static float[] collect(Minecraft mc) {
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        float[] out = testSource == null ? NONE : testSource.clone();
        int n = out.length / AuraLight.STRIDE;
        if (!ZeroConfig.CLIENT.kiLight.get()) kiSnapshot = NONE;
        float[] ki = ZeroConfig.CLIENT.kiLight.get() ? kiSources(mc, eye) : NONE;
        if (ki.length > 0) {
            float[] grown = new float[out.length + ki.length];
            System.arraycopy(out, 0, grown, 0, n * AuraLight.STRIDE);
            System.arraycopy(ki, 0, grown, n * AuraLight.STRIDE, ki.length);
            out = grown;
            n += ki.length / AuraLight.STRIDE;
        }
        if (!ZeroConfig.CLIENT.auraLight.get()) return trim(out, n);
        for (Player player : mc.level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.distanceToSqr(eye) > RANGE * RANGE) continue;
            StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            if (stats == null || !auraVisible(stats.getStatus())) continue;
            ImpactProfile p = ImpactLookup.current(stats);
            int level = AuraLight.level(p.tier(), p.has(ImpactProfile.DARK));
            if (out.length < (n + 1) * AuraLight.STRIDE) {
                float[] grown = new float[Math.max(AuraLight.STRIDE * 4, out.length * 2)];
                System.arraycopy(out, 0, grown, 0, n * AuraLight.STRIDE);
                out = grown;
            }
            int i = n * AuraLight.STRIDE;
            out[i] = (float) player.getX();
            out[i + 1] = (float) (player.getY() + player.getBbHeight() * 0.5);
            out[i + 2] = (float) player.getZ();
            out[i + 3] = level;
            n++;
        }
        return trim(out, n);
    }

    private static float[] trim(float[] out, int n) {
        if (n == 0) return NONE;
        if (out.length == n * AuraLight.STRIDE) return out;
        float[] exact = new float[n * AuraLight.STRIDE];
        System.arraycopy(out, 0, exact, 0, exact.length);
        return exact;
    }

    // a ki ball is one light; a beam is lit from the hands along its whole length
    private static float[] kiSources(Minecraft mc, Vec3 eye) {
        if (kiTick++ % KI_EVERY != 0) return kiSnapshot;
        KI.removeIf(e -> e.isRemoved() || e.level() != mc.level);
        if (KI.isEmpty()) return kiSnapshot = NONE;
        List<float[]> found = new ArrayList<>();
        for (AbstractKiProjectile ki : KI) {
            if (ki.isInvisible()) continue;
            int color = ki.getColor() & 0xFFFFFF;
            if (ki instanceof KiWaveEntity || ki instanceof KiLaserEntity) {
                double length = ki.getClashBeamLength();
                if (ki.distanceToSqr(eye) > (RANGE + length) * (RANGE + length)) continue;
                int level = KiLightMath.dimmed(KiLightMath.beamLevel(ki.getSize()), color);
                if (level <= 0) continue;
                BeamMath.V dir = BeamMath.direction(ki.getClashPitch(), ki.getClashYaw());
                for (double d : KiLightMath.beamPoints(length, BEAM_POINTS, BEAM_SPACING)) {
                    found.add(new float[]{(float) (ki.getX() + dir.x() * d), (float) (ki.getY() + dir.y() * d), (float) (ki.getZ() + dir.z() * d), level});
                }
            } else {
                if (ki.distanceToSqr(eye) > RANGE * RANGE) continue;
                int level = KiLightMath.dimmed(KiLightMath.ballLevel(ki.getSize()), color);
                if (level <= 0) continue;
                found.add(new float[]{(float) ki.getX(), (float) (ki.getY() + ki.getBbHeight() * 0.5), (float) ki.getZ(), level});
            }
        }
        float[] all = new float[found.size() * AuraLight.STRIDE];
        for (int i = 0; i < found.size(); i++) System.arraycopy(found.get(i), 0, all, i * AuraLight.STRIDE, AuraLight.STRIDE);
        return kiSnapshot = all.length == 0 ? NONE : KiLightMath.nearest(all, eye.x, eye.y, eye.z, MAX_KI);
    }

    // dmz's own test for drawing an aura
    static boolean auraVisible(Status s) {
        return s.isAuraActive() || s.isPermanentAura() || s.isForcedAura();
    }

    // every section the old or the new light reaches, so it shows where it is and leaves where it was
    private static void rebuild(Minecraft mc, float[] before, float[] after) {
        Set<Long> sections = new HashSet<>();
        collectSections(before, sections);
        collectSections(after, sections);
        for (long s : sections) {
            mc.levelRenderer.setSectionDirty(SectionPos.x(s), SectionPos.y(s), SectionPos.z(s));
        }
        rebuilds++;
    }

    private static void collectSections(float[] src, Set<Long> out) {
        for (int i = 0; i + AuraLight.STRIDE - 1 < src.length; i += AuraLight.STRIDE) {
            int level = (int) src[i + 3];
            for (int x = AuraLight.minSection(src[i], level); x <= AuraLight.maxSection(src[i], level); x++) {
                for (int y = AuraLight.minSection(src[i + 1], level); y <= AuraLight.maxSection(src[i + 1], level); y++) {
                    for (int z = AuraLight.minSection(src[i + 2], level); z <= AuraLight.maxSection(src[i + 2], level); z++) {
                        out.add(SectionPos.asLong(x, y, z));
                    }
                }
            }
        }
    }
}
