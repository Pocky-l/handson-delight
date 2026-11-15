package com.pockyl.handson_delight.neoforge.mixin;

import com.farmersrespite.common.block.entity.KettleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.HandsOnConfig;

/**
 * The kettle's own steam and sound: the station's kettle steams from its spout, rattles and whistles for every client near it.
 * Farmer's Respite's port boils with a loop of its own and makes a potion's swirls at the spout; with the station on, the station's
 * take their place.
 */
@Pseudo
@Mixin(value = KettleBlockEntity.class, remap = false)
abstract class KettleQuietMixin {
    @Inject(method = "animationTick", at = @At("HEAD"), cancellable = true)
    private static void handson$stationSteams(Level level, BlockPos pos, BlockState state, KettleBlockEntity kettle, CallbackInfo callback) {
        if (HandsOnConfig.kettle()) {
            callback.cancel();
        }
    }
}
