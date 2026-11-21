package com.pockyl.handson_delight.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.station.ChillerStation;
import com.pockyl.handson_delight.station.KitchenChiller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The chiller's book: what sets of what, with the liquid it sets from (as the bottle or bucket it is poured from) and the container
 * it sets in among its ingredients, and ice and the seconds of cold at the foot. A tab for every container many things set in, named
 * by it, and one for the rest.
 */
final class ChillerBook implements RecipeBook.Source {
    private static final String OTHER = "other";
    /** A container gets a tab of its own from this many recipes on. */
    private static final int OWN_TAB = 5;

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        KitchenChiller.Access access = KitchenChiller.access();
        List<KitchenChiller.Setting> recipes = KitchenChiller.recipes(view.level());
        Map<Item, Integer> tabs = tabbed(recipes);
        List<ItemStack> foot = List.of(new ItemStack(Items.ICE), new ItemStack(Items.CLOCK));
        for (KitchenChiller.Setting recipe : recipes) {
            List<Ingredient> needed = new ArrayList<>(recipe.ingredients());
            if (access != null && recipe.wantsLiquid()) {
                List<ItemStack> containers = new ArrayList<>(access.containersOf(recipe.fluid()));
                // A bucketful wants a bucket first; less, a bottle.
                boolean bucketful = recipe.amount() >= KitchenChiller.BUCKET;
                containers.sort(Comparator.comparing(stack -> stack.getItem() instanceof BucketItem != bucketful));
                if (containers.isEmpty()) {
                    // A liquid nothing brings to the chiller: it cannot be made by hand.
                    continue;
                }
                needed.add(Ingredient.of(containers.stream()));
            }
            if (!recipe.container().isEmpty()) {
                needed.add(Ingredient.of(recipe.container().getItem()));
            }
            Component seconds = Component.translatable("handson_delight.book.seconds", Math.max(1, Math.round(recipe.ticks() / 20.0F)));
            pages.add(new RecipeBook.Page(recipe.id(), recipe.result(), needed, foot, seconds, false, kind(tabs, recipe)));
        }
        return pages;
    }

    /** The containers with a tab of their own, most recipes first. A square pan goes with the trays. */
    private static Map<Item, Integer> tabbed(List<KitchenChiller.Setting> recipes) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (KitchenChiller.Setting recipe : recipes) {
            if (!recipe.container().isEmpty()) {
                counts.merge(recipe.container().getItem(), 1, Integer::sum);
            }
        }
        Map<Item, Integer> tabs = new LinkedHashMap<>();
        counts.entrySet().stream().filter(entry -> entry.getValue() >= OWN_TAB)
                .sorted(Map.Entry.<Item, Integer>comparingByValue().reversed()).forEach(entry -> tabs.put(entry.getKey(), entry.getValue()));
        return tabs;
    }

    private static String kind(Map<Item, Integer> tabs, KitchenChiller.Setting recipe) {
        Item container = recipe.container().getItem();
        return tabs.containsKey(container) ? BuiltInRegistries.ITEM.getKey(container).toString() : OTHER;
    }

    @Override
    public List<RecipeBook.Tab> tabs(StationView view) {
        List<RecipeBook.Tab> tabs = new ArrayList<>();
        tabs.add(new RecipeBook.Tab(null, new ItemStack(KitchenChiller.block()), Component.translatable("handson_delight.book.tab.all")));
        for (Item container : tabbed(KitchenChiller.recipes(view.level())).keySet()) {
            ItemStack icon = new ItemStack(container);
            tabs.add(new RecipeBook.Tab(BuiltInRegistries.ITEM.getKey(container).toString(), icon, icon.getHoverName()));
        }
        tabs.add(new RecipeBook.Tab(OTHER, new ItemStack(Items.GLASS_BOTTLE), Component.translatable("handson_delight.book.tab.other_containers")));
        return tabs;
    }

    @Override
    public String memory() {
        return "chiller";
    }

    /** A recipe picked in the book is set up from the inventory: one set, or as many as the tank's liquid makes with shift. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(ChillerStation.DISH, page.key().toString());
        fill.putBoolean(ChillerStation.ALL, Screen.hasShiftDown());
        view.sendAction(ChillerStation.ACTION_FILL, fill);
    }

    /** What is in the chiller counts as had: its places, its container, and a container of its liquid. */
    @Override
    public List<ItemStack> inStation(StationView view) {
        List<ItemStack> in = new ArrayList<>(Arrays.asList(view.state().getItems(ChillerStation.INGREDIENTS)));
        in.add(view.state().getItem(ChillerStation.CONTAINER_SLOT));
        KitchenChiller.Access access = KitchenChiller.access();
        String fluid = view.state().data().getString(ChillerStation.FLUID);
        ResourceLocation key = fluid.isEmpty() ? null : ResourceLocation.tryParse(fluid);
        if (access != null && key != null) {
            Fluid inside = BuiltInRegistries.FLUID.get(key);
            in.addAll(access.containersOf(inside));
        }
        return in;
    }

    /** The book opens at what is set up in the chiller. */
    @Override
    public Object current(StationView view) {
        String making = view.state().data().getString(ChillerStation.MAKING);
        return making.isEmpty() ? null : ResourceLocation.tryParse(making);
    }
}
