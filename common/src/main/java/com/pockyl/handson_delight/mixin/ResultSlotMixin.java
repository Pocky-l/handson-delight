package com.pockyl.handson_delight.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.pockyl.handson_delight.assembly.AssemblyRecipes;

/**
 * What a player's grid leaves when its result is taken is looked up as the result was ({@code CraftingMenuMixin}): with a dish of
 * the same stacks left to the board, the grid leaves what the recipe it made instead leaves ({@code RecipeManagerMixin}).
 */
@Mixin(ResultSlot.class)
abstract class ResultSlotMixin {
    @ModifyVariable(method = "onTake", at = @At("STORE"), ordinal = 0)
    private CraftingInput handson$aPlayersGrid(CraftingInput input, Player player, ItemStack taken) {
        AssemblyRecipes.lookingUp(player, input);
        return input;
    }

    @ModifyVariable(method = "onTake", at = @At("STORE"), ordinal = 0)
    private NonNullList<ItemStack> handson$lookedUp(NonNullList<ItemStack> left) {
        AssemblyRecipes.lookedUp();
        return left;
    }
}
