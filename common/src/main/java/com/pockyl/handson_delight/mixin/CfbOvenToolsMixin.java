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
 * renderer's {@code render} is named per loader in the jar it comes in: Mojang's name in a Forge development run, the SRG name in
 * Forge, the intermediary name (with intermediary classes) on Fabric; the injection that does not fit the running game is left out.
 */
@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.client.render.OvenRenderer", remap = false)
abstract class CfbOvenToolsMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/blay09/mods/cookingforblockheads/block/entity/OvenBlockEntity;getToolItem(I)Lnet/minecraft/world/item/ItemStack;"),
            require = 0)
    private ItemStack handson$toolOnBurner(@Coerce BlockEntity oven, int index) {
        return handson$tool(oven, index);
    }

    @Redirect(method = "method_3569", at = @At(value = "INVOKE",
            target = "Lnet/blay09/mods/cookingforblockheads/block/entity/OvenBlockEntity;getToolItem(I)Lnet/minecraft/class_1799;"),
            require = 0)
    private ItemStack handson$toolOnBurnerFabric(@Coerce BlockEntity oven, int index) {
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
