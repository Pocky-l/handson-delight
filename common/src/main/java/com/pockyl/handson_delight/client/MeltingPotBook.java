package com.pockyl.handson_delight.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.station.KitchenMeltingPot;
import com.pockyl.handson_delight.station.MeltingPotStation;

import java.util.ArrayList;
import java.util.List;

/**
 * The melting pot's book: what melts into what, shown as the bottle (or bucket) the liquid is drawn into, how much of it, over
 * Farmer's Delight's heat, and for how long.
 */
final class MeltingPotBook implements RecipeBook.Source {
    private static final ResourceLocation STOVE = ResourceLocation.fromNamespaceAndPath("farmersdelight", "stove");

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        KitchenMeltingPot pot = KitchenMeltingPot.of(view.level().getBlockEntity(view.pos()));
        List<RecipeBook.Page> pages = new ArrayList<>();
        ItemStack heat = BuiltInRegistries.ITEM.getOptional(STOVE).map(ItemStack::new).orElse(new ItemStack(Items.CAMPFIRE));
        for (KitchenMeltingPot.Melting recipe : KitchenMeltingPot.recipes(view.level())) {
            ItemStack dish = pot == null ? ItemStack.EMPTY : pot.handson$containerOf(recipe.fluid());
            if (dish.isEmpty()) {
                continue;
            }
            // As many bottles as the piece melts into (a bar one, a block four); a bucket stays one.
            int count = dish.is(recipe.fluid().getBucket()) ? 1 : Math.max(1, recipe.amount() / 250);
            List<ItemStack> foot = List.of(heat, new ItemStack(Items.CLOCK));
            Component seconds = Component.translatable("handson_delight.book.seconds", Math.max(1, Math.round(recipe.ticks() / 20.0F)));
            pages.add(new RecipeBook.Page(recipe.id(), dish.copyWithCount(count), List.of(recipe.input()), foot, seconds, false));
        }
        return pages;
    }

    @Override
    public String memory() {
        return "melting_pot";
    }

    /** A recipe picked in the book puts what melts into it in from the inventory: one, or a stack with shift. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(MeltingPotStation.DISH, page.key().toString());
        fill.putBoolean(MeltingPotStation.ALL, Screen.hasShiftDown());
        view.sendAction(MeltingPotStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return List.of(view.state().getItem(MeltingPotStation.INPUT_SLOT));
    }

    @Override
    public Object current(StationView view) {
        return KitchenMeltingPot.melting(KitchenMeltingPot.recipes(view.level()), view.state().getItem(MeltingPotStation.INPUT_SLOT))
                .map(KitchenMeltingPot.Melting::id).orElse(null);
    }
}
