package com.dmzzero.sense;

import com.dragonminez.common.init.entities.IBattlePower;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

// a creature's battle power and aura color. dmz's LivingEntityMixin gives every living entity IBattlePower, vanilla mobs
// included; DBSagasEntity is checked too in case that mixin ever goes
public final class CreaturePower {

    private CreaturePower() {
    }

    public static double of(LivingEntity e) {
        if (e instanceof Player p) {
            StatsData d = StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
            return d == null ? 0 : d.getBattlePower();
        }
        if (e instanceof IBattlePower bp) return bp.getBattlePower();
        if (e instanceof DBSagasEntity saga) return saga.getBattlePower();
        return 0;
    }

    public static int color(LivingEntity e) {
        if (e instanceof DBSagasEntity saga && saga.getAuraColor() != 0) return saga.getAuraColor() & 0xFFFFFF;
        return 0xFFFFFF;
    }

    /** Androids and machines give off no ki. */
    public static boolean hidden(LivingEntity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id != null && CreatureKi.hidden(id.getNamespace(), id.getPath());
    }
}
