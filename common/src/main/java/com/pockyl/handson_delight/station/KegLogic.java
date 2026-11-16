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
 * Youkai's Feasts' fermentation tank is worked the same way: its lid is its block's, which it also lifts itself when something is
 * done, and while it is off the tank goes back in its count as it always does.
 */
public final class KegLogic implements StationLogic {
    /** The most sets of ingredients one pick in the book puts in. */
    private static final int FILL_MOST = 16;

    private final KitchenKeg keg;
    private final SlotStorage ingredients;
    /** A cook changed what lies in the keg: it is laid out on the next tick. */
    private boolean unsorted;
    /** The recipe the cook last picked in the book: the keg's choice while its food makes it, until the keg is empty. */
    private ResourceLocation wanted;
    /** The liquid the food was last laid out for: food thrown into an empty keg is laid out again once its liquid is poured in. */
    private Fluid arrangedFor = Fluids.EMPTY;
    /** While the lid is off: how far the keg had fermented, which it stays at until the lid is back on; -1 with the lid on. */
    private int heldAt = -1;

    public KegLogic(StationContext station) {
        keg = (KitchenKeg) station.blockEntity();
        StationState state = station.state();
        ingredients = storage(keg, 0, keg.handson$ingredients());
        state.bindSlots(KegStation.INGREDIENTS, ingredients);
        state.bindSlots(KegStation.SERVED, keg.handson$hasOutput() ? storage(keg, KitchenKeg.OUTPUT_SLOT, 1) : NO_OUTPUT);
    }

    /** The served slot of a keg without an output: what ferments into things stays where it lay. */
    private static final SlotStorage NO_OUTPUT = new SlotStorage() {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack get(int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public void set(int index, ItemStack stack) {
        }
    };

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
        if (!station.level().isClientSide() && keg.handson$feelsWarmth()) {
            keg.handson$feelTemperature();
        }
        // The client asks its copy of the keg what may be tapped and poured: it must be the server's.
        ClientCopy.resend(keg.handson$blockEntity(), player);
        refresh(station);
    }

    /** The last cook to go puts the block's own lid back on: the tank is left shut, to ferment. */
    @Override
    public void onClose(StationContext station, Player player) {
        if (!station.level().isClientSide() && keg.handson$lidOff() != null && station.users().stream().allMatch(user -> user == player)) {
            setLid(station, false);
        }
    }

    @Override
    public void tick(StationContext station) {
        if (!station.level().isClientSide()) {
            if (keg.handson$lidOff() == null) {
                // Nobody left to put the lid back: it is put back, a keg is never left open.
                if (station.users().isEmpty() && isOpen(station)) {
                    station.state().setControl(KegStation.LID, 0.0F);
                }
                holdWhileOpen(station);
            }
            if (wanted != null && StationRecipes.kinds(ingredients).isEmpty()) {
                wanted = null;
            }
            // Things one a slot lie as they were put in, and the tank reads its recipes whatever their order.
            if (keg.handson$perSlot() > 1 && (unsorted || keg.handson$fluid() != arrangedFor)) {
                unsorted = false;
                arrangedFor = keg.handson$fluid();
                arrange();
            }
        }
        refresh(station);
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        if (slot.group().equals(KegStation.INGREDIENTS) && !station.level().isClientSide()) {
            unsorted = true;
        }
    }

