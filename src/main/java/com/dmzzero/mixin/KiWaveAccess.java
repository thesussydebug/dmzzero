package com.dmzzero.mixin;

import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// the synced direction of a fired beam, which dmz only sets from its caster's aim
@Mixin(value = KiWaveEntity.class, remap = false)
public interface KiWaveAccess {
    @Accessor("FIXED_YAW")
    static EntityDataAccessor<Float> dmzzero$fixedYaw() {
        throw new AssertionError();
    }

    @Accessor("FIXED_PITCH")
    static EntityDataAccessor<Float> dmzzero$fixedPitch() {
        throw new AssertionError();
    }
}
