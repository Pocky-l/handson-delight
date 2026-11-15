package com.pockyl.handson_delight.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link StationRecipes} for the pots, which read Farmer's Delight's cooking recipes: the food in a pot as kinds and counts, the dish
 * it makes, laid out the way Farmer's Delight reads it.
 */
public final class PotRecipes {
    private PotRecipes() {
    }

    /**
     * A recipe the food makes: in how many slots every kind lies, and whether every kind fits its slots (with too much of a kind the
     * rest goes back to the cook).
     */
    public record Choice(RecipeHolder<CookingPotRecipe> recipe, int[] parts, boolean fits) {
    }

    /** The recipe's ingredients, without gaps: one slot each. */
    public static List<Ingredient> ingredients(CookingPotRecipe recipe) {
        return recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
    }

    /** The food in the pot's ingredient slots as kinds: alike stacks counted together, in the order of the slots. */
    public static List<ItemStack> kinds(KitchenPot pot) {
        List<ItemStack> kinds = new ArrayList<>();
        for (int i = 0; i < pot.handson$slots(); i++) {
            add(kinds, pot.handson$getItem(i));
        }
        return kinds;
    }

    /** Counts {@code stack} into {@code kinds}; a count may grow past a stack. */
    public static void add(List<ItemStack> kinds, ItemStack stack) {
        StationRecipes.add(kinds, stack);
    }

    /** See {@link StationRecipes#parts}. */
    public static int[] parts(List<Ingredient> ingredients, List<ItemStack> kinds) {
        return StationRecipes.parts(ingredients, kinds);
    }

    /** See {@link StationRecipes#servings}. */
    public static int servings(List<ItemStack> kinds, int[] parts) {
        return StationRecipes.servings(kinds, parts);
    }

    /** See {@link StationRecipes#fits}. */
    public static boolean fits(List<ItemStack> kinds, int[] parts) {
        return StationRecipes.fits(kinds, parts);
    }

    /** See {@link StationRecipes#kept}. */
    public static int kept(List<ItemStack> kinds, int[] parts) {
        return StationRecipes.kept(kinds, parts);
    }

    /** The pot's recipes as {@link StationRecipes} reads them, in their order. */
    public static List<StationRecipes.Candidate<RecipeHolder<CookingPotRecipe>>> candidates(List<RecipeHolder<CookingPotRecipe>> recipes) {
        return StationRecipes.candidates(recipes, RecipeHolder::id, holder -> ingredients(holder.value()));
    }

    /** The recipe {@code kinds} make in a pot of {@code slots} ingredient slots, or null: see {@link StationRecipes#choose}. */
    public static Choice choose(List<RecipeHolder<CookingPotRecipe>> recipes, List<ItemStack> kinds, int slots, ResourceLocation wanted,
            ResourceLocation found, boolean overflow) {
        StationRecipes.Choice<RecipeHolder<CookingPotRecipe>> choice = StationRecipes.choose(candidates(recipes), kinds, slots, wanted, found,
                overflow);
        return choice == null ? null : new Choice(choice.candidate().recipe(), choice.parts(), choice.fits());
    }
}
