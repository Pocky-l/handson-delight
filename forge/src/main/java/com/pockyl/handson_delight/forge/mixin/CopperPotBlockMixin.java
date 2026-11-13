package com.pockyl.handson_delight.forge.mixin;

import com.sammy.minersdelight.content.block.copper_pot.CopperPotBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_delight.station.PotServing;

/** A serving taken from the copper pot with a cup in the hand counts as made, as at Farmer's Delight's pot. */
@Pseudo
@Mixin(CopperPotBlock.class)
abstract class CopperPotBlockMixin {
    @Inject(method = "use", at = @At("HEAD"))
    private void handson$rememberTheMeal(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> callback) {
        PotServing.before(level, pos);
    }

    @Inject(method = "use", at = @At("RETURN"))
    private void handson$countTheServing(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> callback) {
        PotServing.after(level, pos, player);
    }
}
