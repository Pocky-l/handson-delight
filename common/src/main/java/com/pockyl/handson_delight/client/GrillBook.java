package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.station.GrillStation;
import com.pockyl.handson_delight.station.KitchenGrill;

import java.util.ArrayList;
import java.util.List;

/**
 * The grill's book: what grills into what, on a campfire in the grill, in how many seconds on both sides. A recipe picked lays its
 * skewer from the inventory: one, or with shift as many as the grill has places.
 */
final class GrillBook implements RecipeBook.Source {
    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (KitchenGrill.Grilling recipe : KitchenGrill.recipes(view.level())) {
            Component seconds = Component.translatable("handson_delight.book.seconds", Math.max(1, Math.round(recipe.ticks() / 20.0F)));
            pages.add(new RecipeBook.Page(recipe.id(), recipe.result(), List.of(recipe.ingredient()),
                    List.of(new ItemStack(Items.CAMPFIRE), new ItemStack(Items.CLOCK)), seconds, false));
        }
        return pages;
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        if (page.ingredients().isEmpty()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
            if (page.ingredients().get(0).test(inventory.getNonEquipmentItems().get(slot))) {
                CompoundTag how = new CompoundTag();
                how.putInt(GrillStation.FROM, slot);
                if (!Minecraft.getInstance().hasShiftDown()) {
                    how.putBoolean(GrillStation.ONE, true);
                    how.putInt(GrillStation.PLACE, GrillBehavior.firstFree(view));
                }
                view.sendAction(GrillStation.ACTION_PLACE, how);
                return;
            }
        }
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        List<ItemStack> there = new ArrayList<>();
        for (int place = 0; place < KitchenGrill.PLACES; place++) {
            there.add(view.state().getItem(GrillStation.place(place)));
        }
        return there;
    }

    /** The book opens at what grills on the first place. */
    @Override
    public Object current(StationView view) {
        for (ItemStack stack : inStation(view)) {
            Object id = KitchenGrill.recipe(view.level(), stack).map(KitchenGrill.Grilling::id).orElse(null);
            if (id != null) {
                return id;
            }
        }
        return null;
    }
}
