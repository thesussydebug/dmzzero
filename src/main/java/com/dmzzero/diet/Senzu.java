package com.dmzzero.diet;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.init.item.consumables.SenzuBeanItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// korin says one senzu bean keeps you fed for ten days: the hunger bar stays full that long. saturation still runs down,
// so healing goes back to vanilla's slow pace once it's spent. the time is the overworld's, kept with the player
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Senzu {
    static final String ROOT = "dmzzero";
    static final String UNTIL = "senzuUntil";
    private static final Map<UUID, Long> FED = new HashMap<>();
    private static int eaten;

    private Senzu() {
    }

    @SubscribeEvent
    public static void onFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getItem().getItem() instanceof SenzuBeanItem)) return;
        eat(p);
    }

    public static void eat(ServerPlayer p) {
        int days = ZeroConfig.SERVER.senzuDays.get();
        if (days <= 0) return;
        long now = p.server.overworld().getGameTime();
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        long until = DietMath.later(root.getLong(UNTIL), DietMath.senzuUntil(now, days));
        root.putLong(UNTIL, until);
        persisted.put(ROOT, root);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        FED.put(p.getUUID(), until);
        eaten++;
        p.displayClientMessage(Component.translatableWithFallback("message.dmzzero.senzu.fed",
                "One senzu bean, and you won't be hungry for %s days.", days).withStyle(ChatFormatting.GREEN), true);
    }

    /** Read by FoodDataMixin every food tick, so it stays a map lookup. */
    public static boolean fed(Player player) {
        if (!(player instanceof ServerPlayer p)) return false;
        Long until = FED.get(p.getUUID());
        if (until == null) return false;
        if (ZeroConfig.SERVER.senzuDays.get() <= 0 || !DietMath.fed(p.server.overworld().getGameTime(), until)) {
            FED.remove(p.getUUID());
            return false;
        }
        return true;
    }

    public static long until(CompoundTag persisted) {
        return persisted.getCompound(ROOT).getLong(UNTIL);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        long until = until(p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG));
        if (DietMath.fed(p.server.overworld().getGameTime(), until)) FED.put(p.getUUID(), until);
    }

    // a respawned player is a new object with the same saved data
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FED.clear();
    }

    public static int eaten() {
        return eaten;
    }

    /** The self-test eats a bean, then ends its effect again. */
    public static void forget(ServerPlayer p) {
        FED.remove(p.getUUID());
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        root.remove(UNTIL);
        persisted.put(ROOT, root);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
