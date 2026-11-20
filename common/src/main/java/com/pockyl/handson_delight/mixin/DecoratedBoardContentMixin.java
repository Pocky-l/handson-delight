package com.pockyl.handson_delight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.phantomwing.choppersdelight.block.entity.DecoratedCuttingBoardBlockEntity;
import com.phantomwing.choppersdelight.renderer.DecoratedCuttingBoardRenderer;
import com.pockyl.diegesis.api.client.SceneBuffers;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.client.HandsOnDelightClient;

/**
 * Chopper's Delight's decorated board has no block model: its renderer draws the board, its banner pattern and what lies on it.
 * While the station's scene is drawn, the scene draws the food, so only that part is stopped here and the board stays in sight. A
 * tool stuck in stays drawn, as on Farmer's Delight's board. What the board inherits from Minecraft is called through Minecraft's
 * own type, so that the Fabric jar names it as Fabric does.
 */
@Pseudo
@Mixin(value = DecoratedCuttingBoardRenderer.class, remap = false)
abstract class DecoratedBoardContentMixin {
    @Inject(method = "renderBoardContent", at = @At("HEAD"), cancellable = true)
    private void handson$drawnByScene(DecoratedCuttingBoardBlockEntity board, float partialTick, PoseStack poseStack,
            SceneBuffers buffers, int light, int overlay, CallbackInfo callback) {
        if (!board.isItemCarvingBoard() && HandsOnDelightClient.isSceneDrawn(((BlockEntity) board).getBlockPos())) {
            callback.cancel();
        }
    }
}
