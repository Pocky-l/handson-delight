package com.pockyl.handson_delight.mixin;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_delight.assembly.AssemblyRecipes;

/** A shaped dish, a pie in its crust, is put together on the cutting board: as {@code ShapelessRecipeMixin}. */
@Mixin(ShapedRecipe.class)
abstract class ShapedRecipeMixin {
    @Inject(method = "matches(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"),
            cancellable = true)
    private void handson$onTheBoardInstead(CraftingContainer grid, Level level, CallbackInfoReturnable<Boolean> callback) {
        if (AssemblyRecipes.notInThisGrid((Recipe<?>) (Object) this, grid)) {
            callback.setReturnValue(false);
        }
    }
}
