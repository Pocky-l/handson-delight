package com.pockyl.handson_delight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.client.renderer.CuttingBoardRenderer;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;

import com.pockyl.handson_delight.client.HandsOnDelightClient;

/**
 * Where a scene shows the board, it draws what lies on it itself, so that a knife can cut it into pieces; Farmer's Delight's
 * drawing would lie in the same place. A knife carved into the board stays Farmer's Delight's. Its typed {@code render} keeps its
 * name in every build, hence no remapping.
 */
@Mixin(value = CuttingBoardRenderer.class, remap = false)
abstract class CuttingBoardRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void handson$drawnByScene(CuttingBoardBlockEntity board, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay, CallbackInfo callback) {
        if (!board.isItemCarvingBoard() && HandsOnDelightClient.isShown(board.getBlockPos())) {
            callback.cancel();
        }
    }
}
