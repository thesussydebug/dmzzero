package com.dmzzero.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

// catching an arrow: whether it's stuck in the ground, and the item it would give back
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccess {
    @Accessor("inGround")
    boolean dmzzero$inGround();

    @Invoker("getPickupItem")
    ItemStack dmzzero$pickupItem();
}
