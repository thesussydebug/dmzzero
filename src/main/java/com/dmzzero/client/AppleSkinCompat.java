package com.dmzzero.client;

import com.dmzzero.diet.DietMath;
import com.dmzzero.diet.Diets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import squeek.appleskin.api.event.FoodValuesEvent;
import squeek.appleskin.api.food.FoodValues;

// appleskin's tooltips and hunger-bar preview show what the player's diet really gives.
// Only registered when appleskin is loaded, so nothing here is touched without it.
public final class AppleSkinCompat {

    private AppleSkinCompat() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(AppleSkinCompat.class);
    }

    @SubscribeEvent
    public static void onFoodValues(FoodValuesEvent event) {
        if (event.player == null || event.modifiedFoodValues == null || !DietClient.active()) return;
        FoodValues values = event.modifiedFoodValues;
        DietMath.Food food = Diets.adjustedFor(event.player, event.itemStack, values.hunger, values.saturationModifier);
        if (food == null) return;
        event.modifiedFoodValues = new FoodValues(food.hunger(), food.saturationModifier());
    }
}
