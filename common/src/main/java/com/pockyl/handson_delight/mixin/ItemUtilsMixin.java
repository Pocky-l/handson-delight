package com.pockyl.handson_delight.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.utility.ItemUtils;

import com.pockyl.handson_delight.station.CaughtDrops;

/**
 * Farmer's Delight throws what its board, skillet and stove make into the world, where a cook whose camera is at the station would
 * have to walk after it. While a station lets Farmer's Delight's code make it, the items are caught ({@code CaughtDrops}) and the
 * station hands them to the cook or lays them where the cook looks.
 */
@Mixin(value = ItemUtils.class, remap = false)
abstract class ItemUtilsMixin {
    @Inject(method = "spawnItemEntity", at = @At("HEAD"), cancellable = true)
    private static void handson$catch(Level level, ItemStack stack, double x, double y, double z, double dx, double dy, double dz,
            CallbackInfo callback) {
        if (CaughtDrops.take(stack, x, y, z, dx, dy, dz)) {
            callback.cancel();
        }
    }
}
