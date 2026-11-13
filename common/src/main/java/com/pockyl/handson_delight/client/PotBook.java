package com.pockyl.handson_delight.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.CookingTimes;
import com.pockyl.handson_delight.station.CookingPotStation;
import com.pockyl.handson_delight.station.KitchenPot;
import com.pockyl.handson_delight.station.PotLogic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The pot's book: Farmer's Delight's cooking recipes, the dish's container and its time at the station's pace, and its stars. */
final class PotBook implements RecipeBook.Source {
    /** How many ingredients the pot holds: a dish that needs more is not in its book. */
    private final int slots;

    PotBook(int slots) {
        this.slots = slots;
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        Level level = view.level();
        BlockEntity pot = level.getBlockEntity(view.pos());
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (CookingPotRecipe recipe : KitchenPot.recipes(pot, level)) {
            List<Ingredient> ingredients = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
            if (ingredients.size() > slots) {
                continue;
            }
            List<ItemStack> foot = new ArrayList<>();
            ItemStack container = KitchenPot.containerOf(pot, recipe, level.registryAccess());
            if (!container.isEmpty()) {
                foot.add(container);
            }
            foot.add(new ItemStack(Items.CLOCK));
            Component seconds = Component.translatable("handson_delight.book.seconds",
                    Math.round(recipe.getCookTime() * CookingTimes.cookingPot() / 20.0F / PotLogic.speed(1)));
            pages.add(new RecipeBook.Page(recipe.getId(), KitchenPot.resultOf(pot, recipe, level.registryAccess()), ingredients, foot, seconds,
                    true));
        }
        return pages;
    }

    /** A dish picked in the book goes into the pot from the inventory: one set, or as many as a batch cooks with shift. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(CookingPotStation.DISH, page.key().toString());
        fill.putBoolean(CookingPotStation.SCOOP_ALL, Screen.hasShiftDown());
        view.sendAction(CookingPotStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return List.of(view.state().getItems(CookingPotStation.INGREDIENTS));
    }

    /** The dish the ingredients make, or the soup in the pot. */
    @Override
    public Object current(StationView view) {
        Level level = view.level();
        Optional<CookingPotRecipe> cooking = KitchenPot.findRecipe(level.getBlockEntity(view.pos()), level, inStation(view));
        if (cooking.isPresent()) {
            return cooking.get().getId();
        }
        ItemStack meal = view.state().getItem(CookingPotStation.MEAL_SLOT);
        return meal.isEmpty() ? null : KitchenPot.recipes(level.getBlockEntity(view.pos()), level).stream()
                .filter(recipe -> KitchenPot.resultOf(level.getBlockEntity(view.pos()), recipe, level.registryAccess()).is(meal.getItem()))
                .map(CookingPotRecipe::getId).findFirst()
                .orElse(null);
    }
}
