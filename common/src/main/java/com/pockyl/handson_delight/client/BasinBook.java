package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.station.BasinStation;
import com.pockyl.handson_delight.station.KitchenBasin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The basin's book: what goes on a skewer, each piece as many times as it goes on, the stick first. A recipe picked fills the
 * compartments from the inventory and skewers one, or with shift as many as there is for.
 */
final class BasinBook implements RecipeBook.Source {
    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (KitchenBasin.Skewering recipe : KitchenBasin.recipes(view.level())) {
            List<Ingredient> needed = new ArrayList<>();
            needed.add(recipe.tool());
            for (int i = 0; i < recipe.foodCount(); i++) {
                needed.add(recipe.food());
            }
            for (int i = 0; !recipe.plain() && i < recipe.sideCount(); i++) {
                needed.add(recipe.side());
            }
            pages.add(new RecipeBook.Page(recipe.id(), recipe.result(), needed, List.of(), Component.empty(), false));
        }
        return pages;
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(BasinStation.DISH, page.key().toString());
        fill.putBoolean(BasinStation.ALL, Minecraft.getInstance().hasShiftDown());
        view.sendAction(BasinStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        List<ItemStack> there = new ArrayList<>();
        for (int place = 0; place < KitchenBasin.PLACES; place++) {
            there.add(view.state().getItem(BasinStation.compartment(place)));
        }
        return there;
    }

    /** The book opens at what the compartments hold: the skewer a stick would make of them. */
    @Override
    public Object current(StationView view) {
        ItemStack first = view.state().getItem(BasinStation.compartment(0));
        ItemStack second = view.state().getItem(BasinStation.compartment(1));
        Optional<KitchenBasin.Skewering> making = KitchenBasin.recipes(view.level()).stream()
                .filter(recipe -> recipe.food().test(first) || recipe.food().test(second)).findFirst();
        return making.map(KitchenBasin.Skewering::id).orElse(null);
    }
}
