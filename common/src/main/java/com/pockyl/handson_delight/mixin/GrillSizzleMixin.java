package com.pockyl.handson_delight.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.client.HandsOnDelightClient;

/**
 * Barbeque's Delight's grill sizzles while anything lies on its fire; where the grill's scene shows it, the scene sizzles by how each
 * skewer is doing (quiet while it cooks, sharper while it waits to be turned), and the grill's own would sound over it.
 */
@Pseudo
@Mixin(targets = "com.mao.barbequesdelight.content.block.GrillBlock", remap = false)
abstract class GrillSizzleMixin {
    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void handson$quietWhileShown(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo callback) {
        if (HandsOnDelightClient.isShown(pos)) {
            callback.cancel();
        }
    }
}
