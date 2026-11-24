package com.dmzzero.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

// training copies: who threw an item, even when they're no longer in the world (getOwner looks them up there)
@Mixin(ItemEntity.class)
public interface ItemEntityAccess {
    @Accessor("thrower")
    UUID dmzzero$thrower();
}