    /**
     * Food thrown in lies together, three beef in one stack, and Brewin' and Chewin' reads a recipe one ingredient a slot: what lies in
     * the keg is laid out the way the recipe it makes wants it ({@link StationRecipes#choose}). Food that makes nothing stays as it
     * lies.
     */
    private void arrange() {
        List<ItemStack> kinds = StationRecipes.kinds(ingredients);
        if (kinds.isEmpty()) {
            return;
        }
        List<ItemStack> inKeg = new java.util.ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            inKeg.add(ingredients.get(i));
        }
        StationRecipes.Candidate<KitchenKeg.Fermenting> found = StationRecipes.reads(candidates(true), inKeg);
        StationRecipes.Choice<KitchenKeg.Fermenting> choice = StationRecipes.choose(candidates(false), kinds, keg.handson$ingredients(), wanted,
                found == null ? null : found.id(), false);
        if (choice != null) {
            StationRecipes.lay(ingredients, kinds, choice.parts());
        }
    }

    /** How {@code kinds} lie for the drink they make in this keg ({@link #arrange}), or null. */
    private int[] partsWith(List<ItemStack> kinds) {
        StationRecipes.Candidate<KitchenKeg.Fermenting> found = StationRecipes.reads(candidates(true), inKeg());
        StationRecipes.Choice<KitchenKeg.Fermenting> choice = StationRecipes.choose(candidates(false), kinds, keg.handson$ingredients(), wanted,
                found == null ? null : found.id(), true);
        return choice == null ? null : choice.parts();
    }

    private List<ItemStack> inKeg() {
        List<ItemStack> inKeg = new java.util.ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            inKeg.add(ingredients.get(i));
        }
        return inKeg;
    }

    /**
     * The keg's recipes for the liquid it holds, as Brewin' and Chewin' reads them: of that base, or of none while it is empty. Without
     * {@code exact}, an empty keg takes any: its liquid may be poured in after the food.
     */
    private List<StationRecipes.Candidate<KitchenKeg.Fermenting>> candidates(boolean exact) {
        Fluid fluid = keg.handson$fluid();
        List<KitchenKeg.Fermenting> recipes = keg.handson$recipes().stream().filter(recipe -> fluid == Fluids.EMPTY
                ? !exact || recipe.base() == Fluids.EMPTY : recipe.base().isSame(fluid)).toList();
        return StationRecipes.candidates(recipes, KitchenKeg.Fermenting::id, KitchenKeg.Fermenting::ingredients);
    }

    /** Whether the lid is off. */
    public static boolean isOpen(StationContext station) {
        return station.state().getControl(KegStation.LID) > 0.5F;
    }

    /** Takes the lid off or puts it back: the block's own, or the one the station keeps. */
    private void setLid(StationContext station, boolean off) {
        keg.handson$setLidOff(off);
        station.state().setControl(KegStation.LID, off ? 1.0F : 0.0F);
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

    /** What lies under the block's own lid is taken out only with the lid off, as everything goes in. */
    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return keg.handson$lidOff() == null || isOpen(station);
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
        if (StationRecipes.ACTION_OFFER.equals(action)) {
            // A stack clicked onto a keg whose slots hold other food: in, when the food still makes a drink with it.
            if (keg.handson$perSlot() > 1 && StationRecipes.offerCarried(station, this, player, KegStation.INGREDIENTS, ingredients,
                    data.getBoolean(StationRecipes.ONE), this::partsWith)) {
                unsorted = true;
            }
        } else if (KegStation.ACTION_LID.equals(action)) {
            setLid(station, data.getBoolean(KegStation.OPEN));
        } else if (KegStation.ACTION_USE.equals(action)) {
            boolean tap = KegStation.TAP.equals(data.getString(KegStation.AT));
            if (!tap && !isOpen(station)) {
                // Nothing is poured in through the lid.
                return;
            }
            if (tap && data.getBoolean(KegStation.FROM_INVENTORY)) {
                tapFromInventory(station, player, data.getBoolean(KegStation.ALL));
            } else {
                useCarried(station, player, tap, data.getBoolean(KegStation.ALL),
                        data.contains(KegStation.FROM) ? data.getInt(KegStation.FROM) : -1);
            }
        } else if (KegStation.ACTION_FILL.equals(action)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString(KegStation.DISH));
            // A recipe picked in the book takes the lid off for its ingredients to go in.
            setLid(station, true);
            keg.handson$recipes().stream().filter(recipe -> recipe.id().equals(id)).findFirst()
                    .ifPresent(recipe -> fill(player, recipe, data.getBoolean(KegStation.ALL) ? FILL_MOST : 1));
        }
    }

    /**
     * The stack on the cursor, or the one in inventory slot {@code from}, at the opening (poured in) or at the tap (filled); whatever
     * comes back goes where it was, then to the inventory.
     */
    private void useCarried(StationContext station, Player player, boolean tap, boolean all, int from) {
        if (from >= Inventory.INVENTORY_SIZE) {
            return;
        }
        ItemStack carried = from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
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
                // The last one stays in the hand, as a bowl at the pot, or in the slot it came from.
                KettleLogic.putBack(player, from, stack);
                handFree = false;
            } else if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        if (handFree) {
            KettleLogic.putBack(player, from, ItemStack.EMPTY);
        }
        station.effect(tap ? KegStation.EFFECT_TAPPED : KegStation.EFFECT_POURED, tap ? KegStation.TAP : KegStation.MOUTH,
                used(tap ? drawn : keg.handson$fluid(), Math.abs(keg.handson$amount() - before), vessel, back.size()));
    }

    /** What an effect at the keg tells the clients: which liquid ran, how much of it (how long its stream runs), and into how many. */
    private static CompoundTag used(Fluid fluid, int millibuckets, ItemStack vessel, int count) {
        CompoundTag data = new CompoundTag();
        data.putString(KegStation.VESSEL, BuiltInRegistries.ITEM.getKey(vessel.getItem()).toString());
        data.putString(KegStation.FLUID, fluid == Fluids.EMPTY ? "" : BuiltInRegistries.FLUID.getKey(fluid).toString());
        data.putInt(KegStation.AMOUNT, millibuckets);
        data.putInt(KegStation.COUNT, count);
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
        station.effect(KegStation.EFFECT_TAPPED, KegStation.TAP, used(drawn, Math.abs(keg.handson$amount() - before), vessel, back.size()));
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

    /**
     * The inventory slot whose container the drink is drawn into: the one in hand first; -1 without one. A bucket is never taken
     * from the inventory (an empty hand asks for a drink, not for a tank's water back); a bucket on the cursor still draws.
     */
    public static int containerSlot(KitchenKeg keg, Inventory inventory) {
        if (offerable(keg, inventory.getSelected())) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (offerable(keg, inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean offerable(KitchenKeg keg, ItemStack stack) {
        return !keg.handson$holdsMany(stack) && keg.handson$canTap(stack);
    }

    /**
     * Puts up to {@code sets} whole sets of the recipe's ingredients from the inventory into the keg, laid out the way Brewin' and
     * Chewin' reads the recipe; the picked recipe stays the keg's choice while its food makes it. A keg that holds food the recipe does
     * not use takes nothing.
     */
    private void fill(Player player, KitchenKeg.Fermenting recipe, int sets) {
        int perSlot = keg.handson$perSlot();
        StationRecipes.Filled filled = StationRecipes.fill(player.getInventory(), recipe.ingredients().stream().filter(ingredient -> !ingredient.isEmpty())
                .toList(), StationRecipes.kinds(ingredients), keg.handson$ingredients(), sets, perSlot);
        if (filled != null) {
            wanted = recipe.id();
            StationRecipes.lay(ingredients, filled.kinds(), filled.parts(), perSlot);
        }
    }

    private void refresh(StationContext station) {
        StationState state = station.state();
        ContainerData data = keg.handson$data();
        int time = data.get(0);
        int total = data.get(1);
        state.setControl(KegStation.PROGRESS, total > 0 ? Mth.clamp((float) time / total, 0.0F, 1.0F) : 0.0F);
        Boolean lidOff = keg.handson$lidOff();
        if (lidOff != null) {
            // The block's lid is the lid: its redstone turns it, and the tank lifts it itself when something is done.
            state.setControl(KegStation.LID, lidOff ? 1.0F : 0.0F);
        }
        if (!station.level().isClientSide() && keg.handson$feelsWarmth()) {
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
