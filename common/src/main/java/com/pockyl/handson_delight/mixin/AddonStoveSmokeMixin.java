package com.pockyl.handson_delight.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.client.HandsOnDelightClient;

/**
 * The stoves of End's Delight and My Nether's Delight are stations too, and puff the same dark smoke over their food as Farmer's
 * Delight's, from a method of their own that {@link StoveSmokeMixin} does not reach. Quiet where the scene shows the grill, as there.
 * Applied only with the addon installed ({@code OptionalMixins}).
 */
@Pseudo
@Mixin(targets = {"cn.foggyhillside.ends_delight.block.entity.EndStoveBlockEntity",
        "com.soytutta.mynethersdelight.common.block.entity.NetherStoveBlockEntity"}, remap = false)
abstract class AddonStoveSmokeMixin {
    @Inject(method = "addSmokeParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void handson$quietWhileShown(CallbackInfo callback) {
        if (HandsOnDelightClient.isShown(((BlockEntity) (Object) this).getBlockPos())) {
            callback.cancel();
        }
    }
}
