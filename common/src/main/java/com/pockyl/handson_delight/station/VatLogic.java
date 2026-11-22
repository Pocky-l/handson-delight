package com.pockyl.handson_delight.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.KitchenTags;
import com.pockyl.handson_delight.RecipeParts;

import java.util.ArrayList;
import java.util.List;

/**
 * The vat's menu by hand: the slots are the vat's own, aging is left to the vat. The lid is the station's: shut, the vat ages; open,
 * things go in and come out, and aging waits where it was. It also runs on the clients near a vat nobody works at; there it only
 * reads.
 */
public final class VatLogic implements StationLogic {
    /** The most sets of ingredients one pick in the book puts in. */
    private static final int FILL_MOST = 16;
    /**
     * How full the vat looks, hundredths of its inside from the floor to the rim: a bucket stands a third up it, a bottle a little,
     * never more than {@link #BREW_MOST}, well under the rim.
     */
    private static final int BREW_BUCKET = 45;
    private static final int BREW_BOTTLE = 14;
    private static final int BREW_MOST = 60;
    /** The liquid the scene draws; its colour is the client's (the liquids poured, the drink it turns into). */
    public static final Identifier BREW_LIQUID = HandsOnDelight.id("brew");
    /** What the vat makes that is no drink (ice cream), waiting in it: thick, opaque, creamy. */
    public static final Identifier CREAM_LIQUID = HandsOnDelight.id("cream");

    private final KitchenVat vat;
    /** While the lid is open: how far the vat had aged, which it stays at until the lid shuts; -1 with the lid shut. */
    private int heldAt = -1;
    /** The places are being shared out: their own changes do not share them out again. */
    private boolean arranging;
    /** The recipe the cook last picked in the book: the vat's choice while its food makes it, until it is empty. */
    private Identifier picked;

