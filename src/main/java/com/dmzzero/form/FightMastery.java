package com.dmzzero.form;

import com.dmzzero.ZeroConfig;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.FormMasteries;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

// winning DMZ Zero's own fights while in a form pays some of that form's mastery
public final class FightMastery {
    private static final String ROOT = "dmzzero";
    private static final String KEY = "fightMastery";

    private FightMastery() {
    }

    public enum Win { DUEL, BATTLE, RIVAL }

    public record Paid(String form, double formMastery, String stack, double stackMastery) {
    }

    private static double percent(Win win) {
        return switch (win) {
            case DUEL -> ZeroConfig.SERVER.masteryDuel.get();
            case BATTLE -> ZeroConfig.SERVER.masteryBattle.get();
            case RIVAL -> ZeroConfig.SERVER.masteryRival.get();
        };
    }

    /** The winner gets their form's (and stacked form's) mastery, once a cooldown. Returns what was paid, or null. */
    public static Paid award(ServerPlayer winner, Win win) {
        if (!ZeroConfig.SERVER.fightMastery.get() || winner == null || !winner.isAlive()) return null;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, winner).orElse(null);
        if (data == null || !data.getStatus().isHasCreatedCharacter()) return null;
        Character c = data.getCharacter();
        if (!c.hasActiveForm() && !c.hasActiveStackForm()) return null;
        CompoundTag persisted = winner.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        long now = System.currentTimeMillis();
        CompoundTag root = persisted.getCompound(ROOT);
        if (!MasteryMath.ready(now, root.getLong(KEY), ZeroConfig.SERVER.masteryCooldown.get())) return null;
        double pct = percent(win);
        String formName = null, stackName = null;
        double formGain = 0, stackGain = 0;
        // the form's own data, looked up the way dmz's mastery gain does (a character's cached form data can be missing)
        if (c.hasActiveForm()) {
            String group = c.getActiveFormGroup(), name = c.getActiveForm();
            FormConfig.FormData form = ConfigManager.getForm(c.getRaceName(), group, name);
            FormMasteries m = c.getFormMasteries();
            double before = m.getMastery(group, name);
            c.gainMastery(group, name, MasteryMath.amount(form == null ? null : form.getMaxMastery(), pct));
            formGain = m.getMastery(group, name) - before;
            formName = name;
        }
        if (c.hasActiveStackForm()) {
            String group = c.getActiveStackFormGroup(), name = c.getActiveStackForm();
            FormConfig.FormData form = ConfigManager.getStackForm(group, name);
            Double max = form == null ? null : form.getMaxMastery();
            FormMasteries m = c.getStackFormMasteries();
            double before = m.getMastery(group, name);
            m.addMastery(group, name, MasteryMath.amount(max, pct), max == null ? 100.0 : max);
            stackGain = m.getMastery(group, name) - before;
            stackName = name;
        }
        if (formGain <= 0 && stackGain <= 0) return null;
        root.putLong(KEY, now);
        persisted.put(ROOT, root);
        winner.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        StatsSyncS2C.sendRequested(winner);
        String shown = formGain > 0 ? formName : stackName;
        winner.sendSystemMessage(Component.translatableWithFallback("message.dmzzero.form_mastery", "The win sharpened your %s. (+%s mastery)",
                shown, String.format(java.util.Locale.ROOT, "%.1f", Math.max(formGain, stackGain))).withStyle(ChatFormatting.AQUA));
        return new Paid(formName, formGain, stackName, stackGain);
    }

    // for the self-test
    public static void forget(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        root.remove(KEY);
        persisted.put(ROOT, root);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
