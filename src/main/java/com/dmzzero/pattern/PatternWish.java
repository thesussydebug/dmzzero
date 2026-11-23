package com.dmzzero.pattern;

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

// "clothing patterns of your choice": a wish Shenron can grant. dmz's own command wish, like Heal the land, so players
// without dmz zero can pick it too; the picking happens in chat
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class PatternWish {
    public static final String NAME = "Clothing patterns of your choice";
    static final String DRAGON = "shenron";
    static final String COMMAND = "execute as %player% at @s run dmzzero pattern wish";

    private PatternWish() {
    }

    // a world loads its wish lists before its config, so the wish is added again once the config is in
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        addTo();
    }

    /** Called after dmz loads its wish lists: adds the wish to Shenron's when it isn't there yet. */
    public static void addTo() {
        Map<String, List<Wish>> now = DragonWishRegistry.getServerWishes();
        if (now == null || !ZeroConfig.SERVER_SPEC.isLoaded() || !ZeroConfig.SERVER.patternWish.get()) return;
        List<Wish> list = now.get(DRAGON);
        if (list == null || has(list)) return;
        List<Wish> more = new ArrayList<>(list);
        more.add(wish());
        Map<String, List<Wish>> next = new HashMap<>(now);
        next.put(DRAGON, List.copyOf(more));
        DragonWishRegistry.setServerWishes(Map.copyOf(next));
        DMZZero.LOG.info("Added the clothing pattern wish to Shenron");
    }

    static Wish wish() {
        int picks = ZeroConfig.SERVER.patternPicks.get();
        CommandWish w = new CommandWish(NAME, "Pick " + picks + " gi, armor or outfit patterns from every one there is.", COMMAND);
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
