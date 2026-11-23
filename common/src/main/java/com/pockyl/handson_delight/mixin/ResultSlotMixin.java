package com.pockyl.handson_delight.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_delight.assembly.AssemblyRecipes;

/**
 * What a player's grid leaves when its result is taken is looked up as the result was ({@code CraftingMenuMixin}): with a dish of the
 * same stacks left to the board, the grid leaves what the recipe it made instead leaves ({@link AssemblyRecipes#leftInTheGrid}).
 */
@Mixin(ResultSlot.class)
abstract class ResultSlotMixin {
    @Shadow
    @Final
    private Player player;

    @Inject(method = "getRemainingItems", at = @At("HEAD"), cancellable = true)
    private void handson$leftByTheRecipeItself(CraftingInput input, Level level, CallbackInfoReturnable<NonNullList<ItemStack>> callback) {
        if (level instanceof ServerLevel server) {
            AssemblyRecipes.leftInTheGrid(player, input, server).ifPresent(callback::setReturnValue);
        }
    }
}
