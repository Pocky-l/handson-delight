package com.pockyl.handson_delight.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The recipe's result and ingredients, which it no longer hands out since Minecraft 1.21.2: for {@code RecipeParts}. */
@Mixin(ShapelessRecipe.class)
public interface ShapelessRecipeAccessor {
    @Accessor("result")
    ItemStack handson$result();

    @Accessor("ingredients")
    List<Ingredient> handson$ingredients();
}
