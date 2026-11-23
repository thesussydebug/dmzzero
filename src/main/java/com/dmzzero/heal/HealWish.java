package com.dmzzero.heal;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.wish.DragonWishRegistry;
import com.dragonminez.common.wish.Wish;
import com.dragonminez.common.wish.wishes.CommandWish;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// "heal the land": a wish Shenron and Porunga can grant. It's dmz's own command wish, so players without dmz zero can see and pick it too
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class HealWish {
    public static final String NAME = "Heal the land";
    public static final String DESCRIPTION = "Everything ki fights broke in the world you're in comes back, all at once.";
    static final String[] DRAGONS = {"shenron", "porunga"};
    // %player% is dmz's: the wisher's name, or their UUID when the name isn't plain
    static final String COMMAND = "execute as %player% at @s run dmzzero heal wish";

    private HealWish() {
    }

    // a world loads its wish lists before its own server config, so the wish is added again once the config is in
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        addTo();
    }

    /** Called after dmz loads its wish lists: adds the wish to each dragon that doesn't have it yet. */
    public static void addTo() {
        Map<String, List<Wish>> now = DragonWishRegistry.getServerWishes();
        if (now == null || !ZeroConfig.SERVER_SPEC.isLoaded() || !ZeroConfig.SERVER.healing.get() || !ZeroConfig.SERVER.healWish.get()) return;
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
        DMZZero.LOG.info("Added the healing wish to {} dragon(s)", added);
    }

    static Wish wish() {
        CommandWish w = new CommandWish(NAME, DESCRIPTION, COMMAND);
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
