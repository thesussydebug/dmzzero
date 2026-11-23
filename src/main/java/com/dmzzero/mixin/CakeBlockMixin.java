package com.dmzzero.mixin;

import com.dmzzero.diet.Diets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// a placed cake (candle cakes too) feeds through FoodData.eat(int, float): remember who is eating for the diets
@Mixin(CakeBlock.class)
public abstract class CakeBlockMixin {

    @Inject(method = "eat", at = @At("HEAD"))
    private static void dmzzero$start(LevelAccessor level, BlockPos pos, BlockState state, Player player,
                                      CallbackInfoReturnable<InteractionResult> cir) {
        Diets.startCake(player);
    }

    @Inject(method = "eat", at = @At("RETURN"))
    private static void dmzzero$end(LevelAccessor level, BlockPos pos, BlockState state, Player player,
                                    CallbackInfoReturnable<InteractionResult> cir) {
        Diets.endCake();
    }
}
