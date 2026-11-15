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
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;

import com.pockyl.handson_delight.client.HandsOnDelightClient;
import com.pockyl.handson_delight.station.KitchenCuisine;
import com.pockyl.handson_delight.station.KitchenBoard;
import com.pockyl.handson_delight.station.KitchenBasin;
import com.pockyl.handson_delight.station.KitchenGrill;

/**
 * Where a scene shows the board, the skillet or the stove, it draws the food itself, so that a knife can cut it, it can fly and
 * turn over; the block's own renderer would draw it a second time in the same place. It is stopped here, at the dispatcher, and
 * not in Farmer's Delight's renderers: another mod can register a renderer of its own for these blocks, and addon stoves built on
 * Farmer's Delight's class bring theirs (End's Delight, My Nether's Delight). A knife carved into the board stays drawn: the scene
 * leaves it alone. A board whose renderer draws the board itself (Chopper's Delight's decorated one) keeps it: only what lies on
 * it is stopped, in that renderer. Farmer's Delight gives the cooking pot no renderer; a mod that draws the pot's body from one (Vanilla
 * Delights' dyed pots) is stopped too, and the pot's scene calls that renderer itself before its soup ({@code PotBody}). Youkai's
 * Feasts' cuisine board's renderer is stopped as well: the scene draws the same models of what lies on it. Only while the scene is
 * drawn: a station is kept a little farther away than its scene is drawn.
 */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class StationRenderersMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void handson$drawnByScene(BlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            CallbackInfo callback) {
        boolean drawnByScene = blockEntity instanceof AbstractStoveBlockEntity || blockEntity instanceof SkilletBlockEntity
                || blockEntity instanceof CookingPotBlockEntity || KitchenGrill.isGrill(blockEntity)
                || KitchenBasin.isBasin(blockEntity) || blockEntity instanceof KitchenCuisine
                || blockEntity instanceof KitchenBoard board && !board.handson$isCarving() && !board.handson$rendersBoard();
        if (drawnByScene && HandsOnDelightClient.isSceneDrawn(blockEntity.getBlockPos())) {
            callback.cancel();
        }
    }
}
