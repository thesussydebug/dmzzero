package com.dmzzero.form;

import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.server.events.players.combat.KnockbackHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// pushing others away with raw power: transformation bursts and giants' stomps
public final class Shove {
    private static LivingEntity pusher;

    private Shove() {
    }

    /** Whether source's power may move this one: never party members, creative or spectating players, or a player DMZ wouldn't let you hit. */
    public static boolean shovable(ServerPlayer source, LivingEntity e) {
        if (e == source || !e.isAlive() || e.isSpectator() || e.isRemoved()) return false;
        if (e.isPassenger() && e.getVehicle() == source) return false;
        TargetHelper.Relation rel = TargetHelper.getRelation(source, e);
        if (e instanceof Player p) return !p.isCreative() && rel == TargetHelper.Relation.HOSTILE;
        return rel != TargetHelper.Relation.FRIENDLY;
    }

    /** DMZ's own heavy knockback, so flying players are synced and smashes into the scenery follow. */
    public static void push(LivingEntity source, LivingEntity target, Vec3 knock) {
        LivingEntity before = pusher;
        pusher = source;
        try {
            KnockbackHelper.apply(target, knock);
        } finally {
            pusher = before;
        }
    }

    /** Who is pushing right now, when it's a burst or a stomp. */
    static LivingEntity pusher() {
        return pusher;
    }
}
