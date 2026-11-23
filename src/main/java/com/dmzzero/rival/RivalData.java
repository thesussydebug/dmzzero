package com.dmzzero.rival;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

// the last player who beat you, and the score between you; kept in PlayerPersisted.dmzzero.rival
public final class RivalData {
    private static final String ROOT = "dmzzero";
    private static final String RIVAL = "rival";
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String SINCE = "since";
    private static final String WINS = "wins";
    private static final String LOSSES = "losses";
    private static final String SETTLED = "settled";
    private static final String BONUS_AT = "bonusAt";
    private static final String SHADOW = "shadow";

    public enum Win {
        // not your rival
        NOT_RIVAL,
        // first win since they last beat you
        BONUS,
        // you already got even
        SETTLED,
        // even again, but the last bonus was too recent
        COOLDOWN
    }

    private RivalData() {
    }

    private static CompoundTag rivalTag(CompoundTag persisted) {
        return persisted.getCompound(ROOT).getCompound(RIVAL);
    }

    private static void put(CompoundTag persisted, CompoundTag rival) {
        CompoundTag root = persisted.getCompound(ROOT);
        root.put(RIVAL, rival);
        persisted.put(ROOT, root);
    }

    public static UUID rival(CompoundTag persisted) {
        CompoundTag r = rivalTag(persisted);
        return r.hasUUID(ID) ? r.getUUID(ID) : null;
    }

    public static String name(CompoundTag persisted) {
        return rivalTag(persisted).getString(NAME);
    }

    public static int wins(CompoundTag persisted) {
        return rivalTag(persisted).getInt(WINS);
    }

    public static int losses(CompoundTag persisted) {
        return rivalTag(persisted).getInt(LOSSES);
    }

    public static long since(CompoundTag persisted) {
        return rivalTag(persisted).getLong(SINCE);
    }

    // true when this made them a new rival
    public static boolean beatenBy(CompoundTag persisted, UUID winner, String winnerName, long nowMs) {
        CompoundTag r = rivalTag(persisted);
        boolean same = r.hasUUID(ID) && r.getUUID(ID).equals(winner);
        if (!same) {
            r = new CompoundTag();
            r.putUUID(ID, winner);
            r.putLong(SINCE, nowMs);
        }
        r.putString(NAME, winnerName);
        r.putInt(LOSSES, r.getInt(LOSSES) + 1);
        r.putBoolean(SETTLED, false);
        put(persisted, r);
        return !same;
    }

    public static Win won(CompoundTag persisted, UUID loser, long nowMs, long cooldownMs) {
        CompoundTag r = rivalTag(persisted);
        if (!r.hasUUID(ID) || !r.getUUID(ID).equals(loser)) return Win.NOT_RIVAL;
        r.putInt(WINS, r.getInt(WINS) + 1);
        Win result;
        if (r.getBoolean(SETTLED)) {
            result = Win.SETTLED;
        } else if (r.contains(BONUS_AT) && nowMs - r.getLong(BONUS_AT) < cooldownMs) {
            result = Win.COOLDOWN;
        } else {
            r.putBoolean(SETTLED, true);
            r.putLong(BONUS_AT, nowMs);
            result = Win.BONUS;
        }
        put(persisted, r);
        return result;
    }

    // the rival's stats the last time you fought, for their shadow
    public record Snapshot(double maxHealth, double defense, long battlePower, double melee, double ki, int aura, long at) {
        public Snapshot scaled(double f) {
            if (f == 1.0) return this;
            return new Snapshot(maxHealth * f, defense * f, Math.round(battlePower * f), melee * f, ki * f, aura, at);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putDouble("maxHealth", maxHealth);
            t.putDouble("defense", defense);
            t.putLong("bp", battlePower);
            t.putDouble("melee", melee);
            t.putDouble("ki", ki);
            t.putInt("aura", aura);
            t.putLong("at", at);
            return t;
        }

        static Snapshot load(CompoundTag t) {
            return new Snapshot(t.getDouble("maxHealth"), t.getDouble("defense"), t.getLong("bp"),
                    t.getDouble("melee"), t.getDouble("ki"), t.getInt("aura"), t.getLong("at"));
        }
    }

    public static Snapshot snapshot(CompoundTag persisted) {
        CompoundTag r = rivalTag(persisted);
        return r.contains(SHADOW) ? Snapshot.load(r.getCompound(SHADOW)) : null;
    }

    // only your current rival; a new rival starts without one
    public static boolean snapshot(CompoundTag persisted, UUID of, Snapshot s) {
        CompoundTag r = rivalTag(persisted);
        if (!r.hasUUID(ID) || !r.getUUID(ID).equals(of)) return false;
        r.put(SHADOW, s.save());
        put(persisted, r);
        return true;
    }

    public static void clear(CompoundTag persisted) {
        CompoundTag root = persisted.getCompound(ROOT);
        root.remove(RIVAL);
        persisted.put(ROOT, root);
    }

    public static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static void save(Player player, CompoundTag persisted) {
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
