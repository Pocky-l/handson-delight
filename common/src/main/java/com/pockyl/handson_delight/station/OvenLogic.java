package com.pockyl.handson_delight.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.List;

/**
 * The oven's menu by hand: the slots are the oven's own, baking is left to the oven. The door is the station's: shut, the oven
 * bakes; open, what is in it can be laid on the tray or taken off, and baking waits where it was. It also runs on the clients
 * near an oven nobody works at; there it only reads.
 */
public final class OvenLogic implements StationLogic {
    /** The most sets of ingredients one pick in the book puts in. */
    private static final int FILL_MOST = 16;

    private final KitchenOven oven;
    /** While the door is open: how far the oven had baked, which it stays at until the door shuts; -1 with the door shut. */
    private int heldAt = -1;
    /** The tray is being shared out: its own changes do not share it out again. */
    private boolean arranging;
    /** The recipe the cook last picked in the book: the oven's choice while the tray's food makes it, until the tray is empty. */
    private net.minecraft.resources.ResourceLocation picked;

    public OvenLogic(StationContext station) {
        oven = KitchenOven.of(station.blockEntity());
        StationState state = station.state();
        state.bindSlots(OvenStation.INGREDIENTS, storage(oven, 0, KitchenOven.INGREDIENTS));
        state.bindSlots(OvenStation.SERVED, storage(oven, KitchenOven.OUTPUT_SLOT, 1));
    }

    private static SlotStorage storage(KitchenOven oven, int start, int count) {
        return new SlotStorage() {
            @Override
            public int size() {
                return count;
            }

            @Override
            public ItemStack get(int index) {
                return oven == null ? ItemStack.EMPTY : oven.getItem(start + index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                if (oven != null) {
                    oven.setItem(start + index, stack);
                }
            }
        };
    }

    /** The cook comes to an oven that bakes nothing: its door swings open for them. One that bakes stays shut until they open it. */
    @Override
    public void onOpen(StationContext station, Player player) {
        if (oven != null && oven.progress() == 0) {
            setDoor(station, true);
        }
        refresh(station);
    }

    /** The last cook goes: the door is shut behind them, and what lies inside bakes. */
    @Override
    public void onClose(StationContext station, Player player) {
        if (oven != null && station.users().stream().allMatch(user -> user == player) && isOpen(station)) {
            setDoor(station, false);
        }
    }

    @Override
    public void tick(StationContext station) {
        if (oven == null) {
            return;
        }
        if (!station.level().isClientSide()) {
            // Nobody left to shut the door: it is shut, an oven is never left open.
            if (station.users().isEmpty() && isOpen(station)) {
                setDoor(station, false);
            }
            holdWhileOpen(station);
        }
        refresh(station);
    }

    public static boolean isOpen(StationContext station) {
        return station.state().getControl(OvenStation.DOOR) > 0.5F;
    }

    /** The door as the station has it, and the block's own {@code open} the same, so the oven looks right to everybody. */
    private void setDoor(StationContext station, boolean open) {
        station.state().setControl(OvenStation.DOOR, open ? 1.0F : 0.0F);
        BlockState block = station.level().getBlockState(station.pos());
        if (block.hasProperty(BlockStateProperties.OPEN) && block.getValue(BlockStateProperties.OPEN) != open) {
            station.level().setBlock(station.pos(), block.setValue(BlockStateProperties.OPEN, open), 3);
        }
    }

    /** With the door open the heat goes: the oven's count of how far it has baked stays where it was. */
    private void holdWhileOpen(StationContext station) {
        if (!isOpen(station)) {
            heldAt = -1;
            return;
        }
        int time = oven.progress();
        if (heldAt < 0 || time < heldAt) {
            heldAt = time;
        }
        if (time != heldAt) {
            oven.setProgress(heldAt);
        }
    }

    /** Things go on the tray and come off it only with the door open. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return isOpen(station);
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return isOpen(station);
    }

    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(OvenStation.SERVED_SLOT)) {
            stack.onCraftedBy(station.level(), player, stack.getCount());
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (oven == null) {
            return;
        }
        if (OvenStation.ACTION_DOOR.equals(action)) {
            setDoor(station, data.getBoolean(OvenStation.OPEN));
        } else if (OvenStation.ACTION_LAY.equals(action)) {
            if (!isOpen(station) && oven.progress() == 0) {
                // The cook came with food to an oven that bakes nothing: its door is open for them before anything else.
                setDoor(station, true);
            }
            if (isOpen(station)) {
                lay(player, data.getInt(OvenStation.FROM));
            }
        } else if (OvenStation.ACTION_FILL.equals(action)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString(OvenStation.DISH));
            KitchenOven.recipes(station.level()).stream().filter(recipe -> recipe.id().equals(id)).findFirst().ifPresent(recipe -> {
                // A recipe picked in the book opens the door for its ingredients to go in.
                setDoor(station, true);
                fill(player, recipe, data.getBoolean(OvenStation.ALL) ? FILL_MOST : 1);
            });
        }
    }

    /** The stack in the inventory's slot {@code from} goes on the tray: onto the same thing where it lies, else into the first free place. */
    private void lay(Player player, int from) {
        Inventory inventory = player.getInventory();
        if (from < 0 || from >= Inventory.INVENTORY_SIZE) {
            return;
        }
        ItemStack stack = inventory.getItem(from);
        if (stack.isEmpty()) {
            return;
        }
        int place = -1;
        for (int slot = 0; slot < KitchenOven.INGREDIENTS && place < 0; slot++) {
            ItemStack there = oven.getItem(slot);
            if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
                place = slot;
            }
        }
        for (int slot = 0; slot < KitchenOven.INGREDIENTS && place < 0; slot++) {
            if (oven.getItem(slot).isEmpty()) {
                place = slot;
            }
        }
        if (place < 0) {
            return;
        }
        ItemStack there = oven.getItem(place);
        int moved = Math.min(stack.getCount(), stack.getMaxStackSize() - there.getCount());
        ItemStack laid = there.isEmpty() ? stack.copyWithCount(moved) : there.copyWithCount(there.getCount() + moved);
        oven.setItem(place, laid);
        stack.shrink(moved);
        inventory.setChanged();
        arrange(player.level());
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        if (oven != null && slot.group().equals(OvenStation.INGREDIENTS)) {
            arrange(station.level());
        }
    }

