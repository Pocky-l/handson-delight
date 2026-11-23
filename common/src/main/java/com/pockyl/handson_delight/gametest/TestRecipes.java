package com.pockyl.handson_delight.gametest;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import vectorwing.farmersdelight.common.crafting.CookingPotBookCategory;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.crafting.ingredient.ChanceResult;

import com.pockyl.handson_delight.KitchenRecipes;
import com.pockyl.handson_delight.mixin.RecipeManagerAccessor;
import com.pockyl.handson_delight.mixin.RecipeMapAccessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Recipes the game tests make and lay into the server's recipes, as the constructors of 1.21.1 made them (stacks for results). Since
 * Minecraft 1.21.2 the recipes are loaded as a registry and a manager's are fixed once it is made: a test sets new ones in its place.
 */
final class TestRecipes {
    private TestRecipes() {
    }

    static ResourceKey<Recipe<?>> key(Identifier id) {
        return ResourceKey.create(Registries.RECIPE, id);
    }

    /** A cooking pot recipe; {@code container} empty for none of its own. */
    static CookingPotRecipe cooking(CookingPotBookCategory category, List<Ingredient> inputs, ItemStack result, ItemStack container,
            float experience, int cookTime) {
        return new CookingPotRecipe("", category, List.copyOf(inputs), ItemStackTemplate.fromNonEmptyStack(result),
                container.isEmpty() ? Optional.empty() : Optional.of(ItemStackTemplate.fromNonEmptyStack(container)), experience, cookTime);
    }

    /** A cutting recipe giving each of {@code results} for sure, with Farmer's Delight's own sound. */
    static CuttingBoardRecipe cutting(Ingredient input, Ingredient tool, ItemStack... results) {
        List<ChanceResult> chances = Arrays.stream(results).map(stack -> new ChanceResult(ItemStackTemplate.fromNonEmptyStack(stack), 1.0F))
                .toList();
        return new CuttingBoardRecipe("", input, tool, chances, null);
    }

    /** A shapeless craft of the crafting table's. */
    static ShapelessRecipe shapeless(ItemStack result, Ingredient... ingredients) {
        return new ShapelessRecipe(new Recipe.CommonInfo(true), new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                ItemStackTemplate.fromNonEmptyStack(result), List.of(ingredients));
    }

    /** The server's recipes with {@code recipe} under {@code id}, in place of one of that id. */
    static void add(ServerLevel level, Identifier id, Recipe<?> recipe) {
        List<RecipeHolder<?>> all = new ArrayList<>(level.recipeAccess().getRecipes());
        all.removeIf(holder -> holder.id().identifier().equals(id));
        all.add(new RecipeHolder<>(key(id), recipe));
        replace(level.recipeAccess(), all);
    }

    /** {@code manager}'s recipes replaced by {@code recipes}, and what was worked out from the old ones forgotten. */
    static void replace(RecipeManager manager, Collection<RecipeHolder<?>> recipes) {
        ImmutableMultimap.Builder<RecipeType<?>, RecipeHolder<?>> byType = ImmutableMultimap.builder();
        ImmutableMap.Builder<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey = ImmutableMap.builder();
        for (RecipeHolder<?> holder : recipes) {
            byType.put(holder.value().getType(), holder);
            byKey.put(holder.id(), holder);
        }
        ((RecipeManagerAccessor) manager).handson$setRecipes(RecipeMapAccessor.handson$create(byType.build(), byKey.build()));
        KitchenRecipes.forget(manager);
    }

    /**
     * {@code check} run while {@code manager} holds {@code recipes} alone, in that order; its own come back after, untouched. The
     * board chooses its dishes from the server's recipes when first asked: they must be chosen before ({@code AssemblyRecipes}).
     */
    static void whileHolding(RecipeManager manager, Collection<RecipeHolder<?>> recipes, Runnable check) {
        RecipeMap own = ((RecipeManagerAccessor) manager).handson$recipes();
        replace(manager, recipes);
        try {
            check.run();
        } finally {
            ((RecipeManagerAccessor) manager).handson$setRecipes(own);
            KitchenRecipes.forget(manager);
        }
    }
}
