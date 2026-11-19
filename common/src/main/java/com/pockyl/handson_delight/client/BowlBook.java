package com.pockyl.handson_delight.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.RecipeParts;
import com.pockyl.handson_delight.station.BowlStation;
import com.pockyl.handson_delight.station.KitchenBowl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The mixing bowl's book: what is mixed of what, its liquids as the bottles or buckets they are poured from, the container it goes
 * into, the utensil and how many stirs. Tabs by what the result goes into: a bowl (salads, pasta), a bottle (shakes, drinks,
 * syrups), nothing (doughs, frostings, butter).
 */
final class BowlBook implements RecipeBook.Source {
    private static final String IN_BOWLS = "bowl";
    private static final String IN_BOTTLES = "bottle";
    private static final String LOOSE = "loose";

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (KitchenBowl.Mixing recipe : KitchenBowl.recipes(view.level())) {
            List<ItemStack> foot = new ArrayList<>();
            ItemStack[] utensils = RecipeParts.items(recipe.utensil());
            if (utensils.length > 0) {
                foot.add(utensils[0].copyWithCount(1));
            }
            Component stirs = Component.translatable("handson_delight.book.stirs", recipe.stirs());
            pages.add(new RecipeBook.Page(recipe.id(), recipe.result(), recipe.needed(), foot, stirs, false, kind(recipe)));
        }
        return pages;
    }

    private static String kind(KitchenBowl.Mixing recipe) {
        if (!recipe.hasContainer()) {
            return LOOSE;
        }
        return recipe.container().is(Items.GLASS_BOTTLE) ? IN_BOTTLES : IN_BOWLS;
    }

    @Override
    public List<RecipeBook.Tab> tabs(StationView view) {
        ItemStack dough = BuiltInRegistries.ITEM.getOptional(FarmersDelightContent.id("wheat_dough")).map(ItemStack::new)
                .orElse(new ItemStack(Items.SUGAR));
        return List.of(new RecipeBook.Tab(null, new ItemStack(BowlStation.BOWL.get()), Component.translatable("handson_delight.book.tab.all")), new RecipeBook.Tab(IN_BOWLS, new ItemStack(Items.BOWL), Component.translatable("handson_delight.book.tab.in_bowls")),
                new RecipeBook.Tab(IN_BOTTLES, new ItemStack(Items.GLASS_BOTTLE), Component.translatable("handson_delight.book.tab.in_bottles")), new RecipeBook.Tab(LOOSE, dough, Component.translatable("handson_delight.book.tab.loose")));
    }

    @Override
    public String memory() {
        return "mixing_bowl";
    }

    /** A liquid the player has in any container counts as the bottles of it the book shows: a bucket of milk is four. */
    @Override
    public ItemStack asIngredient(StationView view, ItemStack stack) {
        KitchenBowl bowl = KitchenBowl.of(view.level().getBlockEntity(view.pos()));
        KitchenBowl.Liquid liquid = bowl == null || stack.isEmpty() ? null : bowl.handson$liquidOf(stack);
        if (liquid == null) {
            return stack;
        }
        ItemStack shown = KitchenBowl.shown(liquid.fluid(), KitchenBowl.BOTTLE);
        if (shown.isEmpty() || ItemStack.isSameItem(shown, stack)) {
            return stack;
        }
        return shown.copyWithCount(Math.max(1, liquid.amount() / KitchenBowl.BOTTLE) * stack.getCount());
    }

    /** A recipe picked in the book goes in from the inventory: one set, or as many as fit with shift. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(BowlStation.DISH, page.key().toString());
        fill.putBoolean(BowlStation.ALL, net.minecraft.client.Minecraft.getInstance().hasShiftDown());
        view.sendAction(BowlStation.ACTION_FILL, fill);
    }

    /** What is in the bowl counts as the cook's: the food, and each liquid as the bottles of it. */
    @Override
    public List<ItemStack> inStation(StationView view) {
        List<ItemStack> in = new ArrayList<>(Arrays.asList(view.state().getItems(BowlStation.INGREDIENTS)));
        KitchenBowl bowl = KitchenBowl.of(view.level().getBlockEntity(view.pos()));
        if (bowl != null) {
            for (KitchenBowl.Liquid liquid : bowl.handson$liquids()) {
                ItemStack shown = KitchenBowl.shown(liquid.fluid(), KitchenBowl.BOTTLE);
                if (!shown.isEmpty()) {
                    in.add(shown.copyWithCount(Math.max(1, liquid.amount() / KitchenBowl.BOTTLE)));
                }
            }
        }
        return in;
    }

    /** The book opens at what the bowl makes. */
    @Override
    public Object current(StationView view) {
        String making = view.state().data().getStringOr(BowlStation.MAKING, "");
        return making.isEmpty() ? null : Identifier.tryParse(making);
    }
}
