package com.dmzzero.status;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

// how long each feature's tick handler takes on the server thread, for /dmzzero status. a handler does
// long start = TickCost.start(); ...; TickCost.add(ID, start);
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class TickCost {
    private static final TickWindow WINDOW = new TickWindow();
    private static final List<List<IntSupplier>> HELD = new ArrayList<>();
    private static volatile Thread serverThread;
    private static volatile Boolean testTiming;
    private static volatile Integer testWindow;

    private TickCost() {
    }

    /**
     * A part, named after the feature's server.toml section so the report can use its name.
     * Each supplier counts something the feature keeps in memory; classes that share a section add up.
     */
    public static int id(String section, IntSupplier... held) {
        synchronized (HELD) {
            int id = WINDOW.id(section);
            while (HELD.size() <= id) HELD.add(new ArrayList<>());
            HELD.get(id).addAll(List.of(held));
            return id;
        }
    }

    /** 0 when called off the server thread (a client's own player tick) or with timing turned off. */
    public static long start() {
        if (Thread.currentThread() != serverThread || !timing()) return 0;
        return System.nanoTime();
    }

    /** [server.status] timing, unless the self-test has set it in memory. */
    public static boolean timing() {
        Boolean test = testTiming;
        return test != null ? test : ZeroConfig.SERVER.statusTiming.get();
    }

    /** Self-test only: timing on or off without writing server.toml, which Forge would save and reload mid-test. Null clears it. */
    public static void setTestTiming(Boolean on) {
        testTiming = on;
    }

    /** Autotest only: a window this many ticks long, starting now, so a scene's totals don't roll over. Null goes back to [server.status]. */
    public static void setTestWindow(Integer ticks) {
        testWindow = ticks;
        WINDOW.clear();
    }

    public static void add(int id, long start) {
        if (start != 0) WINDOW.add(id, System.nanoTime() - start);
    }

    public static TickWindow.Report report() {
        return WINDOW.report();
    }

    /** What the section's features keep in memory right now, or -1 when none of them count it. */
    public static int held(String section) {
        synchronized (HELD) {
            int id = WINDOW.id(section);
            return id < HELD.size() && !HELD.get(id).isEmpty() ? sum(HELD.get(id)) : -1;
        }
    }

    /** Every section that counts what it keeps in memory, with its count right now, in the order they registered. */
    public static Map<String, Integer> held() {
        synchronized (HELD) {
            List<String> names = WINDOW.names();
            Map<String, Integer> out = new LinkedHashMap<>();
            for (int id = 0; id < HELD.size() && id < names.size(); id++) {
                if (!HELD.get(id).isEmpty()) out.put(names.get(id), sum(HELD.get(id)));
            }
            return out;
        }
    }

    private static int sum(List<IntSupplier> counts) {
        int sum = 0;
        for (IntSupplier s : counts) sum += s.getAsInt();
        return sum;
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        serverThread = Thread.currentThread();
        WINDOW.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Integer test = testWindow;
        WINDOW.endTick(test != null ? test : ZeroConfig.SERVER.statusSeconds.get() * 20);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        serverThread = null;
        testTiming = null;
        testWindow = null;
        WINDOW.clear();
    }
}
