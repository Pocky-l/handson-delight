package com.pockyl.handson_delight.mixin;

import com.baisylia.culturaldelights.block.custom.VatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.station.KitchenVat;

/**
 * Cultural Delights' vat shows its warmth with flames and smoke, or snowflakes and bubbles, in the middle of its block: hidden in its
 * own solid cube, but floating inside this mod's hollow vat. A vat standing on its bottom shows its warmth on its liquid instead
 * (steam and bubbles over heat, ice over ice), so these stay off for it. The method is named as each loader names it: its NeoForge
 * jar has Mojang's name, its Fabric jar the intermediary one.
 */
@Pseudo
@Mixin(value = VatBlock.class, remap = false)
abstract class VatSparksMixin {
    @Inject(method = {"animateTick", "method_9496"}, at = @At("HEAD"), cancellable = true)
    private void handson$hollow(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (HandsOnConfig.vat() && KitchenVat.isUpright(state)) {
            ci.cancel();
        }
    }
}
