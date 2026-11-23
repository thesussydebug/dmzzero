package com.dmzzero.village;

import com.dmzzero.DMZZero;
import com.dmzzero.ZeroConfig;
import com.dragonminez.common.alignment.AlignmentBand;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// villagers know heroes and villains by dragonminez's alignment: discounts for the good, higher prices, fleeing villagers and angry golems for the evil
@Mod.EventBusSubscriber(modid = DMZZero.MOD_ID)
public final class Villages {

    private Villages() {
    }

    public static VillageMath.Standing standing(Player player) {
        if (!ZeroConfig.SERVER.villages.get() || player == null || player.isCreative() || player.isSpectator()) return VillageMath.Standing.NEITHER;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (data == null || !data.getStatus().isHasCreatedCharacter()) return VillageMath.Standing.NEITHER;
        AlignmentBand band = AlignmentBand.fromValue(data.getResources().getAlignment());
        return VillageMath.standing(band == AlignmentBand.GOOD, band == AlignmentBand.EVIL);
    }

    /** After vanilla works out its own discounts for this customer, heroes get theirs and villains pay more. */
    public static int adjustPrices(AbstractVillager villager, Player player) {
        if (player.level().isClientSide()) return 0;
        VillageMath.Standing s = standing(player);
        if (s == VillageMath.Standing.NEITHER) return 0;
        double off = ZeroConfig.SERVER.heroDiscount.get(), up = ZeroConfig.SERVER.villainMarkup.get();
        int changed = 0;
        for (MerchantOffer offer : villager.getOffers()) {
            int change = VillageMath.priceChange(offer.getBaseCostA().getCount(), off, s, up);
            if (change == 0) continue;
            offer.addToSpecialPriceDiff(change);
            changed++;
        }
        return changed;
    }

    /** Villagers treat this one like a zombie: they run when it comes close. */
    public static boolean scaresVillagers(LivingEntity e) {
        return e instanceof Player p && ZeroConfig.SERVER.villagersFlee.get() && standing(p) == VillageMath.Standing.VILLAIN;
    }

    public static boolean closeEnough(LivingEntity villager, LivingEntity villain) {
        return VillageMath.closeEnoughToFlee(villain.distanceToSqr(villager), ZeroConfig.SERVER.fleeRange.get());
    }

    public static boolean golemTarget(LivingEntity e) {
        return e instanceof Player p && ZeroConfig.SERVER.golemsAttack.get() && standing(p) == VillageMath.Standing.VILLAIN;
    }

    // village golems go after villains; ones a player built answer to their builder, as in vanilla
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof IronGolem golem)) return;
        golem.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(golem, Player.class, 10, true, false,
                e -> !golem.isPlayerCreated() && golemTarget(e)));
    }
}