    public VatLogic(StationContext station) {
        vat = KitchenVat.of(station.blockEntity());
        StationState state = station.state();
        state.bindSlots(VatStation.INGREDIENTS, storage(vat, 0, KitchenVat.INGREDIENTS));
        state.bindSlots(VatStation.CONTAINER, storage(vat, KitchenVat.CONTAINER_SLOT, 1));
        state.bindSlots(VatStation.SERVED, storage(vat, KitchenVat.OUTPUT_SLOT, 1));
        state.bindSlots(VatStation.SERVINGS, new SlotStorage() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public ItemStack get(int index) {
                return vat == null ? ItemStack.EMPTY : vat.servings();
            }

            @Override
            public void set(int index, ItemStack stack) {
                if (vat != null) {
                    vat.setServings(stack);
                }
            }
        });
    }

    private static SlotStorage storage(KitchenVat vat, int start, int count) {
        return new SlotStorage() {
            @Override
            public int size() {
                return count;
            }

            @Override
            public ItemStack get(int index) {
                return vat == null ? ItemStack.EMPTY : vat.getItem(start + index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                if (vat != null) {
                    vat.setItem(start + index, stack);
                }
            }
        };
    }

    /** The cook comes to a vat that ages nothing: its lid swings up for them. One that ages stays shut until they open it. */
    @Override
    public void onOpen(StationContext station, Player player) {
        if (vat != null && vat.progress() == 0) {
            setLid(station, true);
        }
        if (vat != null && !station.level().isClientSide()) {
            giveBackContainers(player);
        }
        refresh(station);
    }

    /** The last cook goes: the lid is shut behind them, and what is inside ages. */
    @Override
    public void onClose(StationContext station, Player player) {
        if (vat != null && station.users().stream().allMatch(user -> user == player) && isOpen(station)) {
            setLid(station, false);
        }
    }

    @Override
    public void tick(StationContext station) {
        if (vat == null) {
            return;
        }
        if (!station.level().isClientSide()) {
            // Nobody left to shut the lid: it is shut, a vat is never left open.
            if (station.users().isEmpty() && isOpen(station)) {
                setLid(station, false);
            }
            holdWhileOpen(station);
        }
        refresh(station);
    }

    public static boolean isOpen(StationContext station) {
        return station.state().getControl(VatStation.LID) > 0.5F;
    }

    /** The lid as the station has it, and the block's own {@code open} the same, so the vat looks right to everybody. */
    private void setLid(StationContext station, boolean open) {
        station.state().setControl(VatStation.LID, open ? 1.0F : 0.0F);
        BlockState block = station.level().getBlockState(station.pos());
        if (block.hasProperty(BlockStateProperties.OPEN) && block.getValue(BlockStateProperties.OPEN) != open) {
            station.level().setBlock(station.pos(), block.setValue(BlockStateProperties.OPEN, open), 3);
        }
    }

    /** With the lid open the vat's count of how far it has aged stays where it was. */
    private void holdWhileOpen(StationContext station) {
        if (!isOpen(station)) {
            heldAt = -1;
            return;
        }
        int time = vat.progress();
        if (heldAt < 0 || time < heldAt) {
            heldAt = time;
        }
        if (time != heldAt) {
            vat.setProgress(heldAt);
        }
    }

    /** Things go in and come out only with the lid open; containers never go into the brew, nor into their place by hand. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (!isOpen(station)) {
            return false;
        }
        return !slot.group().equals(VatStation.CONTAINER) && !KitchenVat.isContainer(KitchenVat.recipes(station.level()), stack);
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return isOpen(station);
    }

    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(VatStation.SERVED_SLOT)) {
            stack.onCraftedBy(player, stack.getCount());
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (vat == null) {
            return;
        }
        if (VatStation.ACTION_LID.equals(action)) {
            setLid(station, data.getBooleanOr(VatStation.OPEN, false));
        } else if (VatStation.ACTION_PUT.equals(action)) {
            if (!isOpen(station) && vat.progress() == 0) {
                // The cook came with something for a vat that ages nothing: its lid is up for them before anything else.
                setLid(station, true);
            }
            if (isOpen(station)) {
                put(player, data.getIntOr(VatStation.FROM, 0));
            }
        } else if (VatStation.ACTION_FILL.equals(action)) {
            Identifier id = Identifier.tryParse(data.getStringOr(VatStation.DISH, ""));
            KitchenVat.recipes(station.level()).stream().filter(recipe -> recipe.id().equals(id)).findFirst().ifPresent(recipe -> {
                // A recipe picked in the book lifts the lid for its ingredients to go in.
                setLid(station, true);
                fill(player, recipe, data.getBooleanOr(VatStation.ALL, false) ? FILL_MOST : 1);
                giveBackContainers(player);
            });
        } else if (VatStation.ACTION_TAKE.equals(action) && isOpen(station)) {
            take(station, player, data.getBooleanOr(VatStation.ALL, false), data.contains(VatStation.FROM) ? data.getIntOr(VatStation.FROM, 0) : -1);
        }
    }

    /**
     * What is aged goes to the cook. A drink waiting in the vat is drawn into the containers they bring, one a serving: those on their
     * cursor ({@code from} -1) or in inventory slot {@code from}; one serving, or (with shift) as many as they brought containers for.
     * A container drawn full on the cursor stays there as the drink; the rest go to the inventory. Without a container nothing is
     * drawn. Anything else is taken as it lies, one or all. What the inventory has no room for drops at the cook's feet.
     */
    private void take(StationContext station, Player player, boolean all, int from) {
        ItemStack servings = vat.servings();
        if (!servings.isEmpty()) {
            java.util.Optional<KitchenVat.Aging> bottling = KitchenVat.bottling(KitchenVat.recipes(station.level()), servings);
            Inventory inventory = player.getInventory();
            boolean carried = from < 0 || from >= Inventory.INVENTORY_SIZE;
            ItemStack containers = carried ? player.inventoryMenu.getCarried() : inventory.getItem(from);
            if (bottling.isEmpty() || containers.isEmpty() || !bottling.get().container().test(containers)) {
                return;
            }
            int drawn = Math.min(all ? servings.getCount() : 1, containers.getCount());
            boolean wholeStack = drawn == containers.getCount();
            containers.shrink(drawn);
            vat.setServings(servings.copyWithCount(servings.getCount() - drawn));
            ItemStack given = servings.copyWithCount(drawn);
            given.onCraftedBy(player, drawn);
            if (carried && wholeStack) {
                player.inventoryMenu.setCarried(given);
            } else {
                if (carried) {
                    player.inventoryMenu.setCarried(containers.isEmpty() ? ItemStack.EMPTY : containers);
                }
                give(player, given);
            }
            inventory.setChanged();
            return;
        }
        ItemStack served = vat.getItem(KitchenVat.OUTPUT_SLOT);
        if (served.isEmpty()) {
            return;
        }
        int count = all ? served.getCount() : 1;
        ItemStack given = served.copyWithCount(count);
        vat.setItem(KitchenVat.OUTPUT_SLOT, served.copyWithCount(served.getCount() - count));
        given.onCraftedBy(player, count);
        give(player, given);
        player.getInventory().setChanged();
    }

    /**
     * The stack in the inventory's slot {@code from} goes in: onto the same thing where it lies, else into the first free place. A
     * container never goes in: the client draws the waiting drink into it instead.
     */
    private void put(Player player, int from) {
        Inventory inventory = player.getInventory();
        if (from < 0 || from >= Inventory.INVENTORY_SIZE) {
            return;
        }
        ItemStack stack = inventory.getItem(from);
        if (stack.isEmpty()) {
            return;
        }
        List<KitchenVat.Aging> recipes = KitchenVat.recipes(player.level());
        int place = -1;
        if (KitchenVat.isContainer(recipes, stack)) {
            // A bottle brought to the vat is for what waits in it: never into the brew.
            return;
        } else {
            for (int slot = 0; slot < KitchenVat.INGREDIENTS && place < 0; slot++) {
                ItemStack there = vat.getItem(slot);
                if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
                    place = slot;
                }
            }
            for (int slot = 0; slot < KitchenVat.INGREDIENTS && place < 0; slot++) {
                if (vat.getItem(slot).isEmpty()) {
                    place = slot;
                }
            }
        }
        if (place < 0) {
            return;
        }
        ItemStack there = vat.getItem(place);
        int moved = Math.min(stack.getCount(), stack.getMaxStackSize() - there.getCount());
        vat.setItem(place, there.isEmpty() ? stack.copyWithCount(moved) : there.copyWithCount(there.getCount() + moved));
        stack.shrink(moved);
        inventory.setChanged();
        arrange(player.level());
        giveBackContainers(player);
    }

    /**
     * Containers put in the vat's container slot elsewhere (its own menu, a hopper) are in the way of a recipe that wants none or
     * another one: Cultural Delights then makes nothing. They go back to the cook. A drink is drawn into a container from the cook's
     * inventory when it is done, so the station never puts one there.
     */
    private void giveBackContainers(Player player) {
        ItemStack there = vat.getItem(KitchenVat.CONTAINER_SLOT);
        if (there.isEmpty()) {
            return;
        }
        java.util.Optional<KitchenVat.Aging> making = KitchenVat.matchingPlaces(KitchenVat.recipes(player.level()), places());
        if (making.isPresent() && (!making.get().hasContainer() || !making.get().container().test(there))) {
            vat.setItem(KitchenVat.CONTAINER_SLOT, ItemStack.EMPTY);
            give(player, there);
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        if (vat != null && slot.group().equals(VatStation.INGREDIENTS)) {
            arrange(station.level());
        }
    }

    /** The vat's places as slots; laying them out is no cook's change. */
    private SlotStorage placesAsSlots() {
        return new SlotStorage() {
            @Override
            public int size() {
                return KitchenVat.INGREDIENTS;
            }

            @Override
            public ItemStack get(int index) {
                return vat.getItem(index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                arranging = true;
                try {
                    vat.setItem(index, stack);
                } finally {
                    arranging = false;
                }
            }
        };
    }

    /**
     * The vat ages one of each place at a time, so two lemons dropped in as one stack make no margarita: what lies in it is laid out
     * over its places the way the recipe it makes wants it ({@link StationRecipes#choose}).
     */
    private void arrange(Level level) {
        if (arranging) {
            return;
        }
        SlotStorage slots = placesAsSlots();
        List<ItemStack> kinds = StationRecipes.kinds(slots);
        if (kinds.isEmpty()) {
            picked = null;
            return;
        }
        List<KitchenVat.Aging> recipes = KitchenVat.recipes(level);
        Identifier found = KitchenVat.matchingPlaces(recipes, places()).map(KitchenVat.Aging::id).orElse(null);
        StationRecipes.Choice<KitchenVat.Aging> choice = StationRecipes.choose(StationRecipes.candidates(recipes, KitchenVat.Aging::id,
                KitchenVat.Aging::ingredients), kinds, KitchenVat.INGREDIENTS, picked, found, false);
        if (choice != null) {
            StationRecipes.lay(slots, kinds, choice.parts());
        }
    }

    private ItemStack[] places() {
        ItemStack[] places = new ItemStack[KitchenVat.INGREDIENTS];
        for (int slot = 0; slot < places.length; slot++) {
            places[slot] = vat.getItem(slot);
        }
        return places;
    }

    /**
     * Puts up to {@code sets} sets of the recipe's ingredients from the inventory into the vat, each in its own place from the first
     * on. Never more sets than there is room for once aged: in the vat for a drink (it is drawn into its containers later), in the
     * output for anything else.
     */
    private void fill(Player player, KitchenVat.Aging recipe, int sets) {
        SlotStorage slots = placesAsSlots();
        List<ItemStack> kinds = StationRecipes.kinds(slots);
        int[] parts = kinds.isEmpty() ? null : StationRecipes.parts(recipe.ingredients(), kinds);
        int already = parts == null ? 0 : StationRecipes.servings(kinds, parts);
        ItemStack done = recipe.hasContainer() ? vat.servings() : vat.getItem(KitchenVat.OUTPUT_SLOT);
        int room = done.isEmpty() ? recipe.result().getMaxStackSize()
                : ItemStack.isSameItemSameComponents(done, recipe.result()) ? done.getMaxStackSize() - done.getCount() : 0;
        int most = Math.min(sets, Math.max(1, room / Math.max(1, recipe.result().getCount())) - already);
        if (most <= 0) {
            return;
        }
        StationRecipes.Filled filled = StationRecipes.fill(player.getInventory(), recipe.ingredients(), kinds, KitchenVat.INGREDIENTS, most);
        if (filled != null) {
            picked = recipe.id();
            StationRecipes.lay(slots, filled.kinds(), filled.parts());
            player.getInventory().setChanged();
        }
    }

    /**
     * How full the vat looks: the liquids poured into it, a bucket filling it more than a bottle, and the drinks it has made that wait
     * to be drawn into their bottles.
     */
    static int brew(ItemStack[] places, ItemStack servings) {
        // A drink stands where its water stood, a little higher with every serving more.
        int amount = KitchenVat.servingsLevel(servings);
        for (ItemStack stack : places) {
            if (stack.isEmpty() || !KitchenTags.is(stack, KitchenTags.POURED)) {
                continue;
            }
            boolean bucket = RecipeParts.remainder(stack.getItem()) == Items.BUCKET;
            amount += (bucket ? BREW_BUCKET : BREW_BOTTLE) * stack.getCount();
        }
        return Math.min(BREW_MOST, amount);
    }

    private void refresh(StationContext station) {
        StationState state = station.state();
        int time = vat.progress();
        int total = vat.total();
        state.setControl(VatStation.PROGRESS, time > 0 && total > 0 ? Mth.clamp((float) time / total, 0.0F, 1.0F) : 0.0F);
        int brew = brew(places(), vat.servings());
        // What waits and is no drink (ice cream) is a liquid of its own look: thick and creamy.
        Identifier liquid = !vat.servings().isEmpty() && !KitchenVat.isDrink(KitchenVat.recipes(station.level()), vat.servings())
                ? CREAM_LIQUID : BREW_LIQUID;
        TankContents tank = brew > 0 ? TankContents.of(liquid, brew) : TankContents.EMPTY;
        if (!state.getTank(VatStation.BREW).equals(tank)) {
            state.setTank(VatStation.BREW, tank);
        }
        CompoundTag own = state.data().copy();
        // In whole seconds, as the badge tells it.
        int left = time > 0 && total > 0 ? Mth.ceil((total - time) / 20.0F) * 20 : 0;
        if (own.getIntOr(VatStation.LEFT, 0) != left) {
            own.putInt(VatStation.LEFT, left);
            state.setData(own);
        }
    }
}
