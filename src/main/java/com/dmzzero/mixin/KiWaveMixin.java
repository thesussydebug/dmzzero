package com.dmzzero.mixin;

import com.dmzzero.beam.CombinedBeams;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// combined beams: a joined beam's length is held where it meets the leader, so it grows, hits, breaks blocks
// and finally explodes no farther than that
@Mixin(KiWaveEntity.class)
public abstract class KiWaveMixin {

    @ModifyVariable(method = "tick", at = @At("STORE"), name = "targetLen")
    private float dmzzero$heldAtMeetingPoint(float length) {
        return CombinedBeams.capLength((KiWaveEntity) (Object) this, length);
    }
}
