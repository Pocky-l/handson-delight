package com.pockyl.handson_delight.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.Optional;

/** The basin's actions at the station: food in, food back, a stick skewering what lies in the compartments, a recipe from the book. */
public final class BasinLogic implements StationLogic {
    /** The most skewers one click with shift makes: a stack of sticks. */
    private static final int MOST = 64;

    private final KitchenBasin basin;

    public BasinLogic(StationContext station) {
        basin = KitchenBasin.of(station.blockEntity());
        station.state().bindSlots(BasinStation.COMPARTMENTS, new SlotStorage() {
            @Override
            public int size() {
                return KitchenBasin.PLACES;
            }

            @Override
            public ItemStack get(int index) {
                return basin == null ? ItemStack.EMPTY : basin.getItem(index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                if (basin != null) {
                    basin.setItem(index, stack);
                }
            }
        });
    }

    /** Nothing goes in by the library's own moves: every stack comes through {@code put}, the hand's slot stays empty. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return false;
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (basin == null) {
            return;
        }
        int place = data.contains(BasinStation.PLACE) ? data.getInt(BasinStation.PLACE) : -1;
        int from = data.contains(BasinStation.FROM) ? data.getInt(BasinStation.FROM) : -1;
        boolean all = data.getBoolean(BasinStation.ALL);
        switch (action) {
            case BasinStation.ACTION_PUT -> put(station, player, place, from, all);
            case BasinStation.ACTION_TAKE -> take(station, player, place);
            case BasinStation.ACTION_SKEWER -> skewer(station, player, from, all);
            case BasinStation.ACTION_FILL -> fill(station, player, data.getString(BasinStation.DISH), all);
            default -> {
            }
        }
    }

    /** The stack on the cursor, or in inventory slot {@code from}, into compartment {@code place} or the one that takes it. */
    private void put(StationContext station, Player player, int place, int from, boolean all) {
        ItemStack carried = held(player, from);
        if (carried.isEmpty() || KitchenBasin.isTool(station.level(), carried)) {
            return;
        }
        int at = place >= 0 && place < KitchenBasin.PLACES ? place : compartmentFor(carried, -1);
        int put = at < 0 ? 0 : basin.put(at, carried, all ? carried.getCount() : 1);
        CompoundTag data = new CompoundTag();
        data.putInt(BasinStation.PLACE, at);
        if (put == 0) {
            data.put(BasinStation.STACK, carried.copyWithCount(1).saveOptional(station.level().registryAccess()));
            station.effect(BasinStation.EFFECT_REFUSED, "basin", data);
            return;
        }
        putBack(player, from, carried);
        station.effect(BasinStation.EFFECT_PUT, "basin", data);
    }

    /** The compartment that holds the same as {@code stack}, else a free one other than {@code not}; -1 for none. */
    private int compartmentFor(ItemStack stack, int not) {
        for (int place = 0; place < KitchenBasin.PLACES; place++) {
            if (place != not && ItemStack.isSameItemSameComponents(basin.getItem(place), stack)) {
                return place;
            }
        }
        for (int place = 0; place < KitchenBasin.PLACES; place++) {
            if (place != not && basin.getItem(place).isEmpty()) {
                return place;
            }
        }
        return -1;
    }

    private void take(StationContext station, Player player, int place) {
        ItemStack there = basin.getItem(place);
        if (there.isEmpty()) {
            return;
        }
        ItemStack taken = there.copy();
        basin.setItem(place, ItemStack.EMPTY);
        CompoundTag data = new CompoundTag();
        data.putInt(BasinStation.PLACE, place);
        data.put(BasinStation.STACK, taken.copyWithCount(1).saveOptional(station.level().registryAccess()));
        station.effect(BasinStation.EFFECT_TAKEN, "basin", data);
        if (!player.getInventory().add(taken)) {
            player.drop(taken, false);
        }
    }

    /**
     * Skewers with the stick on the cursor, in inventory slot {@code from}, or the first stick in the inventory (an empty hand is
     * offered what the inventory holds): one, or with {@code all} as many as there are sticks and food for.
     */
    private void skewer(StationContext station, Player player, int from, boolean all) {
        int slot = from;
        ItemStack tool = held(player, from);
        if (!KitchenBasin.isTool(station.level(), tool)) {
            slot = from >= 0 ? -1 : findTool(station, player);
            tool = slot >= 0 ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
        }
        if (tool.isEmpty()) {
            return;
        }
        Optional<KitchenBasin.Match> first = basin.match(station.level(), tool);
        if (first.isEmpty()) {
            CompoundTag data = new CompoundTag();
            data.put(BasinStation.STACK, tool.copyWithCount(1).saveOptional(station.level().registryAccess()));
            station.effect(BasinStation.EFFECT_REFUSED, "basin", data);
            return;
        }
        KitchenBasin.Match match = first.get();
        ItemStack food = basin.getItem(match.foodPlace()).copyWithCount(1);
        ItemStack side = match.recipe().plain() ? ItemStack.EMPTY : basin.getItem(match.sidePlace()).copyWithCount(1);
        int made = 0;
        ItemStack result = ItemStack.EMPTY;
        while (made < (all ? MOST : 1) && !tool.isEmpty()) {
            Optional<KitchenBasin.Match> now = basin.match(station.level(), tool);
            if (now.isEmpty() || !now.get().recipe().id().equals(match.recipe().id())) {
                break;
            }
            result = basin.skewer(now.get(), tool);
            made++;
        }
        // The sticks' slot first: the skewers would otherwise go into it while it is empty, and be put back over.
        putBack(player, slot, tool);
        for (int i = 0; i < made; i++) {
            ItemStack given = result.copy();
            if (!player.getInventory().add(given)) {
                player.drop(given, false);
            }
        }
        CompoundTag data = new CompoundTag();
        data.put(BasinStation.STACK, result.saveOptional(station.level().registryAccess()));
        data.put(BasinStation.FOOD, food.saveOptional(station.level().registryAccess()));
        data.put(BasinStation.SIDE, side.saveOptional(station.level().registryAccess()));
        data.putInt(BasinStation.FOOD_COUNT, match.recipe().foodCount());
        data.putInt(BasinStation.SIDE_COUNT, match.recipe().plain() ? 0 : match.recipe().sideCount());
        data.putInt(BasinStation.PLACE, match.foodPlace());
        data.putInt(BasinStation.MADE, made);
        station.effect(BasinStation.EFFECT_SKEWERED, "basin", data);
    }

    private static int findTool(StationContext station, Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (KitchenBasin.isTool(station.level(), inventory.items.get(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * A recipe picked in the book: its food goes into the compartment that holds it or a free one, its side into the other, from the
     * inventory, enough for one skewer or with {@code all} for as many as there is; then a stick from the inventory skewers them.
     */
    private void fill(StationContext station, Player player, String id, boolean all) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        KitchenBasin.Skewering recipe = KitchenBasin.recipes(station.level()).stream().filter(it -> it.id().equals(key)).findFirst()
                .orElse(null);
        if (recipe == null) {
            return;
        }
        int foodPlace = bring(player, recipe.food(), recipe.foodCount(), all, -1);
        if (foodPlace < 0) {
            return;
        }
        if (!recipe.plain() && bring(player, recipe.side(), recipe.sideCount(), all, foodPlace) < 0) {
            return;
        }
        skewer(station, player, -1, all);
    }

    /**
     * Brings {@code count} of what {@code wanted} takes (all the inventory holds with {@code all}) into the compartment holding it or a
     * free one other than {@code not}, topping up what lies there already; the compartment, or -1 when nothing fit.
     */
    private int bring(Player player, Ingredient wanted, int count, boolean all, int not) {
        for (int place = 0; place < KitchenBasin.PLACES; place++) {
            if (place != not && wanted.test(basin.getItem(place)) && basin.getItem(place).getCount() >= count && !all) {
                return place;
            }
        }
        Inventory inventory = player.getInventory();
        int target = -1;
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (!wanted.test(stack)) {
                continue;
            }
            int place = compartmentFor(stack, not);
            if (place < 0) {
                continue;
            }
            int have = basin.getItem(place).getCount();
            int want = all ? stack.getCount() : Math.max(0, count - have);
            basin.put(place, stack, want);
            target = place;
            if (!all && basin.getItem(place).getCount() >= count) {
                break;
            }
        }
        return target >= 0 && basin.getItem(target).getCount() >= count ? target : -1;
    }

    /** What the cook works with: the stack on the cursor, or the one in inventory slot {@code from}. */
    private static ItemStack held(Player player, int from) {
        if (from >= Inventory.INVENTORY_SIZE) {
            return ItemStack.EMPTY;
        }
        return from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
    }

    private static void putBack(Player player, int from, ItemStack left) {
        ItemStack kept = left.isEmpty() ? ItemStack.EMPTY : left;
        if (from >= 0) {
            player.getInventory().setItem(from, kept);
        } else {
            player.inventoryMenu.setCarried(kept);
        }
    }
}
