package com.pockyl.handson_delight.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.CookingTimes;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.station.CookingPotStation;
import com.pockyl.handson_delight.station.KitchenPot;
import com.pockyl.handson_delight.station.PotLogic;
import com.pockyl.handson_delight.station.ScrollPot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The pot's book: Farmer's Delight's cooking recipes, the dish's container and its time at the station's pace, and its stars. The
 * alchemist pot's own recipes come first, in a tab of their own, Farmer's Delight's in another; a recipe that cooks only under a school
 * shows a scroll of that school among what goes in, and the scroll hanging over the pot counts as the player's.
 */
final class PotBook implements RecipeBook.Source {
    private static final String OWN = "own";
    private static final String FARMERS_DELIGHT = "farmersdelight";

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
        ScrollPot scrolls = ScrollPot.of(pot);
        for (RecipeHolder<CookingPotRecipe> holder : KitchenPot.recipes(pot, level)) {
            CookingPotRecipe recipe = holder.value();
            if (PotLogic.takesAnything(recipe)) {
                // Made of whatever lies in the pot (Modest Meals' meals): no list to show, nothing to fill the pot with.
                continue;
            }
            List<Ingredient> ingredients = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
            if (ingredients.size() > slots) {
                continue;
            }
            ResourceLocation school = scrolls == null ? null : scrolls.handson$schoolFor(recipe);
            if (school != null) {
                ingredients = new ArrayList<>(ingredients);
                ingredients.add(scrolls.handson$scrollIngredient(school));
            }
            List<ItemStack> foot = new ArrayList<>();
            ItemStack container = KitchenPot.containerOf(pot, recipe, level.registryAccess());
            if (!container.isEmpty()) {
                foot.add(container);
            }
            foot.add(new ItemStack(Items.CLOCK));
            Component seconds = Component.translatable("handson_delight.book.seconds",
                    Math.round(recipe.getCookTime() * CookingTimes.cookingPot() / 20.0F / PotLogic.speed(1)));
            String kind = scrolls == null ? null : scrolls.handson$isOwnRecipe(recipe) ? OWN : FARMERS_DELIGHT;
            pages.add(new RecipeBook.Page(holder.id(), KitchenPot.resultOf(pot, recipe, level.registryAccess()), ingredients, foot, seconds,
                    true, kind));
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

    /** At the alchemist pot: everything, its own recipes under its own block, Farmer's Delight's under the cooking pot. */
    @Override
    public List<RecipeBook.Tab> tabs(StationView view) {
        if (ScrollPot.of(view.level().getBlockEntity(view.pos())) == null) {
            return List.of();
        }
        return List.of(new RecipeBook.Tab(null, new ItemStack(Items.COMPASS)),
                new RecipeBook.Tab(OWN, new ItemStack(FarmersDelightContent.ALCHEMIST_POT.get())),
                new RecipeBook.Tab(FARMERS_DELIGHT, new ItemStack(BuiltInRegistries.ITEM.get(FarmersDelightContent.id("cooking_pot")))));
    }

    @Override
    public String memory() {
        return "alchemist_pot";
    }

    /** The ingredients in the pot, and the scroll hanging over it. */
    @Override
    public List<ItemStack> inStation(StationView view) {
        if (ScrollPot.of(view.level().getBlockEntity(view.pos())) == null) {
            return List.of(view.state().getItems(CookingPotStation.INGREDIENTS));
        }
        return Stream.concat(Stream.of(view.state().getItems(CookingPotStation.INGREDIENTS)),
                Stream.of(view.state().getItem(CookingPotStation.SCROLL_SLOT))).toList();
    }

    /** The dish the ingredients make, or the soup in the pot. */
    @Override
    public Object current(StationView view) {
        Level level = view.level();
        List<ItemStack> food = inStation(view);
        Optional<RecipeHolder<CookingPotRecipe>> cooking = KitchenPot.findRecipe(level.getBlockEntity(view.pos()), level, food);
        ScrollPot scrolls = ScrollPot.of(level.getBlockEntity(view.pos()));
        if (cooking.isEmpty() && scrolls != null) {
            // A dish waiting for its scroll opens the book at its page, which shows the scroll it wants.
            cooking = scrolls.handson$waiting(level, food);
        }
        if (cooking.isPresent()) {
            return cooking.get().id();
        }
        ItemStack meal = view.state().getItem(CookingPotStation.MEAL_SLOT);
        return meal.isEmpty() ? null : KitchenPot.recipes(level.getBlockEntity(view.pos()), level).stream()
                .filter(recipe -> KitchenPot.resultOf(level.getBlockEntity(view.pos()), recipe.value(), level.registryAccess()).is(meal.getItem()))
                .map(RecipeHolder::id).findFirst()
                .orElse(null);
    }
}
