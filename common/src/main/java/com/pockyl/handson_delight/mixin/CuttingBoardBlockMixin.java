package com.pockyl.handson_delight.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.CuttingBoardBlock;

import com.pockyl.handson_delight.station.BoardLayers;

/** A broken board drops the dish being put together on it, as it drops what lies there to be cut. */
@Mixin(CuttingBoardBlock.class)
abstract class CuttingBoardBlockMixin {
    @Inject(method = "onRemove", at = @At("HEAD"))
    private void handson$dropLayers(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved, CallbackInfo callback) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BoardLayers board) {
            Containers.dropContents(level, pos, board.handson$layers());
            board.handson$layers().clear();
        }
    }
}
