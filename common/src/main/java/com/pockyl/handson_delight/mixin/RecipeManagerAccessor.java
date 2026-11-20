package com.pockyl.handson_delight.mixin;

import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The recipes a manager holds, which the game tests replace to cook recipes of their own ({@code TestRecipes}). */
@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    @Mutable
    @Accessor("recipes")
    void handson$setRecipes(RecipeMap recipes);
}
