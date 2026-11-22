package com.pockyl.handson_delight.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;

import com.pockyl.handson_delight.client.HandsOnDelightClient;
import com.pockyl.handson_delight.station.CfbOven;
import com.pockyl.handson_delight.station.KitchenCuisine;
import com.pockyl.handson_delight.station.KitchenBoard;
import com.pockyl.handson_delight.station.KitchenBasin;
import com.pockyl.handson_delight.station.KitchenBowl;
import com.pockyl.handson_delight.station.KitchenGrill;
import com.pockyl.handson_delight.station.KitchenKeg;
import com.pockyl.handson_delight.station.KitchenSteamer;
import com.pockyl.handson_delight.station.SteamerTower;
import com.pockyl.handson_delight.station.StoveCooking;

/**
 * Where a scene shows the board, the skillet or the stove, it draws the food itself, so that a knife can cut it, it can fly and
 * turn over; the block's own renderer would draw it a second time in the same place. It is stopped here, at the dispatcher, and
 * not in Farmer's Delight's renderers: another mod can register a renderer of its own for these blocks, and addon stoves built on
 * Farmer's Delight's class bring theirs (End's Delight, My Nether's Delight). A knife carved into the board stays drawn: the scene
 * leaves it alone. A board whose renderer draws the board itself (Chopper's Delight's decorated one) keeps it: only what lies on
 * it is stopped, in that renderer. Farmer's Delight gives the cooking pot no renderer; a mod that draws the pot's body from one (Vanilla
 * Delights' dyed pots) is stopped too, and the pot's scene calls that renderer itself before its soup ({@code PotBody}). Youkai's
 * Feasts' cuisine board's renderer is stopped as well: the scene draws the same models of what lies on it, and so is its steamer's,
 * whose racks the scene lifts apart; its fermentation tank's while its scene shows the block; Extra Delight's mixing bowl's,
 * whose scene draws what lies in it going round with the spoon; and Cooking for Blockheads' oven's, whose door and rack the
 * scene opens. Only while the scene is
 * drawn: a station is kept a little farther away than its scene is drawn.
 */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class StationRenderersMixin {
    /** Since Minecraft 26.x a block entity is drawn from the look the dispatcher reads of it: none read, nothing drawn. */
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void handson$drawnByScene(BlockEntity blockEntity, float partialTicks, ModelFeatureRenderer.CrumblingOverlay breakProgress,
            CallbackInfoReturnable<BlockEntityRenderState> callback) {
        boolean drawnByScene = StoveCooking.isStove(blockEntity) || blockEntity instanceof SkilletBlockEntity
                || blockEntity instanceof CookingPotBlockEntity || KitchenGrill.isGrill(blockEntity)
                || KitchenBasin.isBasin(blockEntity) || blockEntity instanceof KitchenCuisine || blockEntity instanceof KitchenBowl
                || blockEntity instanceof KitchenBoard board && !board.handson$isCarving() && !board.handson$rendersBoard();
        if (drawnByScene && HandsOnDelightClient.isSceneDrawn(blockEntity.getBlockPos())) {
            callback.setReturnValue(null);
            return;
        }
        // A keg or Youkai's Feasts' fermentation tank: while its scene stands in for the block, it draws the liquid and what lies in it.
        if (blockEntity instanceof KitchenKeg && HandsOnDelightClient.isSceneDrawn(blockEntity.getBlockPos())
                && HandsOnDelightClient.isBlockHidden(blockEntity.getBlockPos())) {
            callback.setReturnValue(null);
            return;
        }
        // Cooking for Blockheads' oven: while its scene stands in for the block, it draws the door, the rack and the tools on top. Only
        // then: the oven in the world stays as its own renderer draws it.
        if (CfbOven.isOven(blockEntity) && HandsOnDelightClient.isSceneDrawn(blockEntity.getBlockPos())
                && HandsOnDelightClient.isBlockHidden(blockEntity.getBlockPos())) {
            callback.cancel();
            return;
        }
        // Youkai's Feasts' steamer: every block of the tower is drawn by the scene of the pot under it while somebody works there.
        if (blockEntity instanceof KitchenSteamer && blockEntity.getLevel() != null && HandsOnDelightClient.isSceneDrawn(
                SteamerTower.potUnder(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState()))) {
            callback.setReturnValue(null);
        }
    }
}
