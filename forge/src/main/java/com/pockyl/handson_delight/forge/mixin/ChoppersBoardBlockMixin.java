package com.pockyl.handson_delight.forge.mixin;

import com.phantomwing.choppersdelight.block.custom.ModCuttingBoardBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.station.BoardLayers;

/** A broken board of Chopper's Delight drops the dish being put together on it, as Farmer's Delight's board does. */
@Pseudo
@Mixin(ModCuttingBoardBlock.class)
abstract class ChoppersBoardBlockMixin {
    @Inject(method = "onRemove", at = @At("HEAD"))
    private void handson$dropLayers(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved, CallbackInfo callback) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BoardLayers board) {
            Containers.dropContents(level, pos, board.handson$layers());
            board.handson$layers().clear();
        }
    }
}
