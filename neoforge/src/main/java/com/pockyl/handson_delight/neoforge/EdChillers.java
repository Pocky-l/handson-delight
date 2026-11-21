package com.pockyl.handson_delight.neoforge;

import com.lance5057.extradelight.ExtraDelightComponents;
import com.lance5057.extradelight.ExtraDelightRecipes;
import com.lance5057.extradelight.items.components.ChillComponent;
import com.lance5057.extradelight.workstations.chiller.ChillerRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import com.pockyl.handson_delight.Cookable;
import com.pockyl.handson_delight.station.KitchenChiller;
import com.pockyl.handson_delight.station.KitchenEdVat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Extra Delight's chiller recipes, the liquids of its containers and how long its ice lasts, read through its own classes. Loaded
 * only with Extra Delight there.
 */
final class EdChillers implements KitchenChiller.Access {
    static final EdChillers INSTANCE = new EdChillers();

    private List<ItemStack> ices;

    private EdChillers() {
    }

    @Override
    public List<KitchenChiller.Setting> read(Level level) {
        List<KitchenChiller.Setting> recipes = new ArrayList<>();
        for (RecipeHolder<ChillerRecipe> holder : level.getRecipeManager().getAllRecipesFor(ExtraDelightRecipes.CHILLER.get())) {
            ChillerRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            List<Ingredient> ingredients = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
            // A recipe with an ingredient that names nothing can never be made.
            if (result.isEmpty() || ingredients.size() > KitchenChiller.PLACES || !Cookable.all(ingredients)) {
                continue;
            }
            FluidStack fluid = recipe.getFluid();
            recipes.add(new KitchenChiller.Setting(holder.id(), List.copyOf(ingredients), fluid.isEmpty() ? Fluids.EMPTY : fluid.getFluid(),
                    fluid.isEmpty() ? 0 : fluid.getAmount(), recipe.getOutputContainer().copy(), recipe.shouldConsumeContainer(),
                    result.copy(), Math.max(1, recipe.getCookTime())));
        }
        return recipes;
    }

    @Override
    public Optional<KitchenEdVat.Liquid> liquidOf(ItemStack stack) {
        return EdVats.INSTANCE.liquidOf(stack);
    }

    @Override
    public List<ItemStack> containersOf(Fluid fluid) {
        return EdVats.INSTANCE.containersOf(fluid);
    }

    @Override
    public int chillOf(ItemStack stack) {
        ChillComponent chill = stack.isEmpty() ? null : stack.get(ExtraDelightComponents.CHILL.get());
        return chill == null ? 0 : Math.max(0, chill.time());
    }

    @Override
    public synchronized List<ItemStack> ices() {
        if (ices == null) {
            List<ItemStack> found = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (chillOf(stack) > 0) {
                    found.add(stack);
                }
            }
            found.sort(Comparator.comparingInt((ItemStack stack) -> chillOf(stack)).reversed());
            ices = List.copyOf(found);
        }
        return ices;
    }
}
