package com.dmzzero.mixin;

import com.dmzzero.diet.Diets;
import com.dmzzero.diet.Senzu;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// racial diets: namekians get half from food, majins double from sweets. a senzu bean keeps the hunger bar full
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    @Shadow
    private int foodLevel;
    @Shadow
    private float saturationLevel;
    @Shadow
    private float exhaustionLevel;

    // food only ticks on the server. saturation may still run down; the bar itself can't drop
    @Inject(method = "tick", at = @At("HEAD"))
    private void dmzzero$senzu(Player player, CallbackInfo ci) {
        if (!Senzu.fed(player)) return;
        if (foodLevel < 20) foodLevel = 20;
        if (exhaustionLevel > 4.0f && saturationLevel <= 0) exhaustionLevel = 0;
    }

    // forge's version that knows who is eating; vanilla's two-argument eat(Item, ItemStack) forwards here
    @Inject(method = "eat(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void dmzzero$diet(Item item, ItemStack stack, LivingEntity entity, CallbackInfo ci) {
        if (Diets.onEat((FoodData) (Object) this, item, stack, entity)) ci.cancel();
    }

    // a placed cake feeds through the plain numbers
    @Inject(method = "eat(IF)V", at = @At("HEAD"), cancellable = true)
    private void dmzzero$cake(int hunger, float saturationModifier, CallbackInfo ci) {
        if (Diets.onEatRaw((FoodData) (Object) this, hunger, saturationModifier)) ci.cancel();
    }
}
