package com.pockyl.handson_delight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.client.renderer.DefaultStoveRenderer;
import vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity;

import com.pockyl.handson_delight.client.HandsOnDelightClient;

/** Where the stove's scene shows the food on its grill, Farmer's Delight does not draw it a second time. */
@Mixin(value = DefaultStoveRenderer.class, remap = false)
abstract class StoveRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void handson$drawnByScene(AbstractStoveBlockEntity stove, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay, CallbackInfo callback) {
        if (HandsOnDelightClient.isShown(stove.getBlockPos())) {
            callback.cancel();
        }
    }
}
