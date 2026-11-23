package com.dmzzero.magic;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.racial.RacialRegistry;
import com.dragonminez.common.racial.impl.GlindDivinity;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.extras.ActionMode;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// a glind's spells: dmz zero's client sends a press of dmz's Action key (cast the picked spell) or Second Function
// (pick the next) in Racial mode. the pick is kept in PlayerPersisted.dmzzero.spell
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Magic {
    private static final String ROOT = "dmzzero";
    private static final String KEY = "spell";
    private static final Map<UUID, Long> LAST_CAST = new HashMap<>();
    private static final Map<UUID, Long> LAST_SWITCH = new HashMap<>();
    private static int casts;
    private static int switches;
    private static String lastReason = "";

    private Magic() {
    }

    /** Glind magic and at least one spell switched on, read the server's way on either side. */
    public static boolean on() {
        return ZeroConfig.read(ZeroConfig.SERVER.glindMagic, false) && Spells.anyOn();
    }

    /** The same things that stop DMZ's own actions: knocked down, stunned, locked in a strike or a match. */
    public static boolean canCast(ServerPlayer player, StatsData data) {
        if (data == null || !player.isAlive() || player.isSpectator() || !data.getStatus().isHasCreatedCharacter()) return false;
        if (player.hasEffect(MainEffects.STUN.get())) return false;
        var s = data.getStatus();
        return !s.isKnockedDown() && !s.isStunned() && !s.isStrikeLocked() && !s.isMatchFrozen() && !s.isInGodRitual();
    }

    /** The spell packet: SpellMath.CAST or NEXT from a key press. "ok", or why not. */
    public static String request(ServerPlayer player, int action) {
        return request(player, SpellKit.stats(player), action, player.server.getTickCount());
    }

    /** The self-test passes its own tick, since the server's doesn't move while it runs. */
    public static String request(ServerPlayer player, StatsData data, int action, long now) {
        // a release only ever stops something, so it counts even after leaving Racial mode
        if (action == SpellMath.RELEASE) return release(player);
        if (data == null || data.getStatus().getSelectedAction() != ActionMode.RACIAL) return "not_racial";
        if (RacialRegistry.forPlayer(data).filter(a -> a instanceof GlindMagic).isEmpty()) return "off";
        if (action == SpellMath.CAST) return cast(player, data, now);
        if (action != SpellMath.NEXT) return "unknown";
        UUID id = player.getUUID();
        if (SpellMath.samePress(LAST_SWITCH.get(id), now, SpellMath.PRESS_GAP)) return "same_press";
        LAST_SWITCH.put(id, now);
        if (!canCast(player, data)) return say(player, "cannot", "You can't use magic right now.");
        return next(player) == null ? "none" : "ok";
    }

    /** Casts the picked spell. "ok", or why not. */
    public static String cast(ServerPlayer player, StatsData data, long now) {
        casts++;
        lastReason = attempt(player, data, now);
        return lastReason;
    }

    private static String attempt(ServerPlayer player, StatsData data, long now) {
        if (!on() || data == null || !GlindDivinity.isActive(data)) return "off";
        UUID id = player.getUUID();
        if (SpellMath.samePress(LAST_CAST.get(id), now, SpellMath.PRESS_GAP)) return "same_press";
        LAST_CAST.put(id, now);
        String why;
        if (!canCast(player, data)) {
            why = say(player, "cannot", "You can't use magic right now.");
        } else if (Spells.holding(player)) {
            why = say(player, "held", "You can't cast while you're being held.");
        } else {
            Spell spell = Spells.get(selected(player));
            why = spell == null ? say(player, "none", "No spell is switched on.") : spell.cast(player, data, now);
        }
        return why;
    }

    /** The Action key went up: the picked spell stops whatever holding it does. */
    public static String release(ServerPlayer player) {
        if (!on()) return "off";
        Spell spell = Spells.get(selected(player));
        if (spell == null) return "none";
        spell.release(player);
        return "ok";
    }

    /** Picks the next spell. The id picked, or null with none on. */
    public static String next(ServerPlayer player) {
        if (!on()) return null;
        String current = selected(player);
        String next = SpellMath.next(Spells.ids(), Spells::on, current);
        if (next == null) {
            say(player, "none", "No spell is switched on.");
            return null;
        }
        switches++;
        boolean only = next.equals(current);
        if (!only) {
            Spell old = Spells.get(current);
            if (old != null && old.busy(player)) old.end(player, player.server.getTickCount());
            save(player, next);
        }
        player.displayClientMessage(Component.translatableWithFallback(only ? "message.dmzzero.magic.only" : "message.dmzzero.magic.spell",
                only ? "Spell: %s (your only spell)" : "Spell: %s", Spells.name(next)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return next;
    }

    public static String selected(Player player) {
        return SpellMath.selected(Spells.ids(), Spells::on, saved(player));
    }

    private static String saved(Player player) {
        CompoundTag root = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ROOT);
        return root.contains(KEY) ? root.getString(KEY) : null;
    }

    private static void save(Player player, String spell) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        if (spell == null) root.remove(KEY);
        else root.putString(KEY, spell);
        persisted.put(ROOT, root);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static String say(ServerPlayer player, String why, String english) {
        player.displayClientMessage(Component.translatableWithFallback("message.dmzzero.magic." + why, english).withStyle(ChatFormatting.GRAY), true);
        return why;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_CAST.remove(event.getEntity().getUUID());
        LAST_SWITCH.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_CAST.clear();
        LAST_SWITCH.clear();
    }

    public static int casts() {
        return casts;
    }

    public static int switches() {
        return switches;
    }

    public static String lastReason() {
        return lastReason;
    }

    /** The self-test: forget the last press and the saved pick. */
    public static void forget(ServerPlayer player) {
        LAST_CAST.remove(player.getUUID());
        LAST_SWITCH.remove(player.getUUID());
        save(player, null);
    }
}
