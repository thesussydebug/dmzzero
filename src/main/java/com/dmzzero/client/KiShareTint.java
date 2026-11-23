package com.dmzzero.client;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dmzzero.kishare.StreamMath;
import com.dragonminez.client.render.HelmetFormLook;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// dmz draws the ki stream itself; this works out whose aura takes on whose colors. dmz already syncs
// every donor's target, so nothing extra goes over the network
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID, value = Dist.CLIENT)
public final class KiShareTint {
    private static final double RANGE = 64;
    // read from the render thread by the aura mixin, only ever swapped
    private static volatile Map<Integer, float[][]> tints = Map.of();
    private static volatile float[] lastTint;
    private static int tinted;
    private static int testReceiver = -1;
    private static float[][] testColors;

    private KiShareTint() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ZeroConfig.CLIENT.auraTint.get()) {
            tints = Map.of();
            return;
        }
        Map<Integer, List<float[]>> byReceiver = new HashMap<>();
        for (Player donor : mc.level.players()) {
            if (donor.distanceToSqr(mc.player) > RANGE * RANGE) continue;
            StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, donor).orElse(null);
            if (stats == null) continue;
            int target = stats.getStatus().getKiTransferTarget();
            if (target < 0) continue;
            Entity receiver = mc.level.getEntity(target);
            if (receiver == null || receiver == donor) continue;
            byReceiver.computeIfAbsent(receiver.getId(), id -> new ArrayList<>()).add(StreamMath.rgb(color(stats)));
        }
        if (testColors != null) byReceiver.computeIfAbsent(testReceiver, id -> new ArrayList<>()).addAll(List.of(testColors));
        if (byReceiver.isEmpty()) {
            tints = Map.of();
            return;
        }
        Map<Integer, float[][]> next = new HashMap<>();
        byReceiver.forEach((id, colors) -> next.put(id, colors.toArray(new float[0][])));
        tints = next;
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) tints = Map.of();
    }

    public static float[][] tintFor(int entityId) {
        return tints.get(entityId);
    }

    static int color(StatsData stats) {
        String hex = null;
        try {
            hex = HelmetFormLook.activeAuraColor(stats);
        } catch (RuntimeException ignored) {
        }
        if (hex == null || hex.isEmpty()) hex = stats.getCharacter().getAuraColor();
        return StreamMath.hex(hex, 0x7FD4FF);
    }

    public static void tinted(float[] color) {
        lastTint = color;
        tinted++;
    }

    public static float[] lastTint() {
        return lastTint;
    }

    public static int tintedCount() {
        return tinted;
    }

    // dev test: one real player in single player, so the donor is made up
    static void setTest(int receiver, float[][] colors) {
        testReceiver = receiver;
        testColors = colors;
    }
}
