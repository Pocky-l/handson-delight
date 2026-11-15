package com.pockyl.handson_delight.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.CookingTimes;
import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.station.KettleStation;
import com.pockyl.handson_delight.station.KitchenKettle;

import java.util.ArrayList;
import java.util.List;

/**
 * The kettle's book: what brews, steeped in what, and for how long. The liquid it is brewed in shows as the bucket or drink it is
 * poured from: water, milk, or the tea a long or strong one is made of.
 */
final class KettleBook implements RecipeBook.Source {
    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        KitchenKettle kettle = KitchenKettle.of(view.level().getBlockEntity(view.pos()));
        List<RecipeBook.Page> pages = new ArrayList<>();
        if (kettle == null) {
            return pages;
        }
        for (KitchenKettle.Brewing recipe : kettle.handson$recipes()) {
            ItemStack dish = kettle.handson$drinkOf(recipe.result());
            List<Ingredient> ingredients = recipe.ingredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
            if (dish.isEmpty() || ingredients.size() > kettle.handson$ingredients()) {
                continue;
            }
            List<ItemStack> foot = new ArrayList<>();
            ItemStack base = kettle.handson$drinkOf(recipe.base());
            if (!base.isEmpty()) {
                foot.add(base);
            }
            foot.add(new ItemStack(Items.CLOCK));
            // A kettle that boils its water first takes a tick a millibucket for that, from cold water; its times are not ours to scale.
            int ticks = kettle.handson$boilsFirst() ? recipe.baseAmount() + recipe.ticks() : HandsOnConfig.scaled(recipe.ticks(), CookingTimes.kettle());
            // Farmer's Respite's own recipes brew in seconds (their files name the time a way its reader does not read).
            Component time = ticks < 2400 ? Component.translatable("handson_delight.book.seconds", Math.max(1, Math.round(ticks / 20.0F)))
                    : Component.translatable("handson_delight.book.minutes", Math.round(ticks / 1200.0F));
            pages.add(new RecipeBook.Page(recipe.id(), dish, ingredients, foot, time, false));
        }
        return pages;
    }

    /** A recipe picked in the book goes in from the inventory: one set, or as many as fit with shift; the lid comes off for it. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(KettleStation.DISH, page.key().toString());
        fill.putBoolean(KettleStation.ALL, Screen.hasShiftDown());
        view.sendAction(KettleStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return List.of(view.state().getItems(KettleStation.INGREDIENTS));
    }
}
