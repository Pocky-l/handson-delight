package com.pockyl.handson_delight.mixin;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_delight.assembly.AssemblyRecipes;

/**
 * A dish is put together on the cutting board, by hand: a player's crafting table and inventory grid do not make it
 * ({@link AssemblyRecipes#notInThisGrid}). Recipes of other types (Farmer's Delight's dough from water) are kept out where the recipe
 * manager looks for one ({@code RecipeManagerMixin}). Shaped recipes: {@code ShapedRecipeMixin} (Fabric names the two methods apart).
 */
@Mixin(ShapelessRecipe.class)
abstract class ShapelessRecipeMixin {
    @Inject(method = "matches(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"),
            cancellable = true)
    private void handson$onTheBoardInstead(CraftingContainer grid, Level level, CallbackInfoReturnable<Boolean> callback) {
        if (AssemblyRecipes.notInThisGrid((Recipe<?>) (Object) this, grid)) {
            callback.setReturnValue(false);
        }
    }
}
