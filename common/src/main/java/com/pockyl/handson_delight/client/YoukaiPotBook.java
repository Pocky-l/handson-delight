package com.pockyl.handson_delight.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.station.KitchenYoukaiPot;
import com.pockyl.handson_delight.station.YoukaiPotLogic;
import com.pockyl.handson_delight.station.YoukaiPotStation;

import java.util.ArrayList;
import java.util.List;

/**
 * A Youkai's Feasts pot's book: the dishes this pot cooks (each needs the pot its dish is served in), what goes in, and how long it
 * cooks over heat. A dish picked goes in from the inventory, one of each.
 */
final class YoukaiPotBook implements RecipeBook.Source {
    private final String memory;

    /** {@code station}: the pot's station type, under whose name the book remembers its place. */
    YoukaiPotBook(ResourceLocation station) {
        this.memory = station.toString();
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        KitchenYoukaiPot pot = YoukaiPotBehavior.pot(view);
        List<RecipeBook.Page> pages = new ArrayList<>();
        if (pot == null) {
            return pages;
        }
        for (KitchenYoukaiPot.Dish dish : YoukaiPotStation.dishes(pot)) {
            // An ingredient nothing in the game fits (a tag no mod fills) makes a dish nobody can cook: it is not shown.
            if (dish.ingredients().stream().anyMatch(ingredient -> ingredient.getItems().length == 0)) {
                continue;
            }
            // Its times are its own: the config's cooking times do not scale them.
            Component time = Component.translatable("handson_delight.book.seconds", Math.max(1, Math.round(dish.ticks() / 20.0F)));
            pages.add(new RecipeBook.Page(dish.id(), dish.result(), dish.ingredients(), List.of(new ItemStack(Items.CLOCK)), time,
                    false));
        }
        return pages;
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(YoukaiPotStation.DISH, page.key().toString());
        view.sendAction(YoukaiPotStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        KitchenYoukaiPot pot = YoukaiPotBehavior.pot(view);
        return pot == null ? List.of() : pot.handson$contents();
    }

    /** The book opens at the dish what is in the pot is part of: the one cooking, else the first it could still become. */
    @Override
    public Object current(StationView view) {
        KitchenYoukaiPot pot = YoukaiPotBehavior.pot(view);
        if (pot == null) {
            return null;
        }
        if (pot.handson$cooking() != null) {
            return pot.handson$cooking();
        }
        List<ItemStack> in = pot.handson$contents();
        if (in.isEmpty()) {
            return null;
        }
        return YoukaiPotStation.dishes(pot).stream().filter(dish -> YoukaiPotLogic.missing(dish, in) != null).findFirst()
                .map(KitchenYoukaiPot.Dish::id).orElse(null);
    }

    @Override
    public String memory() {
        return memory;
    }
}
