package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extra Delight's melting pot, a double boiler, as the station sees it: mixed into its block entity by the NeoForge build. One item
 * at a time melts over Farmer's Delight's heat into its tank of 6000 mB (chocolate bars and blocks into syrup, marshmallows into
 * fluff, ice into water); the liquid is drawn into bottles and buckets. Every step goes through the pot's own tank.
 */
public interface KitchenMeltingPot {
    String MOD_ID = "extradelight";
    int INPUT_SLOT = 0;
    int TANK = 6000;
    ResourceLocation MELTING = ResourceLocation.fromNamespaceAndPath(MOD_ID, "melting_pot");

    static KitchenMeltingPot of(BlockEntity entity) {
        return entity instanceof KitchenMeltingPot pot ? pot : null;
    }

    BlockEntity handson$blockEntity();

    ItemStack handson$input();

    void handson$setInput(ItemStack stack);

    Fluid handson$fluid();

    int handson$amount();

    /** Ticks melted of the piece melting now, and the ticks it takes; 0 and 0 while nothing melts. */
    int handson$cookTime();

    int handson$cookTimeTotal();

    boolean handson$isHeated();

    /** Fills one {@code empty} container from the tank: the full one, or empty when it does not fit what is in the tank. */
    ItemStack handson$draw(ItemStack empty);

    /** What a liquid is drawn into: its bottle where there is one, else its bucket; empty for neither. */
    ItemStack handson$containerOf(Fluid fluid);

    /** A melting recipe: what melts, the liquid it gives and how much, and its ticks over heat. */
    record Melting(ResourceLocation id, Ingredient input, Fluid fluid, int amount, int ticks) {
    }

    static List<Melting> recipes(Level level) {
        return Recipes.of(level);
    }

    static Optional<Melting> melting(List<Melting> recipes, ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : recipes.stream().filter(recipe -> recipe.input().test(stack)).findFirst();
    }

    final class Recipes {
        private static WeakReference<Object> readFrom = new WeakReference<>(null);
        private static List<Melting> read = List.of();

        private Recipes() {
        }

        static synchronized List<Melting> of(Level level) {
            Object manager = level.getRecipeManager();
            if (readFrom.get() != manager) {
                read = List.copyOf(readAll(level));
                readFrom = new WeakReference<>(manager);
            }
            return read;
        }

        private static List<Melting> readAll(Level level) {
            List<Melting> recipes = new ArrayList<>();
            for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
                Recipe<?> recipe = holder.value();
                if (!MELTING.equals(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()))) {
                    continue;
                }
                try {
                    Ingredient input = (Ingredient) recipe.getClass().getField("input").get(recipe);
                    int ticks = recipe.getClass().getField("cooktime").getInt(recipe);
                    Object result = recipe.getClass().getField("result").get(recipe);
                    Fluid fluid = (Fluid) result.getClass().getMethod("getFluid").invoke(result);
                    int amount = (Integer) result.getClass().getMethod("getAmount").invoke(result);
                    if (input == null || input.isEmpty() || fluid == null || fluid == Fluids.EMPTY || amount <= 0) {
                        continue;
                    }
                    recipes.add(new Melting(holder.id(), input, fluid, amount, ticks));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // A recipe of a shape this does not know is left to the pot's own menu.
                }
            }
            return recipes;
        }
    }
}
