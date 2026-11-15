package com.pockyl.handson_delight.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.station.KitchenVat;
import com.pockyl.handson_delight.station.VatStation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The vat's book: what ages of what, in which container, how warm and for how long. The warmth shows as what makes it (Farmer's
 * Delight's stove under the vat for hot, ice for cold, nothing for mild), the time in seconds; tabs for the three warmths, Cultural
 * Delights' own three kinds (fermenting, distilling, chilling).
 */
final class VatBook implements RecipeBook.Source {
    private static final ResourceLocation STOVE = ResourceLocation.fromNamespaceAndPath("farmersdelight", "stove");

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (KitchenVat.Aging recipe : KitchenVat.recipes(view.level())) {
            List<ItemStack> foot = new ArrayList<>();
            ItemStack warmth = warmth(recipe.temperature());
            if (!warmth.isEmpty()) {
                foot.add(warmth);
            }
            foot.add(new ItemStack(Items.CLOCK));
            Component seconds = Component.translatable("handson_delight.book.seconds", Math.max(1, Math.round(recipe.ticks() / 20.0F)));
            pages.add(new RecipeBook.Page(recipe.id(), recipe.result(), recipe.needed(), foot, seconds, false,
                    recipe.temperature().serializedName()));
        }
        return pages;
    }

    /** What makes a warmth: a stove under the vat, ice under it, or nothing at all. */
    private static ItemStack warmth(KitchenVat.Temperature temperature) {
        return switch (temperature) {
            case HOT -> BuiltInRegistries.ITEM.getOptional(STOVE).map(ItemStack::new).orElse(new ItemStack(Items.CAMPFIRE));
            case COLD -> new ItemStack(Items.ICE);
            case NORMAL -> ItemStack.EMPTY;
        };
    }

    @Override
    public List<RecipeBook.Tab> tabs(StationView view) {
        List<RecipeBook.Tab> tabs = new ArrayList<>();
        tabs.add(new RecipeBook.Tab(null, new ItemStack(VatStation.VAT.get())));
        for (KitchenVat.Temperature temperature : KitchenVat.Temperature.values()) {
            ItemStack icon = temperature == KitchenVat.Temperature.NORMAL ? new ItemStack(Items.BROWN_MUSHROOM) : warmth(temperature);
            tabs.add(new RecipeBook.Tab(temperature.serializedName(), icon));
        }
        return tabs;
    }

    @Override
    public String memory() {
        return "vat";
    }

    /** A recipe picked in the book goes in from the inventory: one set, or as many as fit with shift. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(VatStation.DISH, page.key().toString());
        fill.putBoolean(VatStation.ALL, Screen.hasShiftDown());
        view.sendAction(VatStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        List<ItemStack> in = new ArrayList<>(Arrays.asList(view.state().getItems(VatStation.INGREDIENTS)));
        in.add(view.state().getItem(VatStation.CONTAINER_SLOT));
        return in;
    }

    /** The book opens at what is in the vat. */
    @Override
    public Object current(StationView view) {
        return KitchenVat.matchingPlaces(KitchenVat.recipes(view.level()), view.state().getItems(VatStation.INGREDIENTS))
                .map(KitchenVat.Aging::id).orElse(null);
    }
}
