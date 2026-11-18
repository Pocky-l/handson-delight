package com.pockyl.handson_delight.neoforge;

import com.lance5057.extradelight.ExtraDelightRecipes;
import com.lance5057.extradelight.util.BottleFluidRegistry;
import com.lance5057.extradelight.workstations.mixingbowl.recipes.MixingBowlRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import com.pockyl.handson_delight.Cookable;
import com.pockyl.handson_delight.station.KitchenBowl;

import java.util.ArrayList;
import java.util.List;

/**
 * Extra Delight's mixing bowl recipes and its liquids in their containers, read for the bowl station. Loaded only with Extra Delight.
 */
public final class ExtraDelightBowls implements KitchenBowl.Reader {
    @Override
    public List<KitchenBowl.Mixing> read(Level level) {
        List<KitchenBowl.Mixing> recipes = new ArrayList<>();
        for (RecipeHolder<MixingBowlRecipe> holder : level.getRecipeManager().getAllRecipesFor(ExtraDelightRecipes.MIXING_BOWL.get())) {
            MixingBowlRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            List<Ingredient> ingredients = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
            // A recipe with an ingredient or a liquid that names nothing can never be made.
            if (result.isEmpty() || ingredients.size() > KitchenBowl.PLACES || !Cookable.all(ingredients)) {
                continue;
            }
            List<KitchenBowl.LiquidNeed> liquids = new ArrayList<>();
            for (SizedFluidIngredient fluid : recipe.getFluids()) {
                List<ResourceLocation> fluids = new ArrayList<>();
                for (FluidStack stack : fluid.getFluids()) {
                    ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
                    if (stack.getFluid() != Fluids.EMPTY && !fluids.contains(id)) {
                        fluids.add(id);
                    }
                }
                if (fluids.isEmpty()) {
                    liquids = null;
                    break;
                }
                liquids.add(new KitchenBowl.LiquidNeed(List.copyOf(fluids), fluid.amount(), shown(fluids.get(0), fluid.amount())));
            }
            if (liquids == null) {
                continue;
            }
            recipes.add(new KitchenBowl.Mixing(holder.id(), ingredients, List.copyOf(liquids), recipe.getContainer().copy(), recipe.getUtensil(),
                    Math.max(1, recipe.getStirs()), result.copy()));
        }
        return recipes;
    }

    @Override
    public ItemStack shown(ResourceLocation id, int amount) {
        Fluid fluid = BuiltInRegistries.FLUID.get(id);
        if (fluid == Fluids.EMPTY) {
            return ItemStack.EMPTY;
        }
        ItemStack bucket = fluid.getBucket() == Items.AIR ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
        if (amount >= KitchenBowl.BUCKET && !bucket.isEmpty()) {
            return bucket;
        }
        ItemStack bottle = BottleFluidRegistry.getBottleFromFluidWithoutSize(fluid);
        return bottle.isEmpty() ? bucket : bottle.copyWithCount(1);
    }
}
