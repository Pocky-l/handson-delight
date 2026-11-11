package com.pockyl.handson_delight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;

import com.pockyl.handson_delight.client.HandsOnDelightClient;

/**
 * Where a scene shows the board, the skillet or the stove, it draws the food itself, so that a knife can cut it, it can fly and
 * turn over; the block's own renderer would draw it a second time in the same place. It is stopped here, at the dispatcher, and
 * not in Farmer's Delight's renderers: another mod can register a renderer of its own for these blocks, and addon stoves built on
 * Farmer's Delight's class bring theirs (End's Delight, My Nether's Delight). A knife carved into the board stays drawn: the scene
 * leaves it alone. The cooking pot is not here: Farmer's Delight gives it no renderer, and a mod that draws the pot itself from
 * one (Vanilla Delights' dyed pots) draws the pot's body, which has to stay.
 */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class StationRenderersMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void handson$drawnByScene(BlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            CallbackInfo callback) {
        boolean drawnByScene = blockEntity instanceof AbstractStoveBlockEntity || blockEntity instanceof SkilletBlockEntity
                || blockEntity instanceof CuttingBoardBlockEntity board && !board.isItemCarvingBoard();
        if (drawnByScene && HandsOnDelightClient.isShown(blockEntity.getBlockPos())) {
            callback.cancel();
        }
    }
}
