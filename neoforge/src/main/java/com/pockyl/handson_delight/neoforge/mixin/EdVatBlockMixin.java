package com.pockyl.handson_delight.neoforge.mixin;

import com.lance5057.extradelight.workstations.vat.VatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.station.KitchenEdVat;

/** Broken, Extra Delight's vat drops its servings and the lid set aside, as it drops everything else in it. */
@Pseudo
@Mixin(value = VatBlock.class, remap = false)
abstract class EdVatBlockMixin {
    @Inject(method = "onRemove", at = @At("HEAD"))
    private void handson$spill(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving, CallbackInfo ci) {
        if (state.is(newState.getBlock()) || !(level.getBlockEntity(pos) instanceof KitchenEdVat vat)) {
            return;
        }
        for (ItemStack kept : new ItemStack[] {vat.handson$servings(), vat.handson$lidAside()}) {
            if (!kept.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), kept.copy());
            }
        }
        vat.handson$setServings(ItemStack.EMPTY);
        vat.handson$setLidAside(ItemStack.EMPTY);
    }
}
