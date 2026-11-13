package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.pockyl.handson_delight.HandsOnDelight;

import java.util.List;

/**
 * The keg's menu by hand: the slots are the keg's own, buckets and drinks go through the keg's own use, and what the station shows
 * is read from it. Fermenting is left to the keg. It also runs on the clients around a keg nobody works at; there it only reads.
 */
public final class KegLogic implements StationLogic {
    /** The most sets of ingredients one pick in the book puts in. */
    private static final int FILL_MOST = 16;

    private final KitchenKeg keg;

    public KegLogic(StationContext station) {
        keg = (KitchenKeg) station.blockEntity();
        StationState state = station.state();
        state.bindSlots(KegStation.INGREDIENTS, storage(keg, 0, KitchenKeg.INGREDIENTS));
        state.bindSlots(KegStation.SERVED, storage(keg, KitchenKeg.OUTPUT_SLOT, 1));
    }

    private static SlotStorage storage(KitchenKeg keg, int start, int count) {
        return new SlotStorage() {
            @Override
            public int size() {
                return count;
            }

            @Override
            public ItemStack get(int index) {
                return keg.handson$getItem(start + index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                keg.handson$setItem(start + index, stack);
            }
        };
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        if (!station.level().isClientSide()) {
            keg.handson$feelTemperature();
        }
        refresh(station);
    }

    @Override
    public void tick(StationContext station) {
        refresh(station);
    }

    /** Buckets, bottles and drinks are poured or filled at the keg, never dropped in with the ingredients. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return !keg.handson$isVessel(stack);
    }

    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(KegStation.SERVED_SLOT)) {
            stack.onCraftedBy(station.level(), player, stack.getCount());
            keg.handson$awardRecipes(player);
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (KegStation.ACTION_USE.equals(action)) {
            boolean tap = KegStation.TAP.equals(data.getString(KegStation.AT));
            if (tap && data.getBoolean(KegStation.FROM_INVENTORY)) {
                tapFromInventory(station, player, data.getBoolean(KegStation.ALL));
            } else {
                useCarried(station, player, tap, data.getBoolean(KegStation.ALL));
            }
        } else if (KegStation.ACTION_FILL.equals(action)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString(KegStation.DISH));
            keg.handson$recipes().stream().filter(recipe -> recipe.id().equals(id)).findFirst()
                    .ifPresent(recipe -> fill(player, recipe, data.getBoolean(KegStation.ALL) ? FILL_MOST : 1));
        }
    }

    /** The stack on the cursor at the opening (poured in) or at the tap (filled); whatever comes back goes to the hand or the inventory. */
    private void useCarried(StationContext station, Player player, boolean tap, boolean all) {
        ItemStack carried = player.inventoryMenu.getCarried();
        if (tap ? !keg.handson$canTap(carried) : !keg.handson$canPour(carried)) {
            return;
        }
        List<ItemStack> back = useWhile(carried, tap, all, player.getAbilities().instabuild);
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
        station.effect(tap ? KegStation.EFFECT_TAPPED : KegStation.EFFECT_POURED, tap ? KegStation.TAP : KegStation.MOUTH, new CompoundTag());
    }

    /**
     * Uses {@code stack} on the keg once, or with {@code all} for as long as it still fills or pours, one at a time: the keg's own use
     * of several at once does not look at how much drink is left. What comes back, without what is left of {@code stack} itself. A
     * player in creative mode pours without emptying the bucket, as Brewin' and Chewin' does, but draws at the tap as anybody does:
     * its own creative use drains the keg and hands out nothing.
     */
    private List<ItemStack> useWhile(ItemStack stack, boolean tap, boolean all, boolean creative) {
        List<ItemStack> back = new java.util.ArrayList<>();
        do {
            for (ItemStack got : keg.handson$use(stack, 1, creative && !tap)) {
                if (got != stack) {
                    back.add(got);
                }
            }
        } while (all && !stack.isEmpty() && (tap ? keg.handson$canTap(stack) : keg.handson$canPour(stack)));
        return back;
    }

    /** An empty hand at the tap: a container from the inventory is filled, or all of them; the drinks go to the inventory. */
    private void tapFromInventory(StationContext station, Player player, boolean all) {
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            return;
        }
        int slot = containerSlot(keg, player.getInventory());
        if (slot < 0) {
            return;
        }
        ItemStack container = player.getInventory().getItem(slot);
        List<ItemStack> back = useWhile(container, true, all, player.getAbilities().instabuild);
        for (ItemStack stack : back) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        player.getInventory().setChanged();
        station.effect(KegStation.EFFECT_TAPPED, KegStation.TAP, new CompoundTag());
    }

    /** The inventory slot whose container the drink is drawn into: the one in hand first; -1 without one. */
    public static int containerSlot(KitchenKeg keg, Inventory inventory) {
        if (keg.handson$canTap(inventory.getSelected())) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (keg.handson$canTap(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /** Puts up to {@code sets} sets of the recipe's ingredients from the inventory into the keg, one slot per ingredient. */
    private void fill(Player player, KitchenKeg.Fermenting recipe, int sets) {
        List<Ingredient> ingredients = recipe.ingredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        if (ingredients.isEmpty() || ingredients.size() > KitchenKeg.INGREDIENTS) {
            return;
        }
        ItemStack[] inKeg = new ItemStack[KitchenKeg.INGREDIENTS];
        for (int i = 0; i < inKeg.length; i++) {
            inKeg[i] = keg.handson$getItem(i).copy();
            boolean fits = i < ingredients.size() ? inKeg[i].isEmpty() || ingredients.get(i).test(inKeg[i]) : inKeg[i].isEmpty();
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
                from[i] = source(inventory, ingredients.get(i), inKeg[i], from, i);
                found = from[i] >= 0;
            }
            if (!found) {
                break;
            }
            for (int i = 0; i < from.length; i++) {
                ItemStack one = inventory.removeItem(from[i], 1);
                if (inKeg[i].isEmpty()) {
                    inKeg[i] = one;
                } else {
                    inKeg[i].grow(1);
                }
            }
            done++;
        }
        for (int i = 0; i < ingredients.size() && done > 0; i++) {
            keg.handson$setItem(i, inKeg[i]);
        }
    }

    /** The inventory slot that gives the next item for the keg's slot {@code index}, not wanted by an earlier one beyond what it has. */
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
        ContainerData data = keg.handson$data();
        int time = data.get(0);
        int total = data.get(1);
        state.setControl(KegStation.PROGRESS, total > 0 ? Mth.clamp((float) time / total, 0.0F, 1.0F) : 0.0F);
        if (!station.level().isClientSide()) {
            // The client's keg does not know its temperature, nor which recipe it holds: the server says.
            state.setControl(KegStation.TEMPERATURE, data.get(2));
            state.setControl(KegStation.WANTED, keg.handson$wantedTemperature());
        }
        CompoundTag own = state.data().copy();
        own.putString(KegStation.FLUID, keg.handson$fluid() == Fluids.EMPTY ? "" : BuiltInRegistries.FLUID.getKey(keg.handson$fluid()).toString());
        // In whole seconds, as the badge tells it.
        own.putInt(KegStation.LEFT, total > 0 ? Mth.ceil((total - time) / 20.0F) * 20 : 0);
        state.setData(own);
        int capacity = Math.max(1, keg.handson$capacity());
        state.setTank(KegStation.DRINK, TankContents.of(HandsOnDelight.id("drink"), Math.round(100.0F * keg.handson$amount() / capacity)));
    }
}