    /** The tray's places as slots; laying them out is no cook's change. */
    private com.pockyl.diegesis.api.state.SlotStorage tray() {
        return new com.pockyl.diegesis.api.state.SlotStorage() {
            @Override
            public int size() {
                return KitchenOven.INGREDIENTS;
            }

            @Override
            public ItemStack get(int index) {
                return oven.getItem(index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                arranging = true;
                try {
                    oven.setItem(index, stack);
                } finally {
                    arranging = false;
                }
            }
        };
    }

    /**
     * The oven bakes one of each place at a time, so three cinnamon sticks laid as one stack make no pie: what lies on the tray is laid
     * out over its places the way the recipe it makes wants it ({@link StationRecipes#choose}), as a cook spreads the sticks out.
     * Recipes laid out in the grid are left as the cook laid them.
     */
    private void arrange(net.minecraft.world.level.Level level) {
        if (arranging) {
            return;
        }
        com.pockyl.diegesis.api.state.SlotStorage tray = tray();
        List<ItemStack> kinds = StationRecipes.kinds(tray);
        if (kinds.isEmpty()) {
            picked = null;
            return;
        }
        ItemStack[] grid = new ItemStack[KitchenOven.INGREDIENTS];
        for (int slot = 0; slot < grid.length; slot++) {
            grid[slot] = oven.getItem(slot);
        }
        List<KitchenOven.Baking> recipes = KitchenOven.recipes(level);
        java.util.Optional<KitchenOven.Baking> now = KitchenOven.matching(recipes, grid);
        if (now.isPresent() && now.get().isShaped()) {
            return;
        }
        List<KitchenOven.Baking> loose = recipes.stream().filter(recipe -> !recipe.isShaped()).toList();
        StationRecipes.Choice<KitchenOven.Baking> choice = StationRecipes.choose(StationRecipes.candidates(loose, KitchenOven.Baking::id,
                KitchenOven.Baking::ingredients), kinds, KitchenOven.INGREDIENTS, picked, now.map(KitchenOven.Baking::id).orElse(null), false);
        if (choice != null) {
            StationRecipes.lay(tray, kinds, choice.parts());
        }
    }

    /**
     * Puts up to {@code sets} sets of the recipe's ingredients from the inventory on the tray: a laid-out recipe where its grid has
     * them, any other in the grid's places from the first on. Never more sets than the output slot has room for when baked.
     */
    private void fill(Player player, KitchenOven.Baking recipe, int sets) {
        if (!recipe.isShaped()) {
            fillLoose(player, recipe, sets);
            return;
        }
        int[] places = places(recipe);
        List<Ingredient> wanted = recipe.isShaped() ? recipe.ingredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList()
                : recipe.ingredients();
        if (places == null || wanted.isEmpty()) {
            return;
        }
        ItemStack[] onTray = new ItemStack[KitchenOven.INGREDIENTS];
        for (int slot = 0; slot < onTray.length; slot++) {
            onTray[slot] = oven.getItem(slot).copy();
        }
        // What lies there must be the start of the same recipe: nothing in a place the recipe leaves empty, nothing else in its places.
        for (int slot = 0; slot < onTray.length; slot++) {
            int index = indexOf(places, slot);
            if (index < 0 ? !onTray[slot].isEmpty() : !onTray[slot].isEmpty() && !wanted.get(index).test(onTray[slot])) {
                return;
            }
        }
        ItemStack output = oven.getItem(KitchenOven.OUTPUT_SLOT);
        int room = output.isEmpty() ? recipe.result().getMaxStackSize()
                : ItemStack.isSameItemSameComponents(output, recipe.result()) ? output.getMaxStackSize() - output.getCount() : 0;
        int most = Math.min(sets, Math.max(1, room / Math.max(1, recipe.result().getCount())) - setsOnTray(onTray, places));
        Inventory inventory = player.getInventory();
        int done = 0;
        while (done < most) {
            int[] from = new int[wanted.size()];
            boolean found = true;
            for (int i = 0; i < from.length && found; i++) {
                from[i] = source(inventory, wanted.get(i), onTray[places[i]], from, i);
                found = from[i] >= 0;
            }
            if (!found) {
                break;
            }
            for (int i = 0; i < from.length; i++) {
                ItemStack one = inventory.removeItem(from[i], 1);
                if (onTray[places[i]].isEmpty()) {
                    onTray[places[i]] = one;
                } else {
                    onTray[places[i]].grow(1);
                }
            }
            done++;
        }
        if (done > 0) {
            for (int place : places) {
                oven.setItem(place, onTray[place]);
            }
            inventory.setChanged();
        }
    }

    /**
     * A recipe of no grid: its ingredients go on the tray laid out the way the oven reads it, whatever lies there of it already; the
     * picked recipe stays the oven's choice while the tray's food makes it. Never more sets than the output slot has room for.
     */
    private void fillLoose(Player player, KitchenOven.Baking recipe, int sets) {
        com.pockyl.diegesis.api.state.SlotStorage tray = tray();
        List<ItemStack> kinds = StationRecipes.kinds(tray);
        int[] parts = kinds.isEmpty() ? null : StationRecipes.parts(recipe.ingredients(), kinds);
        int already = parts == null ? 0 : StationRecipes.servings(kinds, parts);
        ItemStack output = oven.getItem(KitchenOven.OUTPUT_SLOT);
        int room = output.isEmpty() ? recipe.result().getMaxStackSize()
                : ItemStack.isSameItemSameComponents(output, recipe.result()) ? output.getMaxStackSize() - output.getCount() : 0;
        int most = Math.min(sets, Math.max(1, room / Math.max(1, recipe.result().getCount())) - already);
        if (most <= 0) {
            return;
        }
        StationRecipes.Filled filled = StationRecipes.fill(player.getInventory(), recipe.ingredients(), kinds, KitchenOven.INGREDIENTS, most);
        if (filled != null) {
            picked = recipe.id();
            StationRecipes.lay(tray, filled.kinds(), filled.parts());
            player.getInventory().setChanged();
        }
    }

    /** The grid's places, row by row, that take the recipe's ingredients in order; null when it does not fit the grid. */
    static int[] places(KitchenOven.Baking recipe) {
        if (!recipe.isShaped()) {
            int count = recipe.ingredients().size();
            if (count > KitchenOven.INGREDIENTS) {
                return null;
            }
            int[] places = new int[count];
            for (int i = 0; i < count; i++) {
                places[i] = i;
            }
            return places;
        }
        if (recipe.width() > KitchenOven.GRID || recipe.height() > KitchenOven.GRID) {
            return null;
        }
        List<Integer> places = new java.util.ArrayList<>();
        for (int y = 0; y < recipe.height(); y++) {
            for (int x = 0; x < recipe.width(); x++) {
                if (!recipe.ingredients().get(x + y * recipe.width()).isEmpty()) {
                    places.add(x + y * KitchenOven.GRID);
                }
            }
        }
        return places.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int indexOf(int[] places, int slot) {
        for (int i = 0; i < places.length; i++) {
            if (places[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /** How many whole sets lie on the tray already: the smallest stack in the recipe's places. */
    private static int setsOnTray(ItemStack[] onTray, int[] places) {
        int sets = Integer.MAX_VALUE;
        for (int place : places) {
            sets = Math.min(sets, onTray[place].getCount());
        }
        return sets == Integer.MAX_VALUE ? 0 : sets;
    }

    /** The inventory slot that gives the next item for the tray's place {@code index}, not wanted by an earlier one beyond what it has. */
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
        int time = oven.progress();
        int total = oven.total();
        state.setControl(OvenStation.PROGRESS, time > 0 && total > 0 ? Mth.clamp((float) time / total, 0.0F, 1.0F) : 0.0F);
        CompoundTag own = state.data().copy();
        // In whole seconds, as the badge tells it.
        int left = time > 0 && total > 0 ? Mth.ceil((total - time) / 20.0F) * 20 : 0;
        if (own.getInt(OvenStation.LEFT) != left) {
            own.putInt(OvenStation.LEFT, left);
            state.setData(own);
        }
    }
}
