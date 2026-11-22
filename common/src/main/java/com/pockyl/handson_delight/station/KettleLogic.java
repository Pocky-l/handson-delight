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
    private final SlotStorage ingredients;
    /** The recipe the cook last picked in the book: the kettle's choice while its leaves make it, until the kettle is empty. */
    private ResourceLocation wanted;

    public KettleLogic(StationContext station) {
        kettle = (KitchenKettle) station.blockEntity();
        ingredients = new SlotStorage() {
            @Override
            public int size() {
                return kettle.handson$ingredients();
            }

            @Override
            public ItemStack get(int index) {
                return kettle.handson$getItem(index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                kettle.handson$setItem(index, stack);
            }
        };
        station.state().bindSlots(KettleStation.INGREDIENTS, ingredients);
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        // The client asks its copy of the kettle what may pour in and out: it must be the server's.
        ClientCopy.resend(kettle.handson$blockEntity(), player);
        refresh(station);
    }

    /** A lid that is the station's alone (Youkai's Feasts' kettle) goes back on when the last cook leaves, as the world draws it. */
    @Override
    public void onClose(StationContext station, Player player) {
        if (!station.level().isClientSide() && !kettle.handson$lidInBlock() && station.users().stream().allMatch(user -> user == player)) {
            kettle.handson$setOpen(false);
            refresh(station);
        }
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
     * slot, and Farmer's Respite's recipe of two leaves wants one in each. What lies in the kettle is laid out the way the recipe it
     * makes wants it ({@link StationRecipes#choose}), of the kettle's liquid (of any liquid while it has none).
     */
    private void arrange(StationContext station) {
        if (kettle.handson$perSlot() == 1) {
            // One thing a slot (Youkai's Feasts'): its own slots hold no more, so things lie apart already.
            return;
        }
        List<ItemStack> kinds = StationRecipes.kinds(ingredients);
        if (kinds.isEmpty()) {
            wanted = null;
            return;
        }
        List<StationRecipes.Candidate<KitchenKettle.Brewing>> candidates = candidates();
        List<ItemStack> inKettle = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            inKettle.add(ingredients.get(i));
        }
        StationRecipes.Candidate<KitchenKettle.Brewing> found = StationRecipes.reads(candidates, inKettle);
        StationRecipes.Choice<KitchenKettle.Brewing> choice = StationRecipes.choose(candidates, kinds, kettle.handson$ingredients(), wanted,
                found == null ? null : found.id(), false);
        if (choice != null) {
            StationRecipes.lay(ingredients, kinds, choice.parts());
        }
    }

    /** The kettle's recipes of the liquid it holds, or all of them while it holds none, in their order. */
    private List<StationRecipes.Candidate<KitchenKettle.Brewing>> candidates() {
        Fluid fluid = kettle.handson$fluid();
        List<KitchenKettle.Brewing> recipes = kettle.handson$recipes().stream().filter(recipe -> fluid == Fluids.EMPTY || recipe.base().isSame(fluid))
                .toList();
        return StationRecipes.candidates(recipes, KitchenKettle.Brewing::id, KitchenKettle.Brewing::ingredients);
    }

    /** Whether {@code items}, one stack a slot, are the recipe's ingredients, one each, in any order. */
    public static boolean matches(KitchenKettle.Brewing recipe, List<ItemStack> items) {
        return StationRecipes.matches(recipe.ingredients(), items);
    }

    /** Only what steeps goes in, and only at the open top: buckets, cups and drinks are poured or filled, unless a recipe steeps one. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return kettle.handson$isOpen() && kettle.handson$dropsIn(stack);
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (StationRecipes.ACTION_OFFER.equals(action)) {
            // A stack clicked onto a kettle whose slots hold other leaves: in, when they still brew a tea with it.
            if (kettle.handson$perSlot() > 1) {
                StationRecipes.offerCarried(station, this, player, KettleStation.INGREDIENTS, ingredients, data.getBoolean(StationRecipes.ONE),
                        kinds -> {
                            List<StationRecipes.Candidate<KitchenKettle.Brewing>> candidates = candidates();
                            List<ItemStack> inKettle = new ArrayList<>();
                            for (int i = 0; i < ingredients.size(); i++) {
                                inKettle.add(ingredients.get(i));
                            }
                            StationRecipes.Candidate<KitchenKettle.Brewing> found = StationRecipes.reads(candidates, inKettle);
                            StationRecipes.Choice<KitchenKettle.Brewing> choice = StationRecipes.choose(candidates, kinds,
                                    kettle.handson$ingredients(), wanted, found == null ? null : found.id(), true);
                            return choice == null ? null : choice.parts();
                        });
            }
        } else if (KettleStation.ACTION_LID.equals(action)) {
            kettle.handson$setOpen(data.getBoolean(KettleStation.OPEN));
        } else if (KettleStation.ACTION_USE.equals(action)) {
            // The client decided from its copy whether this pours or fills: every client gets it again, used or refused. The packet
            // is made when the tick's changes go out, so it already holds what this use did.
            ClientCopy.broadcast(kettle.handson$blockEntity());
            boolean spout = KettleStation.SPOUT.equals(data.getString(KettleStation.AT));
            if (!spout && !kettle.handson$isOpen()) {
                // Nothing is poured in through the lid.
                return;
            }
            if (spout && data.getBoolean(KettleStation.FROM_INVENTORY)) {
                serveFromInventory(station, player, data.getBoolean(KettleStation.ALL));
            } else {
                useCarried(station, player, spout, data.getBoolean(KettleStation.ALL),
                        data.contains(KettleStation.FROM) ? data.getInt(KettleStation.FROM) : -1);
            }
        } else if (KettleStation.ACTION_FILL.equals(action)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString(KettleStation.DISH));
            // A recipe picked in the book takes the lid off for its leaves to go in.
            kettle.handson$setOpen(true);
            kettle.handson$recipes().stream().filter(recipe -> recipe.id().equals(id)).findFirst()
                    .ifPresent(recipe -> fill(player, recipe, data.getBoolean(KettleStation.ALL) ? FILL_MOST : 1));
        }
        refresh(station);
    }

    /**
     * The stack on the cursor, or the one in inventory slot {@code from}, at the opening (poured in) or at the spout (filled); whatever
     * comes back goes where it was, then to the inventory.
     */
    private void useCarried(StationContext station, Player player, boolean spout, boolean all, int from) {
        if (from >= Inventory.INVENTORY_SIZE) {
            return;
        }
        ItemStack carried = from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
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
                // The last one stays in the hand, as a bowl at the pot, or in the slot it came from.
                putBack(player, from, stack);
                handFree = false;
            } else if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        if (handFree) {
            putBack(player, from, ItemStack.EMPTY);
        }
        station.effect(spout ? KettleStation.EFFECT_SERVED : KettleStation.EFFECT_POURED, spout ? KettleStation.SPOUT : KettleStation.MOUTH,
                used(spout ? before : kettle.handson$fluid(), Math.abs(kettle.handson$amount() - amount), vessel, back.size()));
    }

    /** {@code stack} into inventory slot {@code from}, or onto the cursor for -1. */
    static void putBack(Player player, int from, ItemStack stack) {
        if (from >= 0) {
            player.getInventory().setItem(from, stack);
        } else {
            player.inventoryMenu.setCarried(stack);
        }
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
        List<ItemStack> back = useWhile(container, true, all);
        for (ItemStack stack : back) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        player.getInventory().setChanged();
        station.effect(KettleStation.EFFECT_SERVED, KettleStation.SPOUT,
                used(drawn, Math.abs(kettle.handson$amount() - before), vessel, back.size()));
    }

    /** What an effect at the kettle tells the clients: which liquid ran, how much of it, from or into what, and how many of those. */
    private static CompoundTag used(Fluid fluid, int millibuckets, ItemStack vessel, int count) {
        CompoundTag data = new CompoundTag();
        data.putString(KettleStation.VESSEL, BuiltInRegistries.ITEM.getKey(vessel.getItem()).toString());
        data.putString(KettleStation.FLUID, fluid == Fluids.EMPTY ? "" : BuiltInRegistries.FLUID.getKey(fluid).toString());
        data.putInt(KettleStation.AMOUNT, millibuckets);
        data.putInt(KettleStation.COUNT, count);
        return data;
    }

    /**
     * The inventory slot whose cup the drink is poured into: the one in hand first, then any cup or bottle; -1 without one. A bucket
     * is never taken from the inventory: an empty hand at the spout asks for a drink, not for the kettle's water back. A bucket on
     * the cursor still draws it.
     */
    public static int containerSlot(KitchenKettle kettle, Inventory inventory) {
        if (offerable(kettle, inventory.getSelected())) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (offerable(kettle, inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean offerable(KitchenKettle kettle, ItemStack stack) {
        return !kettle.handson$holdsMany(stack) && kettle.handson$canServe(stack);
    }

    /**
     * Puts up to {@code sets} whole sets of the recipe's ingredients from the inventory into the kettle, laid out the way its mod reads
     * the recipe, never more a slot than the kettle holds (one leaf a slot in Youkai's Feasts'); the picked recipe stays the kettle's
     * choice while its leaves make it.
     */
    private void fill(Player player, KitchenKettle.Brewing recipe, int sets) {
        int perSlot = kettle.handson$perSlot();
        StationRecipes.Filled filled = StationRecipes.fill(player.getInventory(), recipe.ingredients().stream().filter(ingredient -> !ingredient.isEmpty())
                .toList(), StationRecipes.kinds(ingredients), kettle.handson$ingredients(), sets, perSlot);
        if (filled != null) {
            wanted = recipe.id();
            StationRecipes.lay(ingredients, filled.kinds(), filled.parts(), perSlot);
        }
    }

    private void refresh(StationContext station) {
        StationState state = station.state();
        ContainerData data = kettle.handson$data();
        int time = data.get(0);
        int total = data.get(1);
        state.setControl(KettleStation.PROGRESS, total > 0 ? Mth.clamp((float) time / total, 0.0F, 1.0F) : 0.0F);
        state.setControl(KettleStation.HEATED, kettle.handson$isHeated() ? 1.0F : 0.0F);
        float boil = kettle.handson$boil();
        state.setControl(KettleStation.BOIL, boil < 0.0F ? 1.0F : boil);
        if (kettle.handson$lidInBlock() || !station.level().isClientSide()) {
            // A lid that is the station's alone is known to the server only: the clients have it from the station's state.
            state.setControl(KettleStation.LID, kettle.handson$isOpen() ? 1.0F : 0.0F);
        }
        CompoundTag own = state.data().copy();
        own.putString(KettleStation.FLUID, kettle.handson$fluid() == Fluids.EMPTY ? "" : BuiltInRegistries.FLUID.getKey(kettle.handson$fluid()).toString());
        // In whole seconds, as the badge tells it; water that still has to boil first counts.
        int toBoil = kettle.handson$ticksToBoil();
        own.putInt(KettleStation.LEFT, total > 0 && (time > 0 || toBoil > 0) ? Mth.ceil((toBoil + total - time) / 20.0F) * 20 : 0);
        state.setData(own);
        int capacity = Math.max(1, kettle.handson$capacity());
        // The tank holds the kettle's own liquid, so that it looks like what it is (KettleLiquids on the client).
        ResourceLocation liquid = kettle.handson$fluid() == Fluids.EMPTY ? HandsOnDelight.id("drink") : BuiltInRegistries.FLUID.getKey(kettle.handson$fluid());
        state.setTank(KettleStation.DRINK, TankContents.of(liquid, Math.round(100.0F * kettle.handson$amount() / capacity)));
    }
}
