package com.dmzzero.mixin;

import com.dmzzero.chamber.ChamberWish;
import com.dmzzero.heal.HealWish;
import com.dmzzero.pattern.PatternWish;
import com.dragonminez.common.wish.DragonWishRegistry;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

// once dmz has loaded every dragon's wishes (server start, /reload), dmz zero's wishes join Shenron's and Porunga's lists
@Mixin(value = DragonWishRegistry.class, remap = false)
public abstract class DragonWishRegistryMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("TAIL"), require = 0)
    private void dmzzero$healWish(Map<?, ?> map, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        HealWish.addTo();
        ChamberWish.addTo();
        PatternWish.addTo();
    }
}
