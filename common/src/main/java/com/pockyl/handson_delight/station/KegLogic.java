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
import net.minecraft.world.level.material.Fluid;
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
    /** While the lid is off: how far the keg had fermented, which it stays at until the lid is back on; -1 with the lid on. */
    private int heldAt = -1;

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
        if (!station.level().isClientSide()) {
            // Nobody left to put the lid back: it is put back, a keg is never left open.
            if (station.users().isEmpty() && isOpen(station)) {
                station.state().setControl(KegStation.LID, 0.0F);
            }
            holdWhileOpen(station);
        }
        refresh(station);
    }

    /** Whether the lid is off. */
    public static boolean isOpen(StationContext station) {
        return station.state().getControl(KegStation.LID) > 0.5F;
    }

    /**
     * A brew ferments only under its lid: while the lid is off, the keg's count of how long it has fermented stays where it was
     * (it goes down only when the keg starts something new).
     */
    private void holdWhileOpen(StationContext station) {
        if (!isOpen(station)) {
            heldAt = -1;
            return;
        }
        int time = keg.handson$data().get(0);
        if (heldAt < 0 || time < heldAt) {
            heldAt = time;
        }
        if (time != heldAt) {
            keg.handson$data().set(0, heldAt);
        }
    }

    /** Only food goes in, and only at the open top: buckets, bottles and drinks are poured or filled, never dropped in. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return isOpen(station) && !keg.handson$isVessel(stack);
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
        if (KegStation.ACTION_LID.equals(action)) {
            station.state().setControl(KegStation.LID, data.getBoolean(KegStation.OPEN) ? 1.0F : 0.0F);
        } else if (KegStation.ACTION_USE.equals(action)) {
            boolean tap = KegStation.TAP.equals(data.getString(KegStation.AT));
            if (!tap && !isOpen(station)) {
                // Nothing is poured in through the lid.
                return;
            }
            if (tap && data.getBoolean(KegStation.FROM_INVENTORY)) {
                tapFromInventory(station, player, data.getBoolean(KegStation.ALL));
            } else {
                useCarried(station, player, tap, data.getBoolean(KegStation.ALL));
            }
        } else if (KegStation.ACTION_FILL.equals(action)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString(KegStation.DISH));
            // A recipe picked in the book takes the lid off for its ingredients to go in.
            station.state().setControl(KegStation.LID, 1.0F);
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
        Fluid drawn = keg.handson$fluid();
        int before = keg.handson$amount();
        ItemStack vessel = carried.copyWithCount(1);
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
        station.effect(tap ? KegStation.EFFECT_TAPPED : KegStation.EFFECT_POURED, tap ? KegStation.TAP : KegStation.MOUTH,
                used(tap ? drawn : keg.handson$fluid(), Math.abs(keg.handson$amount() - before), vessel));
    }

    /** What an effect at the keg tells the clients: which liquid ran, and how much of it, for how long its stream runs. */
    private static CompoundTag used(Fluid fluid, int millibuckets, ItemStack vessel) {
        CompoundTag data = new CompoundTag();
        data.putString(KegStation.VESSEL, BuiltInRegistries.ITEM.getKey(vessel.getItem()).toString());
        data.putString(KegStation.FLUID, fluid == Fluids.EMPTY ? "" : BuiltInRegistries.FLUID.getKey(fluid).toString());
        data.putInt(KegStation.AMOUNT, millibuckets);
        return data;
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
        ItemStack vessel = container.copyWithCount(1);
        Fluid drawn = keg.handson$fluid();
        int before = keg.handson$amount();
        List<ItemStack> back = useWhile(container, true, all, player.getAbilities().instabuild);
        for (ItemStack stack : back) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        player.getInventory().setChanged();
        station.effect(KegStation.EFFECT_TAPPED, KegStation.TAP, used(drawn, Math.abs(keg.handson$amount() - before), vessel));
    }

    /** Brewin' and Chewin's own rule of which of the keg's temperatures, 1 to 5, a recipe that wants {@code wanted} takes. */
    public static boolean fitsWarmth(int keg, int wanted) {
        return switch (wanted) {
            case 1 -> keg <= 1;
            case 2 -> keg <= 2;
            case 3 -> keg > 1 && keg < 5;
            case 4 -> keg >= 4;
            case 5 -> keg >= 5;
            default -> true;
        };
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
        // The tank holds the keg's own liquid, so that it looks like what it is (KegLiquids on the client).
        ResourceLocation liquid = keg.handson$fluid() == Fluids.EMPTY ? HandsOnDelight.id("drink") : BuiltInRegistries.FLUID.getKey(keg.handson$fluid());
        state.setTank(KegStation.DRINK, TankContents.of(liquid, Math.round(100.0F * keg.handson$amount() / capacity)));
    }
}
