package com.pockyl.handson_delight.neoforge;

import com.lance5057.extradelight.ExtraDelightRecipes;
import com.lance5057.extradelight.util.BottleFluidRegistry;
import com.lance5057.extradelight.workstations.vat.recipes.VatRecipe;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

import com.pockyl.handson_delight.station.KitchenEdVat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Extra Delight's vat recipes and the liquids of its containers, read through its own classes. Loaded only with Extra Delight there. */
final class EdVats implements KitchenEdVat.Access {
    static final EdVats INSTANCE = new EdVats();

    private EdVats() {
    }

    @Override
    public List<KitchenEdVat.Aging> read(Level level) {
        List<KitchenEdVat.Aging> recipes = new ArrayList<>();
        for (RecipeHolder<VatRecipe> holder : level.getRecipeManager().getAllRecipesFor(ExtraDelightRecipes.VAT.get())) {
            VatRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty() || recipe.getIngredients().isEmpty() || recipe.getIngredients().size() > KitchenEdVat.INGREDIENTS
                    || recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.isEmpty())) {
                continue;
            }
            List<Fluid> fluids = Arrays.stream(recipe.getFluid().getFluids()).map(FluidStack::getFluid).distinct().toList();
            List<KitchenEdVat.Stage> stages = new ArrayList<>();
            int count = Math.min(recipe.getStages(), recipe.getStageIngredients().size());
            for (int stage = 0; stage < count; stage++) {
                VatRecipe.StageIngredient wanted = recipe.getStageIngredients().get(stage);
                stages.add(new KitchenEdVat.Stage(wanted.ingredient, wanted.time, wanted.lid));
            }
            recipes.add(new KitchenEdVat.Aging(holder.id(), List.copyOf(recipe.getIngredients()), fluids, recipe.getFluid().amount(),
                    recipe.getUsedItem().copy(), result.copy(), List.copyOf(stages)));
        }
        return recipes;
    }

    @Override
    public Optional<KitchenEdVat.Liquid> liquidOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        if (stack.getItem() instanceof BucketItem bucket) {
            return bucket.content == Fluids.EMPTY ? Optional.empty() : Optional.of(new KitchenEdVat.Liquid(bucket.content, 1000));
        }
        FluidStack bottled = BottleFluidRegistry.getFluidFromBottle(stack);
        if (!bottled.isEmpty()) {
            return Optional.of(new KitchenEdVat.Liquid(bottled.getFluid(), bottled.getAmount()));
        }
        return FluidUtil.getFluidContained(stack.copyWithCount(1)).filter(fluid -> !fluid.isEmpty())
                .map(fluid -> new KitchenEdVat.Liquid(fluid.getFluid(), fluid.getAmount()));
    }

    @Override
    public List<ItemStack> containersOf(Fluid fluid) {
        List<ItemStack> containers = new ArrayList<>();
        ItemStack bottle = BottleFluidRegistry.getBottleFromFluidWithoutSize(fluid);
        if (!bottle.isEmpty()) {
            containers.add(bottle.copyWithCount(1));
        }
        if (fluid.getBucket() != Items.AIR) {
            containers.add(new ItemStack(fluid.getBucket()));
        }
        return containers;
    }
}
