package com.pockyl.handson_delight.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.client.HandsOnDelightClient;

/**
 * Barbeque's Delight's grill puffs dark smoke over each skewer and steam over the grill; where the grill's scene shows it, its own
 * steam and smoke tell how each skewer is doing, and the grill's puffs would come a second time beside them.
 */
@Pseudo
@Mixin(targets = "com.mao.barbequesdelight.content.block.GrillBlockEntity", remap = false)
abstract class GrillSmokeMixin {
    @Inject(method = "addParticles", at = @At("HEAD"), cancellable = true)
    private void handson$quietWhileShown(CallbackInfo callback) {
        if (HandsOnDelightClient.isShown(((BlockEntity) (Object) this).getBlockPos())) {
            callback.cancel();
        }
    }
}
