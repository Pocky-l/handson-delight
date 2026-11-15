package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.pockyl.handson_delight.HandsOnDelight;

import java.util.ArrayList;
import java.util.List;

/**
 * The kettle's menu by hand: the slots are the kettle's own, buckets and cups go through the kettle's own use, and what the station
 * shows is read from it. Brewing is left to the kettle. It also runs on the clients around a kettle nobody works at; there it only
 * reads.
 */
public final class KettleLogic implements StationLogic {
    /** The most sets of ingredients one pick in the book puts in. */
    private static final int FILL_MOST = 16;

    private final KitchenKettle kettle;

    public KettleLogic(StationContext station) {
        kettle = (KitchenKettle) station.blockEntity();
        station.state().bindSlots(KettleStation.INGREDIENTS, new SlotStorage() {
            @Override
            public int size() {
                return KitchenKettle.INGREDIENTS;
            }

            @Override
            public ItemStack get(int index) {
                return kettle.handson$getItem(index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                kettle.handson$setItem(index, stack);
            }
        });
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        refresh(station);
    }

    @Override
    public void tick(StationContext station) {
        if (!station.level().isClientSide()) {
            arrange(station);
        }
        refresh(station);
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        if (!station.level().isClientSide()) {
            arrange(station);
        }
    }

    /**
     * Things dropped into the kettle lie in it together, not in its two slots: two leaves dropped in one after the other stack in one
     * slot, and Farmer's Respite's recipe of two leaves wants one in each. What lies in the kettle is laid out the way a recipe wants
     * it: a stack shared out over both slots, or two of a kind put together, when that makes a recipe of the kettle's liquid (of any
     * liquid while it has none) and the way it lies does not.
     */
    private void arrange(StationContext station) {
        ItemStack first = kettle.handson$getItem(0);
        ItemStack second = kettle.handson$getItem(1);
        if (first.isEmpty() && second.isEmpty() || makesSomething(List.of(first, second))) {
            return;
        }
        if (second.isEmpty() && first.getCount() >= 2 || first.isEmpty() && second.getCount() >= 2) {
            ItemStack stack = first.isEmpty() ? second : first;
            ItemStack rest = stack.copyWithCount(stack.getCount() - 1);
            ItemStack one = stack.copyWithCount(1);
            if (makesSomething(List.of(rest, one))) {
                kettle.handson$setItem(0, rest);
                kettle.handson$setItem(1, one);
            }
        } else if (!first.isEmpty() && ItemStack.isSameItemSameComponents(first, second) && first.getCount() + second.getCount() <= first.getMaxStackSize()) {
            ItemStack together = first.copyWithCount(first.getCount() + second.getCount());
            if (makesSomething(List.of(together, ItemStack.EMPTY))) {
                kettle.handson$setItem(0, together);
                kettle.handson$setItem(1, ItemStack.EMPTY);
            }
        }
    }

    private boolean makesSomething(List<ItemStack> slots) {
        List<ItemStack> items = slots.stream().filter(stack -> !stack.isEmpty()).toList();
        Fluid fluid = kettle.handson$fluid();
        return kettle.handson$recipes().stream().anyMatch(recipe -> (fluid == Fluids.EMPTY || recipe.base().isSame(fluid)) && matches(recipe, items));
    }

    /** Whether {@code items}, one stack a slot, are the recipe's ingredients, one each, in any order. */
    public static boolean matches(KitchenKettle.Brewing recipe, List<ItemStack> items) {
        List<Ingredient> wanted = recipe.ingredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        if (wanted.size() != items.size()) {
            return false;
        }
        boolean[] used = new boolean[items.size()];
        for (Ingredient ingredient : wanted) {
            boolean found = false;
            for (int i = 0; i < items.size() && !found; i++) {
                if (!used[i] && ingredient.test(items.get(i))) {
                    used[i] = true;
                    found = true;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /** Whether the lid is off. */
    public static boolean isOpen(StationContext station) {
        return station.state().getControl(KettleStation.LID) > 0.5F;
    }

    /** Only what steeps goes in, and only at the open top: buckets, cups and drinks are poured or filled, never dropped in. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return KettleStation.isOpen(station.level().getBlockState(station.pos())) && !kettle.handson$isVessel(stack);
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (KettleStation.ACTION_LID.equals(action)) {
            setLid(station, data.getBoolean(KettleStation.OPEN));
        } else if (KettleStation.ACTION_USE.equals(action)) {
            boolean spout = KettleStation.SPOUT.equals(data.getString(KettleStation.AT));
            if (!spout && !KettleStation.isOpen(station.level().getBlockState(station.pos()))) {
                // Nothing is poured in through the lid.
                return;
            }
            if (spout && data.getBoolean(KettleStation.FROM_INVENTORY)) {
                serveFromInventory(station, player, data.getBoolean(KettleStation.ALL));
            } else {
                useCarried(station, player, spout, data.getBoolean(KettleStation.ALL));
            }
        } else if (KettleStation.ACTION_FILL.equals(action)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString(KettleStation.DISH));
            // A recipe picked in the book takes the lid off for its leaves to go in.
            setLid(station, true);
            kettle.handson$recipes().stream().filter(recipe -> recipe.id().equals(id)).findFirst()
                    .ifPresent(recipe -> fill(player, recipe, data.getBoolean(KettleStation.ALL) ? FILL_MOST : 1));
        }
        refresh(station);
    }

    /** Takes the lid off ({@code open}) or puts it back: the kettle block's own state, as Farmer's Respite's sneaking click sets it. */
    private static void setLid(StationContext station, boolean open) {
        BlockState state = station.level().getBlockState(station.pos());
        BooleanProperty lid = KettleStation.lidProperty(state);
        if (lid != null && state.getValue(lid) == open) {
            station.level().setBlockAndUpdate(station.pos(), state.setValue(lid, !open));
        }
    }

    /** The stack on the cursor at the opening (poured in) or at the spout (filled); whatever comes back goes to the hand or the inventory. */
    private void useCarried(StationContext station, Player player, boolean spout, boolean all) {
        ItemStack carried = player.inventoryMenu.getCarried();
        if (spout ? !kettle.handson$canServe(carried) : !kettle.handson$canPour(carried)) {
            return;
        }
        Fluid before = kettle.handson$fluid();
        int amount = kettle.handson$amount();
        ItemStack vessel = carried.copyWithCount(1);
        List<ItemStack> back = useWhile(carried, spout, all);
        boolean handFree = carried.isEmpty();
        for (ItemStack stack : back) {
            if (handFree) {
                // The last one stays in the hand, as a bowl at the pot.
                player.inventoryMenu.setCarried(stack);
                handFree = false;
            } else if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        if (handFree) {
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
        }
        station.effect(spout ? KettleStation.EFFECT_SERVED : KettleStation.EFFECT_POURED, spout ? KettleStation.SPOUT : KettleStation.MOUTH,
                used(spout ? before : kettle.handson$fluid(), Math.abs(kettle.handson$amount() - amount), vessel));
    }

    /** Uses {@code stack} on the kettle once, or with {@code all} for as long as it still fills or pours, one at a time. */
    private List<ItemStack> useWhile(ItemStack stack, boolean spout, boolean all) {
        List<ItemStack> back = new ArrayList<>();
        do {
            ItemStack got = kettle.handson$useOne(stack);
            if (got.isEmpty()) {
                break;
            }
            back.add(got);
        } while (all && !stack.isEmpty() && (spout ? kettle.handson$canServe(stack) : kettle.handson$canPour(stack)));
        return back;
    }

    /** An empty hand at the spout: a cup from the inventory is filled, or all of them; the drinks go to the inventory. */
    private void serveFromInventory(StationContext station, Player player, boolean all) {
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            return;
        }
        int slot = containerSlot(kettle, player.getInventory());
        if (slot < 0) {
            return;
        }
        ItemStack container = player.getInventory().getItem(slot);
        ItemStack vessel = container.copyWithCount(1);
        Fluid drawn = kettle.handson$fluid();
        int before = kettle.handson$amount();
        for (ItemStack stack : useWhile(container, true, all)) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        player.getInventory().setChanged();
        station.effect(KettleStation.EFFECT_SERVED, KettleStation.SPOUT, used(drawn, Math.abs(kettle.handson$amount() - before), vessel));
    }

    /** What an effect at the kettle tells the clients: which liquid ran, how much of it, and from or into what. */
    private static CompoundTag used(Fluid fluid, int millibuckets, ItemStack vessel) {
        CompoundTag data = new CompoundTag();
        data.putString(KettleStation.VESSEL, BuiltInRegistries.ITEM.getKey(vessel.getItem()).toString());
        data.putString(KettleStation.FLUID, fluid == Fluids.EMPTY ? "" : BuiltInRegistries.FLUID.getKey(fluid).toString());
        data.putInt(KettleStation.AMOUNT, millibuckets);
        return data;
    }

    /**
     * The inventory slot whose cup the drink is poured into: the one in hand first, then a cup or bottle, a bucket only when there is
     * none; -1 without one.
     */
    public static int containerSlot(KitchenKettle kettle, Inventory inventory) {
        if (kettle.handson$canServe(inventory.getSelected())) {
            return inventory.selected;
        }
        int bucket = -1;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (kettle.handson$canServe(stack)) {
                if (!stack.is(Items.BUCKET)) {
                    return slot;
                }
                bucket = bucket < 0 ? slot : bucket;
            }
        }
        return bucket;
    }

    /** Puts up to {@code sets} sets of the recipe's ingredients from the inventory into the kettle, one slot per ingredient. */
    private void fill(Player player, KitchenKettle.Brewing recipe, int sets) {
        List<Ingredient> ingredients = recipe.ingredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        if (ingredients.isEmpty() || ingredients.size() > KitchenKettle.INGREDIENTS) {
            return;
        }
        ItemStack[] inKettle = new ItemStack[KitchenKettle.INGREDIENTS];
        for (int i = 0; i < inKettle.length; i++) {
            inKettle[i] = kettle.handson$getItem(i).copy();
            boolean fits = i < ingredients.size() ? inKettle[i].isEmpty() || ingredients.get(i).test(inKettle[i]) : inKettle[i].isEmpty();
            if (!fits) {
                return;
            }
        }
        Inventory inventory = player.getInventory();
        int done = 0;
        while (done < sets) {
            int[] from = new int[ingredients.size()];
            boolean found = true;
            for (int i = 0; i < from.length && found; i++) {
                from[i] = source(inventory, ingredients.get(i), inKettle[i], from, i);
                found = from[i] >= 0;
            }
            if (!found) {
                break;
            }
            for (int i = 0; i < from.length; i++) {
                ItemStack one = inventory.removeItem(from[i], 1);
                if (inKettle[i].isEmpty()) {
                    inKettle[i] = one;
                } else {
                    inKettle[i].grow(1);
                }
            }
            done++;
        }
        for (int i = 0; i < ingredients.size() && done > 0; i++) {
            kettle.handson$setItem(i, inKettle[i]);
        }
    }

    /** The inventory slot that gives the next item for the kettle's slot {@code index}, not wanted by an earlier one beyond what it has. */
    private static int source(Inventory inventory, Ingredient ingredient, ItemStack there, int[] taken, int index) {
        if (!there.isEmpty() && there.getCount() >= there.getMaxStackSize()) {
            return -1;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !ingredient.test(stack) || !there.isEmpty() && !ItemStack.isSameItemSameComponents(there, stack)) {
                continue;
            }
            int wanted = 1;
            for (int earlier = 0; earlier < index; earlier++) {
                wanted += taken[earlier] == slot ? 1 : 0;
            }
            if (stack.getCount() >= wanted) {
                return slot;
            }
        }
        return -1;
    }

    private void refresh(StationContext station) {
        StationState state = station.state();
        ContainerData data = kettle.handson$data();
        int time = data.get(0);
        int total = data.get(1);
        state.setControl(KettleStation.PROGRESS, total > 0 ? Mth.clamp((float) time / total, 0.0F, 1.0F) : 0.0F);
        state.setControl(KettleStation.HEATED, kettle.handson$isHeated() ? 1.0F : 0.0F);
        state.setControl(KettleStation.LID, KettleStation.isOpen(station.level().getBlockState(station.pos())) ? 1.0F : 0.0F);
        CompoundTag own = state.data().copy();
        own.putString(KettleStation.FLUID, kettle.handson$fluid() == Fluids.EMPTY ? "" : BuiltInRegistries.FLUID.getKey(kettle.handson$fluid()).toString());
        // In whole seconds, as the badge tells it.
        own.putInt(KettleStation.LEFT, total > 0 && time > 0 ? Mth.ceil((total - time) / 20.0F) * 20 : 0);
        state.setData(own);
        int capacity = Math.max(1, kettle.handson$capacity());
        // The tank holds the kettle's own liquid, so that it looks like what it is (KettleLiquids on the client).
        ResourceLocation liquid = kettle.handson$fluid() == Fluids.EMPTY ? HandsOnDelight.id("drink") : BuiltInRegistries.FLUID.getKey(kettle.handson$fluid());
        state.setTank(KettleStation.DRINK, TankContents.of(liquid, Math.round(100.0F * kettle.handson$amount() / capacity)));
    }
}
