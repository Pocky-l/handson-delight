package com.pockyl.handson_delight.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluids;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.station.KegStation;
import com.pockyl.handson_delight.station.KitchenKeg;

import java.util.ArrayList;
import java.util.List;

/**
 * The keg's book: what ferments, in what liquid, how warm and for how long. The liquid shows as the bucket or drink it is poured
 * from, the temperature as what makes it (ice or snow for cold, a torch or magma for warm and hot), nothing for a cellar's mild.
 */
final class KegBook implements RecipeBook.Source {
    /** A block or item for each temperature a recipe wants, 1 (cold) to 5 (hot); mild (3) wants nothing. */
    private static final ItemLike[] WARMTH = {Items.AIR, Items.PACKED_ICE, Items.SNOW_BLOCK, Items.AIR, Items.TORCH, Items.MAGMA_BLOCK};
    /** Shorter than this, ticks, a recipe's time is told in seconds (the fermentation tank's minute and a half), else in minutes. */
    private static final int IN_SECONDS = 3600;

    private final String memory;

    /** {@code memory}: the book's own memory of the page picked last (see {@link RecipeBook.Source#memory}). */
    KegBook(String memory) {
        this.memory = memory;
    }

    @Override
    public String memory() {
        return memory;
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        KitchenKeg keg = KitchenKeg.of(view.level().getBlockEntity(view.pos()));
        List<RecipeBook.Page> pages = new ArrayList<>();
        if (keg == null) {
            return pages;
        }
        for (KitchenKeg.Fermenting recipe : keg.handson$recipes()) {
            ItemStack dish = recipe.resultItem().isEmpty() ? keg.handson$drinkOf(recipe.resultFluid()) : recipe.resultItem();
            List<Ingredient> ingredients = recipe.ingredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
            if (dish.isEmpty() || ingredients.size() > keg.handson$ingredients()) {
                continue;
            }
            List<ItemStack> foot = new ArrayList<>();
            if (recipe.base() != Fluids.EMPTY) {
                ItemStack base = keg.handson$drinkOf(recipe.base());
                if (!base.isEmpty()) {
                    foot.add(base);
                }
            }
            int warmth = recipe.temperature();
            if (warmth > 0 && warmth < WARMTH.length && WARMTH[warmth].asItem() != Items.AIR) {
                foot.add(new ItemStack(WARMTH[warmth]));
            }
            foot.add(new ItemStack(Items.CLOCK));
            Component time = recipe.ticks() < IN_SECONDS
                    ? Component.translatable("handson_delight.book.seconds", Math.max(1, Math.round(recipe.ticks() / 20.0F)))
                    : Component.translatable("handson_delight.book.minutes", Math.max(1, Math.round(recipe.ticks() / 1200.0F)));
            pages.add(new RecipeBook.Page(recipe.id(), dish, ingredients, foot, time, false));
        }
        return pages;
    }

    /** A recipe picked in the book goes in from the inventory: one set, or as many as fit with shift. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(KegStation.DISH, page.key().toString());
        fill.putBoolean(KegStation.ALL, Screen.hasShiftDown());
        view.sendAction(KegStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return List.of(view.state().getItems(KegStation.INGREDIENTS));
    }
}
