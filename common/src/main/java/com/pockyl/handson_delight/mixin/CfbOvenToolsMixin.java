package com.pockyl.handson_delight.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.client.HandsOnDelightClient;
import com.pockyl.handson_delight.station.CfbOven;

/**
 * Cooking for Blockheads' oven renderer stands what lies in its tool places tilted over the top and lights it with the light inside
 * its own block. Where the oven's station is on and its scene is drawn, the scene stands them on the burners instead, as a skillet
 * and a pot stand on a stove, so the renderer is told those places are empty; its
 * door and what lies on its rack stay its own. A client without this mod, or with the station off, keeps the oven's look. The
 * renderer reads the tools as it takes the oven's look ({@code extractRenderState}); Loom writes the call's stack type in Fabric's
 * names into the Fabric jar.
 */
@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.client.render.OvenRenderer", remap = false)
abstract class CfbOvenToolsMixin {
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/blay09/mods/cookingforblockheads/block/entity/OvenBlockEntity;getToolItem(I)Lnet/minecraft/world/item/ItemStack;"),
            require = 0)
    private ItemStack handson$toolOnBurner(@Coerce BlockEntity oven, int index) {
        return handson$tool(oven, index);
    }

    private static ItemStack handson$tool(BlockEntity entity, int index) {
        if (HandsOnConfig.oven() && HandsOnDelightClient.isSceneDrawn(entity.getBlockPos())) {
            return ItemStack.EMPTY;
        }
        CfbOven oven = CfbOven.of(entity);
        return oven == null ? ItemStack.EMPTY : oven.tool(index);
    }
}
