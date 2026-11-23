package com.pockyl.handson_delight.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.pockyl.handson_delight.assembly.AssemblyRecipes;

import java.util.Optional;

/**
 * A sandwich is put together on the cutting board, by hand: a player's crafting table and inventory grid do not make it (both go
 * through this method). The grid is marked for the lookup ({@link AssemblyRecipes#lookingUp}): the recipe manager then gives what
 * else the grid makes, and lists of every match leave the dish out, Polymorph's own lookup in this method included.
 */
@Mixin(CraftingMenu.class)
abstract class CraftingMenuMixin {
    @ModifyVariable(method = "slotChangedCraftingGrid", at = @At("STORE"), ordinal = 0)
    private static CraftingInput handson$aPlayersGrid(CraftingInput input, AbstractContainerMenu menu, Level level, Player player,
            CraftingContainer grid, ResultContainer result, RecipeHolder<CraftingRecipe> last) {
        AssemblyRecipes.lookingUp(player, input);
        return input;
    }

    /** Looked up: a dish that still came (another mod's lookup in the manager's place) gives way to what else the grid makes. */
    @ModifyVariable(method = "slotChangedCraftingGrid", at = @At("STORE"), ordinal = 0)
    private static Optional<RecipeHolder<CraftingRecipe>> handson$onTheBoardInstead(Optional<RecipeHolder<CraftingRecipe>> found,
            AbstractContainerMenu menu, Level level, Player player, CraftingContainer grid, ResultContainer result,
            RecipeHolder<CraftingRecipe> last) {
        return AssemblyRecipes.lookedUp(found, RecipeType.CRAFTING, level);
    }
}
