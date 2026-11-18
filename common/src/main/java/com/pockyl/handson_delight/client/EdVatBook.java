package com.pockyl.handson_delight.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.station.EdVatStation;
import com.pockyl.handson_delight.station.KitchenEdVat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Extra Delight's vat's book: what ages of what, in which liquid and into which containers, and its stages in order at the foot of
 * the page: the thing each stage wants, and the lid where it ages under one; then the whole time in days. Tabs by what it is drawn
 * into: bottles, bowls, anything else.
 */
final class EdVatBook implements RecipeBook.Source {
    private static final int DAY = 24000;

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        KitchenEdVat.Access access = KitchenEdVat.access();
        for (KitchenEdVat.Aging recipe : KitchenEdVat.recipes(view.level())) {
            List<Ingredient> needed = new ArrayList<>(recipe.ingredients());
            if (access != null && !recipe.fluids().isEmpty()) {
                List<ItemStack> containers = new ArrayList<>();
                for (Fluid fluid : recipe.fluids()) {
                    containers.addAll(access.containersOf(fluid));
                }
                // A bucketful wants a bucket first; less, a bottle.
                boolean bucketful = recipe.amount() >= 1000;
                containers.sort(java.util.Comparator.comparing(stack -> stack.getItem() instanceof BucketItem != bucketful));
                if (!containers.isEmpty()) {
                    needed.add(Ingredient.of(containers.stream()));
                }
            }
            if (!recipe.container().isEmpty()) {
                needed.add(Ingredient.of(recipe.container().getItem()));
            }
            List<ItemStack> foot = new ArrayList<>();
            for (KitchenEdVat.Stage stage : recipe.stages()) {
                if (stage.wantsItem() && stage.ingredient().getItems().length > 0) {
                    foot.add(stage.ingredient().getItems()[0].copyWithCount(1));
                }
                if (stage.lid()) {
                    foot.add(new ItemStack(KitchenEdVat.lid()));
                }
            }
            foot.add(new ItemStack(Items.CLOCK));
            Component days = Component.translatable("handson_delight.book.days", Math.max(1, Math.round(recipe.ticks() / (float) DAY)));
            pages.add(new RecipeBook.Page(recipe.id(), recipe.result(), needed, foot, days, false, kind(recipe)));
        }
        return pages;
    }

    private static String kind(KitchenEdVat.Aging recipe) {
        return recipe.container().is(Items.GLASS_BOTTLE) ? "bottle" : recipe.container().is(Items.BOWL) ? "bowl" : "other";
    }

    @Override
    public List<RecipeBook.Tab> tabs(StationView view) {
        return List.of(new RecipeBook.Tab(null, new ItemStack(KitchenEdVat.vat()), Component.translatable("handson_delight.book.tab.all")),
                new RecipeBook.Tab("bottle", new ItemStack(Items.GLASS_BOTTLE), Component.translatable("handson_delight.book.tab.in_bottles")),
                new RecipeBook.Tab("bowl", new ItemStack(Items.BOWL), Component.translatable("handson_delight.book.tab.in_bowls")),
                new RecipeBook.Tab("other", new ItemStack(Items.LILY_PAD), Component.translatable("handson_delight.book.tab.other_containers")));
    }

    @Override
    public String memory() {
        return "ed_vat";
    }

    /** A recipe picked in the book goes in from the inventory: one set, or as many as the tank's liquid makes with shift. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(EdVatStation.DISH, page.key().toString());
        fill.putBoolean(EdVatStation.ALL, Screen.hasShiftDown());
        view.sendAction(EdVatStation.ACTION_FILL, fill);
    }

    /** What is in the vat counts as had: its places, and a container of its liquid for every bit of it there is. */
    @Override
    public List<ItemStack> inStation(StationView view) {
        List<ItemStack> in = new ArrayList<>(Arrays.asList(view.state().getItems(EdVatStation.INGREDIENTS)));
        KitchenEdVat.Access access = KitchenEdVat.access();
        String fluid = view.state().data().getString(EdVatStation.FLUID);
        ResourceLocation key = fluid.isEmpty() ? null : ResourceLocation.tryParse(fluid);
        if (access != null && key != null) {
            Fluid inside = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(key);
            in.addAll(access.containersOf(inside));
        }
        return in;
    }

    /** The book opens at what is in the vat. */
    @Override
    public Object current(StationView view) {
        String making = view.state().data().getString(EdVatStation.MAKING);
        if (!making.isEmpty()) {
            return ResourceLocation.tryParse(making);
        }
        return KitchenEdVat.matchingPlaces(KitchenEdVat.recipes(view.level()), view.state().getItems(EdVatStation.INGREDIENTS))
                .map(KitchenEdVat.Aging::id).orElse(null);
    }
}
