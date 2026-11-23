package com.dmzzero.chamber;

import com.dmzzero.ZeroConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

// goku and gohan stayed transformed all day in the Time Chamber until super saiyan felt like base form. dmz's passive form
// mastery, the award it gives every five seconds you stay transformed, is bigger in there (TickHandlerMasteryMixin)
public final class ChamberMastery {

    private ChamberMastery() {
    }

    /** DMZ's passive mastery award for this entity, after DMZ's own effects. */
    public static double passive(LivingEntity entity, double amount) {
        if (!(entity instanceof Player p) || p.level().isClientSide()) return amount;
        return ChamberMath.passiveMastery(amount, ChamberDepths.inChamber(p), ZeroConfig.SERVER.chamberMastery.get());
    }
}
