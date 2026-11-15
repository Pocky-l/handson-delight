package com.pockyl.handson_delight.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.station.KitchenSteamer;
import com.pockyl.handson_delight.station.SteamerStation;
import com.pockyl.handson_delight.station.SteamerTower;

import java.util.ArrayList;
import java.util.List;

/**
 * The steamer's book: what goes on a rack and what it steams into, with the time it takes in a covered tower. A recipe picked lays
 * one from the inventory onto the open rack; with shift as many as there are free places on it.
 */
final class SteamerBook implements RecipeBook.Source {
    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (SteamerStation.Steaming recipe : SteamerStation.recipes(view.level())) {
            // An ingredient nothing in the game fits (a tag no mod fills) makes a recipe nobody can steam: it is not shown.
            if (recipe.ingredient().getItems().length == 0) {
                continue;
            }
            // Its times are its own. A covered tower steams a thing at 1.2 a tick (the steam up and the steam back off the lid).
            int seconds = Math.max(1, Math.round(recipe.ticks() / 1.2F / 20.0F));
            Component time = Component.translatable("handson_delight.book.seconds", seconds);
            pages.add(new RecipeBook.Page(recipe.id(), recipe.result(), List.of(recipe.ingredient()), List.of(new ItemStack(Items.CLOCK)),
                    time, false));
        }
        return pages;
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(SteamerStation.DISH, page.key().toString());
        fill.putBoolean(SteamerStation.ALL, Screen.hasShiftDown());
        view.sendAction(SteamerStation.ACTION_FILL, fill);
    }

    /** What lies on the open rack. */
    @Override
    public List<ItemStack> inStation(StationView view) {
        SteamerTower tower = SteamerBehavior.tower(view);
        List<ItemStack> on = new ArrayList<>();
        if (tower == null) {
            return on;
        }
        int open = SteamerBehavior.openRack(view, tower);
        for (int place = 0; place < KitchenSteamer.PLACES; place++) {
            ItemStack stack = tower.item(open, place);
            if (!stack.isEmpty()) {
                on.add(stack.copy());
            }
        }
        return on;
    }

    @Override
    public String memory() {
        return SteamerStation.ID.toString();
    }
}
