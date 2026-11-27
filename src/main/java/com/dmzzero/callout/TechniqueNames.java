package com.dmzzero.callout;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.TechniqueData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// dmz only sends a fired technique's id to clients, and the id drops the spaces from its name ("goku_megabeam"). the server knows the
// real name, so it rides along as the projectile's vanilla custom name, which every client already gets. it's never shown as a nametag.
// dmz's own techniques are named by a lang key, which goes as a translatable so each client reads it in its own language
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class TechniqueNames {
    private static int named;
    private static AbstractKiProjectile lastNamed;
    private static int seen;
    private static AbstractKiProjectile lastSeen;

    private TechniqueNames() {
    }

    // dmz sets the technique id before adding the projectile, and the custom name set here goes out with its spawn
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !(event.getEntity() instanceof AbstractKiProjectile proj)) return;
        seen++;
        lastSeen = proj;
        if (!ZeroConfig.SERVER.techniqueNames.get() || proj.hasCustomName()) return;
        if (!(proj.getOwner() instanceof ServerPlayer owner)) return;
        Component name = shown(nameOf(owner, proj.getTechniqueId()));
        if (name == null) return;
        proj.setCustomName(name);
        proj.setCustomNameVisible(false);
        named++;
        lastNamed = proj;
    }

    // the self-test checks what dmz's own dispatcher fired
    public static AbstractKiProjectile lastNamed() {
        return lastNamed;
    }

    /** Every ki projectile seen joining a server level, named or not, and the last of them. */
    public static int seen() {
        return seen;
    }

    public static String lastSeen() {
        AbstractKiProjectile p = lastSeen;
        if (p == null) return "none";
        return p.getClass().getSimpleName() + " id=" + p.getTechniqueId() + " owner=" + (p.getOwner() == null ? "null" : p.getOwner().getName().getString())
                + " named=" + p.hasCustomName() + " at " + p.blockPosition();
    }

    // a key is never cut to a callout's length: half a key matches no translation
    private static Component shown(String name) {
        if (CalloutMath.isNameKey(name)) return Component.translatable(name);
        String clean = CalloutMath.clean(name);
        return clean == null ? null : Component.literal(clean);
    }

    static String nameOf(ServerPlayer owner, String techniqueId) {
        if (techniqueId == null || techniqueId.isEmpty()) return null;
        StatsData d = StatsProvider.get(StatsCapability.INSTANCE, owner).orElse(null);
        if (d == null) return null;
        TechniqueData t = d.getTechniques().getUnlockedTechniques().get(techniqueId);
        return t == null ? null : t.getName();
    }

    public static int named() {
        return named;
    }
}
