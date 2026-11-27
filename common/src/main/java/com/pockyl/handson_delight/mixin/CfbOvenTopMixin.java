package com.pockyl.handson_delight.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.station.CfbOven;

/**
 * The burners on top of Cooking for Blockheads' oven, where its station is on: cookware stands on them as on a stove. Its own use
 * stands only the tools of its kitchen there (knives, Pam's cookware); Farmer's Delight's skillet and cooking pot clicked on a free
 * burner now stand on it too, and an empty hand on a burner takes back what stands there. Everything else is
 * its own use (its tools, its menu, its door held open). A held thing goes through its {@code useItemOn}, an empty hand through its
 * {@code useWithoutItem}.
 */
@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.block.OvenBlock", remap = false)
abstract class CfbOvenTopMixin {
    /** A skillet or a pot onto a free burner. */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, require = 0)
    private void handson$burner(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> result) {
        CfbOven oven = handson$oven(level, pos, hit);
        if (oven == null || !CfbOven.standsOnBurner(held)) {
            return;
        }
        int burner = CfbOven.burnerAt(state, pos, hit.getLocation());
        if (oven.tool(burner).isEmpty()) {
            if (!level.isClientSide()) {
                oven.setTool(burner, held.split(1));
            }
            result.setReturnValue(InteractionResult.SUCCESS);
        }
    }

    /** An empty hand on a burner takes back what stands there. */
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void handson$takeBack(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> result) {
        CfbOven oven = handson$oven(level, pos, hit);
        if (oven == null || player.isSecondaryUseActive()) {
            return;
        }
        int burner = CfbOven.burnerAt(state, pos, hit.getLocation());
        ItemStack there = oven.tool(burner);
        if (!there.isEmpty() && player.getMainHandItem().isEmpty()) {
            if (!level.isClientSide()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, there.copy());
                oven.setTool(burner, ItemStack.EMPTY);
            }
            result.setReturnValue(InteractionResult.SUCCESS);
        }
    }

    private static CfbOven handson$oven(Level level, BlockPos pos, BlockHitResult hit) {
        if (!HandsOnConfig.oven() || hit.getDirection() != Direction.UP) {
            return null;
        }
        return CfbOven.of(level.getBlockEntity(pos));
    }
}
