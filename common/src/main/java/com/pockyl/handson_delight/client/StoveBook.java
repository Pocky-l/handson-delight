package com.pockyl.handson_delight.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.station.Grill;
import com.pockyl.handson_delight.station.SkilletCooking;
import com.pockyl.handson_delight.station.StoveCooking;
import com.pockyl.handson_delight.station.StoveStation;

import java.util.ArrayList;
import java.util.List;

/** The stove's book: what the fire cooks, each with the time a piece takes on the grill at the station's pace, both sides. */
final class StoveBook implements RecipeBook.Source {
    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        Level level = view.level();
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (CampfireCookingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING)) {
            ItemStack dish = recipe.getResultItem(level.registryAccess());
            if (dish.isEmpty() || recipe.getIngredients().isEmpty()) {
                continue;
            }
            int ticks = 2 * StoveCooking.sideTicks(recipe.getCookingTime());
            Component seconds = Component.translatable("handson_delight.book.seconds", Math.round(ticks / 20.0F));
            pages.add(new RecipeBook.Page(recipe.getId(), dish.copy(), List.of(recipe.getIngredients().get(0)),
                    List.of(new ItemStack(Items.CLOCK)), seconds, false));
        }
        return pages;
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        List<ItemStack> there = new ArrayList<>();
        for (int place = 0; place < Grill.PLACES; place++) {
            there.add(view.state().getItem(StoveStation.place(place)));
        }
        return there;
    }

    /** What grills on the first place that holds raw food. */
    @Override
    public Object current(StationView view) {
        for (ItemStack stack : inStation(view)) {
            Object id = SkilletCooking.recipe(view.level(), stack).map(CampfireCookingRecipe::getId).orElse(null);
            if (id != null) {
                return id;
            }
        }
        return null;
    }
}
