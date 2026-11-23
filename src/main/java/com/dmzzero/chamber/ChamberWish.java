package com.dmzzero.chamber;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.wish.DragonWishRegistry;
import com.dragonminez.common.wish.Wish;
import com.dragonminez.common.wish.wishes.CommandWish;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// "more time in the Time Chamber": a wish Shenron and Porunga can grant. dmz's own command wish, like Heal the land
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class ChamberWish {
    public static final String NAME = "More time in the Time Chamber";
    static final String[] DRAGONS = {"shenron", "porunga"};
    static final String COMMAND = "execute as %player% at @s run dmzzero chamber wish";

    private ChamberWish() {
    }

    // a world loads its wish lists before its config, so the wish is added again once the config is in
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        addTo();
    }

    /** Called after dmz loads its wish lists: adds the wish to each dragon that doesn't have it yet. */
    public static void addTo() {
        Map<String, List<Wish>> now = DragonWishRegistry.getServerWishes();
        if (now == null || !ZeroConfig.SERVER_SPEC.isLoaded() || !ZeroConfig.SERVER.chamberWish.get()
                || ZeroConfig.SERVER.chamberLimitDays.get() <= 0) return;
        Map<String, List<Wish>> next = new HashMap<>(now);
        int added = 0;
        for (String dragon : DRAGONS) {
            List<Wish> list = next.get(dragon);
            if (list == null || has(list)) continue;
            List<Wish> more = new ArrayList<>(list);
            more.add(wish());
            next.put(dragon, List.copyOf(more));
            added++;
        }
        if (added == 0) return;
        DragonWishRegistry.setServerWishes(Map.copyOf(next));
        DMZZero.LOG.info("Added the Time Chamber wish to {} dragon(s)", added);
    }

    static Wish wish() {
        int days = ZeroConfig.SERVER.chamberWishDays.get();
        CommandWish w = new CommandWish(NAME, "The Time Chamber's door opens for you for " + days + " more in-game days.", COMMAND);
        w.setRepeatable(true);
        return w;
    }

    public static boolean has(List<Wish> list) {
        for (Wish w : list) if (NAME.equals(w.getName())) return true;
        return false;
    }

    public static Wish find(String dragon) {
        Map<String, List<Wish>> now = DragonWishRegistry.getServerWishes();
        List<Wish> list = now == null ? null : now.get(dragon);
        if (list == null) return null;
        for (Wish w : list) if (NAME.equals(w.getName())) return w;
        return null;
    }
}
