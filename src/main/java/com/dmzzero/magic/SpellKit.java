package com.dmzzero.magic;

import com.dmzzero.beam.BeamMath.V;
import com.dragonminez.common.network.StatsDeltaSync;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** What the Glind spells share: stats, paying ki, the lilac sparkle, and what they never move. */
final class SpellKit {
    static final DustParticleOptions LILAC = new DustParticleOptions(new Vector3f(0.78f, 0.62f, 1f), 1.1f);

    private SpellKit() {
    }

    static StatsData stats(Player player) {
        return player == null ? null : StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
    }

    // dmz's delta sync: only the changed fields, sent once at the end of the tick however often a spell marks it
    static void sync(ServerPlayer player) {
        StatsDeltaSync.markDirty(player);
    }

    static V v(Vec3 p) {
        return new V(p.x, p.y, p.z);
    }

    /** Takes cost from the caster's ki unless they're in creative. False when there isn't enough. */
    static boolean pay(ServerPlayer caster, StatsData data, float cost) {
        if (caster.isCreative()) return true;
        float ki = data.getResources().getCurrentEnergy();
        if (!SpellMath.affords(ki, cost)) return false;
        if (cost > 0) {
            data.getResources().setCurrentEnergy(ki - cost);
            sync(caster);
        }
        return true;
    }

    /** PvP on and not on the same team without friendly fire: vanilla's rule, which a FakePlayer always refuses. */
    static boolean mayHurt(ServerPlayer caster, Player other) {
        if (other.isCreative() || other.isSpectator() || !caster.server.isPvpAllowed()) return false;
        var team = caster.getTeam();
        return team == null || !team.isAlliedTo(other.getTeam()) || team.isAllowFriendlyFire();
    }

    /** Villagers, armor stands and someone else's pets. */
    static boolean offLimits(LivingEntity e, ServerPlayer caster) {
        if (e instanceof ArmorStand || e instanceof AbstractVillager) return true;
        return e instanceof OwnableEntity pet && pet.getOwnerUUID() != null && !pet.getOwnerUUID().equals(caster.getUUID());
    }

    static void sparkle(ServerLevel level, Entity target, int count) {
        double w = target.getBbWidth() * 0.6, hh = target.getBbHeight() * 0.5;
        level.sendParticles(LILAC, target.getX(), target.getY() + hh, target.getZ(), count, w, hh, w, 0.01);
        if (count >= 4) level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + hh, target.getZ(), count / 4, w, hh, w, 0.02);
    }

    /** An action-bar line from message.dmzzero.&lt;group&gt;.&lt;why&gt;; returns why. */
    static String tell(ServerPlayer player, String group, String why, String english, boolean bright, Object... args) {
        player.displayClientMessage(Component.translatableWithFallback("message.dmzzero." + group + "." + why, english, args)
                .withStyle(bright ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), true);
        return why;
    }
}
