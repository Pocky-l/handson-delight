package com.pockyl.handson_delight.neoforge.mixin;

import com.snissac.sabletrees.block.ChoppingBlockEntity;
import com.snissac.sabletrees.client.ChoppingBlockRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.pockyl.handson_delight.client.HandsOnDelightClient;

/**
 * Sable Trees' stump renderer draws its dirt and what lies on it. While the station's scene is drawn, the scene draws what lies there
 * (the food it cuts, the log it splits), so the renderer sees an empty stump; the dirt stays, as it shows how much more food the stump
 * takes.
 */
@Pseudo
@Mixin(value = ChoppingBlockRenderer.class, remap = false)
abstract class StumpContentMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/snissac/sabletrees/block/ChoppingBlockEntity;getLog()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack handson$drawnByScene(ChoppingBlockEntity stump) {
        return HandsOnDelightClient.isSceneDrawn(((BlockEntity) stump).getBlockPos()) ? ItemStack.EMPTY : stump.getLog();
    }
}
