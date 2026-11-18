package com.pockyl.handson_delight.mixin;

import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/** A recipe map of given recipes, which the game makes only from a registry: for the game tests ({@code TestRecipes}). */
@Mixin(RecipeMap.class)
public interface RecipeMapAccessor {
    @Invoker("<init>")
    static RecipeMap handson$create(Multimap<RecipeType<?>, RecipeHolder<?>> byType, Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey) {
        throw new AssertionError();
    }
}
