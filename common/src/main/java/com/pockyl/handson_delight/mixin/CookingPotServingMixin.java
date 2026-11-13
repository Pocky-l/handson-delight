package com.pockyl.handson_delight.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.CookingPotBlock;

import com.pockyl.handson_delight.station.PotServing;

/** A serving taken with a bowl in the hand, without opening the pot, counts as made: {@link PotServing}. */
@Mixin(CookingPotBlock.class)
abstract class CookingPotServingMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void handson$rememberTheMeal(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> callback) {
        PotServing.before(level, pos);
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void handson$countTheServing(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> callback) {
        PotServing.after(level, pos, player);
    }
}
