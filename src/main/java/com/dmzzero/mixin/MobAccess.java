package com.dmzzero.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// training copies: whether vanilla removes this mob on its first tick on peaceful
@Mixin(Mob.class)
public interface MobAccess {
    @Invoker("shouldDespawnInPeaceful")
    boolean dmzzero$despawnsInPeaceful();
}
