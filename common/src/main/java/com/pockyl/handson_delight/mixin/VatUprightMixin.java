package com.pockyl.handson_delight.mixin;

import com.baisylia.culturaldelights.block.custom.VatBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_delight.HandsOnConfig;

/**
 * Cultural Delights places its vat like a barrel, its mouth towards the player, so a vat placed looking ahead lies on its side and
 * never opens as a station. With the vat station on, a new vat always stands on its bottom, the one way it opens as a station.
 * Vats already placed keep their way and their own menu. The method is named as each loader names it: its NeoForge jar has
 * Mojang's name, its Fabric jar the intermediary one.
 */
@Pseudo
@Mixin(value = VatBlock.class, remap = false)
abstract class VatUprightMixin {
    @Inject(method = {"getStateForPlacement", "method_9605"}, at = @At("RETURN"), cancellable = true)
    private void handson$upright(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (HandsOnConfig.vat() && state != null && state.hasProperty(BlockStateProperties.FACING)) {
            cir.setReturnValue(state.setValue(BlockStateProperties.FACING, Direction.UP));
        }
    }
}
