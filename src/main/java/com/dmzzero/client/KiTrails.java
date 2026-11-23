package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.kishare.StreamMath;
import com.dmzzero.sense.TrailMath;
import com.dragonminez.client.systems.kisense.KiSenseScan;
import com.dragonminez.client.systems.kisense.KiSenseState;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

// ki trails: with dmz's ki sense up, a fading line of the fighter's ki shows where they flew in the last five seconds
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class KiTrails {
    private static final int MAX_TRAILS = 16;
    private static final int DRAW_EVERY = 2;
    private static final int BUDGET = 120;
    private static final Map<Integer, Trailed> TRAILS = new HashMap<>();
    private static long ticks;
    private static int spawned;

    private KiTrails() {
    }

    private static final class Trailed {
        final TrailMath.Trail trail = new TrailMath.Trail();
        Vector3f color = new Vector3f(1, 1, 1);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        ticks++;
        if (!ZeroConfig.CLIENT.kiTrails.get() || !KiSenseState.isActive()) {
            TRAILS.clear();
            return;
        }
        Set<Integer> sensed = new HashSet<>(KiSenseScan.getSearchEntities());
        sensed.addAll(KiSenseScan.getCombatEntities());
        for (int id : sensed) {
            Entity e = mc.level.getEntity(id);
            if (!(e instanceof LivingEntity living) || e == mc.player || !e.isAlive()) continue;
            double dx = e.getX() - e.xo, dy = e.getY() - e.yo, dz = e.getZ() - e.zo;
            if (!TrailMath.leavesTrail(e.onGround(), dx * dx + dy * dy + dz * dz)) continue;
            Trailed t = TRAILS.get(id);
            if (t == null) {
                if (TRAILS.size() >= MAX_TRAILS) continue;
                t = new Trailed();
                t.color = color(living);
                TRAILS.put(id, t);
            }
            t.trail.add(ticks, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ());
        }
        Iterator<Trailed> it = TRAILS.values().iterator();
        while (it.hasNext()) {
            Trailed t = it.next();
            t.trail.prune(ticks);
            if (t.trail.size() == 0) it.remove();
        }
        if (ticks % DRAW_EVERY == 0) draw(mc.level);
    }

    private static void draw(ClientLevel level) {
        int budget = BUDGET;
        for (Trailed t : TRAILS.values()) {
            for (TrailMath.Point p : t.trail.points()) {
                if (budget <= 0) return;
                float fade = TrailMath.fade(ticks - p.tick());
                if (fade <= 0.05f) continue;
                level.addParticle(new DustParticleOptions(t.color, 0.8f + 1.4f * fade), p.x(), p.y(), p.z(), 0, 0, 0);
                budget--;
                spawned++;
            }
        }
    }

    private static Vector3f color(LivingEntity e) {
        int rgb = 0xF4FBFF;
        if (e instanceof Player p) {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
            if (data != null) rgb = StreamMath.hex(data.getCharacter().getActiveAuraColor(), rgb);
        }
        return new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) TRAILS.clear();
    }

    // for the dev autotest
    public static int trails() {
        return TRAILS.size();
    }

    public static int points() {
        int n = 0;
        for (Trailed t : TRAILS.values()) n += t.trail.size();
        return n;
    }

    public static int spawned() {
        return spawned;
    }
}
