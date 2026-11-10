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

import com.pockyl.handson_delight.client.HandsOnDelightClient;

/**
 * Where the stove's scene shows the food on its grill, the stove's own renderer does not draw it a second time: Farmer's Delight's, and
 * those of addon stoves built on its class with renderers of their own (End's Delight, My Nether's Delight).
 */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class StoveRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void handson$drawnByScene(BlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            CallbackInfo callback) {
        if (blockEntity instanceof AbstractStoveBlockEntity stove && HandsOnDelightClient.isShown(stove.getBlockPos())) {
            callback.cancel();
        }
    }
}
