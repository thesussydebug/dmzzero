package com.dmzzero.mixin;

import com.dragonminez.common.init.MainGameRules;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// the attack a terrain carve was made for: the gate dmz checks each block against keeps it, and dmz's carve job keeps only the gate
@Mixin(value = MainGameRules.KiGriefGate.class, remap = false)
public interface KiGriefGateAccess {
    @Accessor("source")
    Entity dmzzero$source();
}
