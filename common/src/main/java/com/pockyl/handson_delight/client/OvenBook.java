package com.pockyl.handson_delight.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.station.KitchenOven;
import com.pockyl.handson_delight.station.OvenStation;

import java.util.ArrayList;
import java.util.List;

/**
 * The oven's book: what bakes of what, over what heat and for how long. Farmer's Delight's pies, cakes and roasts that Cultural
 * Delights moves from the crafting table into its oven are here with the rest. The heat shows as Farmer's Delight's stove, the oven's
 * usual fire; the time in seconds.
 */
final class OvenBook implements RecipeBook.Source {
    private static final ResourceLocation STOVE = ResourceLocation.fromNamespaceAndPath("farmersdelight", "stove");

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        ItemStack heat = BuiltInRegistries.ITEM.getOptional(STOVE).map(ItemStack::new).orElse(new ItemStack(Items.CAMPFIRE));
        for (KitchenOven.Baking recipe : KitchenOven.recipes(view.level())) {
            if (recipe.needed().isEmpty() || recipe.needed().size() > KitchenOven.INGREDIENTS) {
                continue;
            }
            List<ItemStack> foot = List.of(heat, new ItemStack(Items.CLOCK));
            Component seconds = Component.translatable("handson_delight.book.seconds", Math.max(1, Math.round(recipe.ticks() / 20.0F)));
            pages.add(new RecipeBook.Page(recipe.id(), recipe.result(), recipe.needed(), foot, seconds, false));
        }
        return pages;
    }

    /** A recipe picked in the book goes in from the inventory: one set, or as many as fit with shift. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(OvenStation.DISH, page.key().toString());
        fill.putBoolean(OvenStation.ALL, Screen.hasShiftDown());
        view.sendAction(OvenStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return List.of(view.state().getItems(OvenStation.INGREDIENTS));
    }

    /** The book opens at what lies on the tray. */
    @Override
    public Object current(StationView view) {
        return KitchenOven.matching(KitchenOven.recipes(view.level()), view.state().getItems(OvenStation.INGREDIENTS))
                .map(KitchenOven.Baking::id).orElse(null);
    }
}
