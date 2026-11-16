package com.pockyl.handson_delight.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.pockyl.handson_delight.station.CuisineBoardStation;
import com.pockyl.handson_delight.station.CuisineLogic;
import com.pockyl.handson_delight.station.Cutting;
import com.pockyl.handson_delight.station.KitchenCuisine;

import com.pockyl.diegesis.api.client.StationView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The cuisine board's book: each dish with what goes on in the order it goes on, from the empty board (the steps done by hand are
 * the station's, not shown). Tabs: nigiri and gunkan, rolls (what a knife cuts), bamboo tubes. A dish picked is laid up from the
 * inventory step by step, shaped and rolled; with shift as many as there is for.
 */
final class CuisineBook implements RecipeBook.Source {
    private static final String SUSHI = "sushi";
    private static final String ROLLS = "rolls";
    private static final String BAMBOO = "bamboo";

    private final CuisineBehavior behavior;

    CuisineBook(CuisineBehavior behavior) {
        this.behavior = behavior;
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        KitchenCuisine board = CuisineBehavior.board(view);
        List<RecipeBook.Page> pages = new ArrayList<>();
        if (board == null) {
            return pages;
        }
        for (KitchenCuisine.Dish dish : board.handson$dishes()) {
            List<Ingredient> needed = dish.steps().stream().filter(step -> !step.isEmpty()).toList();
            // A step nothing in the game fits (a tag no mod fills) makes a dish nobody can make: it is not shown.
            if (needed.stream().anyMatch(step -> step.getItems().length == 0)) {
                continue;
            }
            pages.add(new RecipeBook.Page(dish.id(), dish.result(), needed, List.of(), Component.empty(), false, kind(view, dish)));
        }
        return pages;
    }

    private static String kind(StationView view, KitchenCuisine.Dish dish) {
        if (!dish.steps().isEmpty() && Arrays.stream(dish.steps().get(0).getItems())
                .anyMatch(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains(BAMBOO))) {
            return BAMBOO;
        }
        return Cutting.recipesFor(view.level(), dish.result()).isEmpty() ? SUSHI : ROLLS;
    }

    @Override
    public List<RecipeBook.Tab> tabs(StationView view) {
        return List.of(new RecipeBook.Tab(null, new ItemStack(net.minecraft.world.item.Items.COMPASS)),
                new RecipeBook.Tab(SUSHI, icon("tuna_nigiri")),
                new RecipeBook.Tab(ROLLS, icon("california_roll")),
                new RecipeBook.Tab(BAMBOO, icon("raw_kaguya_hime")));
    }

    @SuppressWarnings("removal")
    private static ItemStack icon(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(KitchenCuisine.MOD_ID, path)));
    }

    @Override
    public String memory() {
        return CuisineBoardStation.ID.toString();
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(CuisineBoardStation.DISH, page.key().toString());
        fill.putBoolean(CuisineBoardStation.ALL, Screen.hasShiftDown());
        view.sendAction(CuisineBoardStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return behavior.shown().stream().filter(stack -> !stack.isEmpty()).toList();
    }

    @Override
    public ItemStack asIngredient(StationView view, ItemStack stack) {
        KitchenCuisine board = CuisineBehavior.board(view);
        return board == null || stack.isEmpty() ? stack : board.handson$asStep(stack);
    }

    /** The book opens at the dish what lies on the board is the start of. */
    @Override
    public Object current(StationView view) {
        KitchenCuisine board = CuisineBehavior.board(view);
        List<ItemStack> on = behavior.shown();
        if (board == null || on.isEmpty()) {
            return null;
        }
        return board.handson$dishes().stream().filter(dish -> CuisineLogic.done(dish, on) > 0).findFirst()
                .map(KitchenCuisine.Dish::id).orElse(null);
    }

    /** A dish picked before stays open while what lies on the board is the start of it: rice is the start of every nigiri. */
    @Override
    public boolean fits(StationView view, Object key) {
        return dish(view, key) != null;
    }

    /** The dish {@code key} while what lies on the board is the start of it or the dish itself, done and waiting to be cut, else null. */
    static KitchenCuisine.Dish dish(StationView view, Object key) {
        KitchenCuisine board = CuisineBehavior.board(view);
        if (board == null || key == null) {
            return null;
        }
        List<ItemStack> on = board.handson$contents();
        ItemStack finished = board.handson$finished();
        return board.handson$dishes().stream().filter(dish -> dish.id().equals(key) && (CuisineLogic.done(dish, on) >= 0
                || !finished.isEmpty() && ItemStack.isSameItem(finished, dish.result()))).findFirst().orElse(null);
    }
}
