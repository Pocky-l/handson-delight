package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.pockyl.handson_delight.RecipeParts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

/**
 * An oven's menu by hand: the slots are the oven's own ({@link KitchenOven}), baking is left to the oven. The door is the station's:
 * with it open things go on the tray and come off it; shut, the oven bakes behind it. It also runs on the clients near an oven nobody
 * works at; there it only reads.
 */
public final class OvenLogic implements StationLogic {
    /** The most sets of ingredients one pick in the book puts in, for an oven that bakes sets. */
    private static final int FILL_MOST = 16;
    /**
     * Ticks a set that makes a dish (in its bakeware, where it needs some) lies still on the tray before the oven takes it in by itself:
     * long enough for the cook to lay a second set, short enough that nobody looks for a way to start it.
     */
    private static final int TAKE_IN_AFTER = 30;

    private final KitchenOven oven;
    /** The tray is being shared out: its own changes do not share it out again. */
    private boolean arranging;
    /** The recipe the cook last picked in the book: the oven's choice while the tray's food makes it, until the tray is empty. */
    private Identifier picked;
    /**
     * The cook changed what lies on the open tray or the bakeware standing there, at {@code changedAt} (game time): once it makes a dish
     * and lies still a moment, the oven takes it in ({@link #TAKE_IN_AFTER}). Off once it is in, and when the cook opens it by hand.
     */
    private boolean armed;
    /** The cook who last laid on the tray or picked in the book: handed what waits in the output when it is in the way. */
    private Player lastCook;
    private long changedAt;

    public OvenLogic(StationContext station) {
        oven = KitchenOven.of(station.blockEntity());
        if (oven == null) {
            return;
        }
        StationState state = station.state();
        state.bindSlots(OvenStation.INGREDIENTS, storage(oven.places(), oven::place, oven::setPlace));
        state.bindSlots(OvenStation.SERVED, storage(oven.outputs(), oven::output, oven::setOutput));
        if (oven.burnsFuel()) {
            state.bindSlots(OvenStation.FUEL, storage(1, index -> oven.fuel(), (index, stack) -> oven.setFuel(stack)));
        }
        if (oven.tools() > 0) {
            state.bindSlots(OvenStation.TOOLS, storage(oven.tools(), oven::tool, oven::setTool));
        }
        if (oven.usesBakeware()) {
            state.bindSlots(OvenStation.BAKEWARE, storage(1, index -> oven.bakeware(), (index, stack) -> oven.setBakeware(stack)));
        }
    }

    private static SlotStorage storage(int count, IntFunction<ItemStack> get, BiConsumer<Integer, ItemStack> set) {
        return new SlotStorage() {
            @Override
            public int size() {
                return count;
            }

            @Override
            public ItemStack get(int index) {
                return get.apply(index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                set.accept(index, stack);
            }
        };
    }

    /** The cook comes to an oven where nothing bakes: its door opens for them. One that bakes stays shut until they open it. */
    @Override
    public void onOpen(StationContext station, Player player) {
        if (oven != null && !isBaking()) {
            setDoor(station, true);
        }
        refresh(station);
    }

    /** The last cook goes: the door is shut behind them, and they take along a dish that would keep what they laid from baking. */
    @Override
    public void onClose(StationContext station, Player player) {
        if (oven != null && station.users().stream().allMatch(user -> user == player) && isOpen(station)) {
            makeWay(player, station.level());
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
            oven.whileDoor(station, isOpen(station));
            takeInWhenMade(station);
        }
        refresh(station);
    }

    /**
     * An oven that bakes sets takes the tray in by itself once what the cook laid makes a dish and has lain still a moment: no gesture to
     * find before the baking starts (the cook can still shut or open it by hand at the door or the mouth).
     */
    private void takeInWhenMade(StationContext station) {
        if (!armed || oven.bakesEachPlace() || !isOpen(station) || station.users().isEmpty()
                || station.level().getGameTime() - changedAt < TAKE_IN_AFTER) {
            return;
        }
        armed = false;
        Player cook = station.users().contains(lastCook) ? lastCook : station.users().iterator().next();
        makeWay(cook, station.level());
        if (makesDish(station.level())) {
            setDoor(station, false);
        }
    }

    /** Whether the tray makes a dish now: a recipe of its bakeware (where it needs some) whose dish the output still has room for. */
    private boolean makesDish(Level level) {
        Optional<KitchenOven.Baking> recipe = onTray(level);
        return recipe.isPresent() && room(recipe.get()) >= Math.max(1, recipe.get().result().getCount());
    }

    /** The recipe what lies on the tray makes, in the bakeware standing in the oven where it needs some, whatever waits in the output. */
    private Optional<KitchenOven.Baking> onTray(Level level) {
        List<KitchenOven.Baking> recipes = oven.recipes(level);
        return oven.usesBakeware() ? KitchenOven.matching(recipes, grid(), oven.bakeware()) : KitchenOven.matching(recipes, grid());
    }

    /**
     * A dish still waiting in the output that leaves no room for the one the tray makes (another dish, or a full stack) goes to the
     * cook, as a baker takes the last batch out before the next goes in: the oven bakes nothing beside it, and the set would lie there
     * with no sign why. Into the inventory, at the cook's feet what does not fit.
     */
    private void makeWay(Player cook, Level level) {
        if (cook == null || level == null || oven.bakesEachPlace()) {
            return;
        }
        Optional<KitchenOven.Baking> recipe = onTray(level);
        ItemStack waiting = oven.output(0);
        if (recipe.isEmpty() || waiting.isEmpty() || room(recipe.get()) >= Math.max(1, recipe.get().result().getCount())) {
            return;
        }
        oven.setOutput(0, ItemStack.EMPTY);
        oven.onTaken(cook, waiting.copy());
        giveBack(cook, waiting);
        cook.getInventory().setChanged();
    }

    /** The cook changed the tray or the bakeware: the oven takes it in once it lies still and makes a dish. */
    private void changed(Level level) {
        if (level != null && !level.isClientSide()) {
            armed = true;
            changedAt = level.getGameTime();
        }
    }

    public static boolean isOpen(StationContext station) {
        return station.state().getControl(OvenStation.DOOR) > 0.5F;
    }

    /** Whether something on the tray is still baking. */
    private boolean isBaking() {
        for (int place = 0; place < oven.places(); place++) {
            if (oven.ticksLeft(place) > 0 && oven.progress(place) > 0.0F) {
                return true;
            }
        }
        return false;
    }

    /** The door as the station has it, and the block's own {@code open} where it has one, so the oven looks right to everybody. */
    private void setDoor(StationContext station, boolean open) {
        station.state().setControl(OvenStation.DOOR, open ? 1.0F : 0.0F);
        BlockState block = station.level().getBlockState(station.pos());
        if (block.hasProperty(BlockStateProperties.OPEN) && block.getValue(BlockStateProperties.OPEN) != open) {
            station.level().setBlock(station.pos(), block.setValue(BlockStateProperties.OPEN, open), 3);
        }
    }

    /** Things go into the oven and come out only with the door open, and only what the oven takes there; tools stand on top. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (oven == null) {
            return false;
        }
        return switch (slot.group()) {
            case OvenStation.INGREDIENTS -> isOpen(station) && oven.takes(stack);
            case OvenStation.FUEL -> isOpen(station) && oven.isFuel(stack);
            case OvenStation.TOOLS -> oven.isTool(stack);
            case OvenStation.BAKEWARE -> isOpen(station) && oven.isBakeware(stack);
            default -> false;
        };
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return oven != null && (slot.group().equals(OvenStation.TOOLS) || isOpen(station));
    }

    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (oven != null && slot.group().equals(OvenStation.SERVED)) {
            oven.onTaken(player, stack);
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (oven == null) {
            return;
        }
        switch (action) {
            case OvenStation.ACTION_DOOR -> {
                // Opened or shut by hand: the cook decides, the oven takes nothing in by itself until they change the tray again. A
                // request for what already is (the client opening for food the server already opened for) changes nothing.
                if (isOpen(station) != data.getBooleanOr(OvenStation.OPEN, false)) {
                    armed = false;
                }
                setDoor(station, data.getBooleanOr(OvenStation.OPEN, false));
            }
            case OvenStation.ACTION_LAY -> {
                lastCook = player;
                if (!isOpen(station) && (!isBaking() || !oven.bakesEachPlace())) {
                    // The cook came with food: the door opens (the tray comes out) for it. An oven that bakes a set holds it while open.
                    setDoor(station, true);
                }
                if (isOpen(station)) {
                    lay(player, data.contains(OvenStation.FROM) ? data.getIntOr(OvenStation.FROM, 0) : -1,
                            data.contains(OvenStation.PLACE) ? data.getIntOr(OvenStation.PLACE, 0) : -1, data.getBooleanOr(OvenStation.ONE, false),
                            data.getBooleanOr(OvenStation.ALL, false));
                    bringBakeware(player, station.level());
                    changed(station.level());
                }
            }
            case OvenStation.ACTION_FILL -> {
                lastCook = player;
                Identifier id = Identifier.tryParse(data.getStringOr(OvenStation.DISH, ""));
                oven.recipes(station.level()).stream().filter(recipe -> recipe.id().equals(id)).findFirst().ifPresent(recipe -> {
                    // A recipe picked in the book opens the door for its ingredients to go in.
                    setDoor(station, true);
                    if (oven.bakesEachPlace()) {
                        fillPlaces(player, recipe, data.getBooleanOr(OvenStation.ALL, false));
                    } else {
                        int sets = data.getBooleanOr(OvenStation.ALL, false) ? FILL_MOST : 1;
                        if (oven.usesBakeware()) {
                            bakewareFor(player, recipe, sets);
                        }
                        fill(player, recipe, sets);
                    }
                    changed(station.level());
                });
            }
            case OvenStation.ACTION_FUEL -> {
                if (isOpen(station) && oven.burnsFuel()) {
                    fuel(player, data.getBooleanOr(OvenStation.FROM_INVENTORY, false), data.contains(OvenStation.FROM) ? data.getIntOr(OvenStation.FROM, 0) : -1);
                }
            }
            default -> {
            }
        }
    }

    /**
     * The stack on the cursor ({@code from} -1) or in inventory slot {@code from} goes on the tray. An oven that bakes each place
     * alone gets one a free place, onto {@code place} only with {@code one}; with {@code all}, the inventory's alike things follow
     * into the places still free. Any other: onto the same thing where it lies, else into the first free place, then shared out.
     */
    private void lay(Player player, int from, int place, boolean one, boolean all) {
        Inventory inventory = player.getInventory();
        if (from >= Inventory.INVENTORY_SIZE) {
            return;
        }
        ItemStack stack = from >= 0 ? inventory.getItem(from) : player.inventoryMenu.getCarried();
        if (oven.usesBakeware() && oven.isBakeware(stack)) {
            layBakeware(player, stack, all);
            if (from < 0) {
                player.inventoryMenu.setCarried(stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
            inventory.setChanged();
            return;
        }
        if (stack.isEmpty() || !oven.takes(stack)) {
            return;
        }
        if (oven.bakesEachPlace()) {
            ItemStack kind = stack.copyWithCount(1);
            List<Integer> free = freePlaces(place, one);
            int laid = 0;
            for (int at : free) {
                if (stack.isEmpty()) {
                    break;
                }
                oven.setPlace(at, stack.split(oven.perPlace()));
                laid++;
            }
            if (all && !one) {
                for (int at : free.subList(laid, free.size())) {
                    int source = sourceOf(inventory, kind);
                    if (source < 0) {
                        break;
                    }
                    oven.setPlace(at, inventory.removeItem(source, oven.perPlace()));
                }
            }
        } else {
            layOnSet(stack, one);
        }
        if (from < 0) {
            player.inventoryMenu.setCarried(stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        inventory.setChanged();
    }

    /**
     * Bakeware let go over the oven stands in it: one of it, or the whole stack with {@code all} (bowls a recipe uses up, one a bake).
     * More of what stands there joins it; other bakeware standing there goes back to the cook.
     */
    private void layBakeware(Player player, ItemStack stack, boolean all) {
        ItemStack there = oven.bakeware();
        if (!there.isEmpty() && !ItemStack.isSameItemSameComponents(there, stack)) {
            giveBack(player, there.copy());
            there = ItemStack.EMPTY;
        }
        int room = stack.getMaxStackSize() - there.getCount();
        int moved = Math.min(all ? stack.getCount() : 1, room);
        if (moved <= 0) {
            return;
        }
        oven.setBakeware(there.isEmpty() ? stack.copyWithCount(moved) : there.copyWithCount(there.getCount() + moved));
        stack.shrink(moved);
    }

    private static void giveBack(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    /**
     * The bakeware a recipe picked in the book bakes in stands in the oven: what stands there of another kind goes back to the cook and
     * theirs comes from the inventory, as many as {@code sets} bakes use up where the recipe uses it up. Nothing changes when the cook has
     * none of it: the food still goes in and the badge shows the bakeware it waits for.
     */
    private void bakewareFor(Player player, KitchenOven.Baking recipe, int sets) {
        if (recipe.bakeware().isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        ItemStack there = oven.bakeware();
        boolean right = recipe.bakesIn(there);
        int wanted = recipe.usesUp() ? sets : 1;
        if (right && there.getCount() >= wanted) {
            return;
        }
        int source = -1;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && source < 0; slot++) {
            if (recipe.bakesIn(inventory.getItem(slot)) && !inventory.getItem(slot).isEmpty()) {
                source = slot;
            }
        }
        if (source < 0) {
            return;
        }
        if (!right) {
            if (!there.isEmpty()) {
                giveBack(player, there.copy());
            }
            there = ItemStack.EMPTY;
        }
        while (source >= 0 && there.getCount() < wanted) {
            ItemStack taken = inventory.removeItem(source, wanted - there.getCount());
            there = there.isEmpty() ? taken : there.copyWithCount(there.getCount() + taken.getCount());
            source = -1;
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE && source < 0; slot++) {
                if (ItemStack.isSameItemSameComponents(inventory.getItem(slot), there)) {
                    source = slot;
                }
            }
        }
        oven.setBakeware(there);
        inventory.setChanged();
    }

    /**
     * What lies on the tray makes a dish only in bakeware other than what stands there (or none): the cook's own of it stands in the oven
     * from the inventory, the other back to them, as the book does. Nothing changes when they have none: the badge shows the one it waits
     * for. The tray is laid out again for what that bakeware makes.
     */
    private void bringBakeware(Player player, Level level) {
        if (!oven.usesBakeware() || oven.bakesEachPlace() || level == null || level.isClientSide()) {
            return;
        }
        List<KitchenOven.Baking> recipes = oven.recipes(level);
        ItemStack[] grid = grid();
        if (KitchenOven.matching(recipes, grid, oven.bakeware()).isPresent()) {
            return;
        }
        Optional<KitchenOven.Baking> recipe = KitchenOven.matching(recipes, grid);
        if (recipe.isPresent() && !recipe.get().bakeware().isEmpty()) {
            bakewareFor(player, recipe.get(), 1);
            arrange(level);
        }
    }

    /** The free places, from the first; just {@code place} when it is free, with {@code one}. */
    private List<Integer> freePlaces(int place, boolean one) {
        List<Integer> free = new ArrayList<>();
        if (one && place >= 0 && place < oven.places() && oven.place(place).isEmpty()) {
            free.add(place);
            return free;
        }
        for (int at = 0; at < oven.places(); at++) {
            if (oven.place(at).isEmpty()) {
                free.add(at);
                if (one) {
                    break;
                }
            }
        }
        return free;
    }

    private static int sourceOf(Inventory inventory, ItemStack kind) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (ItemStack.isSameItemSameComponents(inventory.getItem(slot), kind)) {
                return slot;
            }
        }
        return -1;
    }

    /** One of {@code stack} with {@code one} (a right click, as into a crafting grid), else as much as a place holds. */
    private void layOnSet(ItemStack stack, boolean one) {
        int place = -1;
        for (int slot = 0; slot < oven.places() && place < 0; slot++) {
            ItemStack there = oven.place(slot);
            if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < Math.min(oven.perPlace(), there.getMaxStackSize())) {
                place = slot;
            }
        }
        for (int slot = 0; slot < oven.places() && place < 0; slot++) {
            if (oven.place(slot).isEmpty()) {
                place = slot;
            }
        }
        if (place < 0) {
            return;
        }
        ItemStack there = oven.place(place);
        int moved = Math.min(one ? 1 : stack.getCount(), Math.min(oven.perPlace(), stack.getMaxStackSize()) - there.getCount());
        oven.setPlace(place, there.isEmpty() ? stack.copyWithCount(moved) : there.copyWithCount(there.getCount() + moved));
        stack.shrink(moved);
        arrange(oven.blockEntity().getLevel());
    }

    /** A recipe of one thing picked in the book: one of it from the inventory onto a free place, onto every free place with Shift. */
    private void fillPlaces(Player player, KitchenOven.Baking recipe, boolean all) {
        if (recipe.ingredients().size() != 1) {
            return;
        }
        Ingredient wanted = recipe.ingredients().get(0);
        Inventory inventory = player.getInventory();
        for (int at : freePlaces(-1, !all)) {
            int source = -1;
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE && source < 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && wanted.test(stack) && oven.takes(stack)) {
                    source = slot;
                }
            }
            if (source < 0) {
                break;
            }
            oven.setPlace(at, inventory.removeItem(source, oven.perPlace()));
        }
        inventory.setChanged();
    }

    /**
     * Fuel goes into the oven: the stack on the cursor, the one in inventory slot {@code from}, or with an empty hand the inventory's
     * first fuel (the kind already burning first), as much as the slot holds.
     */
    private void fuel(Player player, boolean fromInventory, int from) {
        Inventory inventory = player.getInventory();
        ItemStack there = oven.fuel();
        if (from >= Inventory.INVENTORY_SIZE) {
            return;
        }
        if (fromInventory) {
            from = fuelSlot(oven, inventory);
            if (from < 0) {
                return;
            }
        }
        ItemStack stack;
        if (from >= 0) {
            stack = inventory.getItem(from);
        } else {
            stack = player.inventoryMenu.getCarried();
        }
        if (stack.isEmpty() || !oven.isFuel(stack) || !there.isEmpty() && !ItemStack.isSameItemSameComponents(there, stack)) {
            return;
        }
        int moved = Math.min(stack.getCount(), stack.getMaxStackSize() - there.getCount());
        if (moved <= 0) {
            return;
        }
        oven.setFuel(there.isEmpty() ? stack.copyWithCount(moved) : there.copyWithCount(there.getCount() + moved));
        stack.shrink(moved);
        if (from < 0) {
            player.inventoryMenu.setCarried(stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        inventory.setChanged();
    }

    /** The inventory slot whose fuel an empty hand is offered: the kind in the oven, else the first fuel; -1 for none. */
    public static int fuelSlot(KitchenOven oven, Inventory inventory) {
        ItemStack there = oven.fuel();
        int first = -1;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !oven.isFuel(stack)) {
                continue;
            }
            if (there.isEmpty()) {
                return slot;
            }
            if (ItemStack.isSameItemSameComponents(there, stack)) {
                return there.getCount() < there.getMaxStackSize() ? slot : -1;
            }
            first = first < 0 ? slot : first;
        }
        return there.isEmpty() ? first : -1;
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        if (oven != null && !oven.bakesEachPlace() && slot.group().equals(OvenStation.INGREDIENTS)) {
            arrange(station.level());
        }
        // Taken off or moved by the cook at the open tray: the oven waits again before it takes in what is left.
        if (oven != null && isOpen(station) && !arranging
                && (slot.group().equals(OvenStation.INGREDIENTS) || slot.group().equals(OvenStation.BAKEWARE))) {
            changed(station.level());
        }
    }

    /** The tray's places as slots; laying them out is no cook's change. */
    private SlotStorage tray() {
        return new SlotStorage() {
            @Override
            public int size() {
                return oven.places();
            }

            @Override
            public ItemStack get(int index) {
                return oven.place(index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                arranging = true;
                try {
                    oven.setPlace(index, stack);
                } finally {
                    arranging = false;
                }
            }
        };
    }

    /**
     * An oven that bakes one of each place at a time makes no pie of three cinnamon sticks laid as one stack: what lies on the tray is
     * laid out over its places the way the recipe it makes wants it ({@link StationRecipes#choose}), as a cook spreads the sticks
     * out. Recipes laid out in the grid are left as the cook laid them.
     */
    private void arrange(Level level) {
        if (arranging || level == null) {
            return;
        }
        SlotStorage tray = tray();
        List<ItemStack> kinds = StationRecipes.kinds(tray);
        if (kinds.isEmpty()) {
            picked = null;
            return;
        }
        List<KitchenOven.Baking> recipes = inBakeware(oven.recipes(level));
        Optional<KitchenOven.Baking> now = KitchenOven.matching(recipes, grid());
        if (now.isPresent() && now.get().isShaped()) {
            return;
        }
        List<KitchenOven.Baking> loose = recipes.stream().filter(recipe -> !recipe.isShaped()).toList();
        StationRecipes.Choice<KitchenOven.Baking> choice = StationRecipes.choose(StationRecipes.candidates(loose, KitchenOven.Baking::id,
                KitchenOven.Baking::ingredients), kinds, oven.places(), picked, now.map(KitchenOven.Baking::id).orElse(null), false);
        if (choice != null) {
            StationRecipes.lay(tray, kinds, choice.parts());
        }
    }

    /**
     * The recipes of the bakeware standing in the oven, for an oven that bakes in bakeware and has some in it: what lies there is laid
     * out for what that bakeware makes. All of them otherwise.
     */
    private List<KitchenOven.Baking> inBakeware(List<KitchenOven.Baking> recipes) {
        ItemStack there = oven.bakeware();
        if (!oven.usesBakeware() || there.isEmpty()) {
            return recipes;
        }
        return recipes.stream().filter(recipe -> recipe.bakesIn(there)).toList();
    }

    /**
     * The bakeware what lies there wants while other bakeware or none stands in the oven: the set makes a recipe, not in this one. Empty
     * when it bakes, when nothing lies there, or when it makes nothing at all.
     */
    private ItemStack wantedBakeware(Level level) {
        if (!oven.usesBakeware() || level == null || StationRecipes.kinds(tray()).isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<KitchenOven.Baking> recipes = oven.recipes(level);
        ItemStack[] grid = grid();
        if (KitchenOven.matching(recipes, grid, oven.bakeware()).isPresent()) {
            return ItemStack.EMPTY;
        }
        return KitchenOven.matching(recipes, grid).map(KitchenOven.Baking::bakeware).orElse(ItemStack.EMPTY);
    }

    private ItemStack[] grid() {
        ItemStack[] grid = new ItemStack[oven.places()];
        for (int slot = 0; slot < grid.length; slot++) {
            grid[slot] = oven.place(slot);
        }
        return grid;
    }

    /**
     * Puts up to {@code sets} sets of the recipe's ingredients from the inventory on the tray: a laid-out recipe where its grid has
     * them, any other laid out the way the oven reads it. Never more sets than the output slot has room for when baked.
     */
    private void fill(Player player, KitchenOven.Baking recipe, int sets) {
        if (!recipe.isShaped()) {
            fillLoose(player, recipe, sets);
            return;
        }
        int[] places = places(recipe);
        List<Ingredient> wanted = recipe.needed();
        if (places == null || wanted.isEmpty()) {
            return;
        }
        ItemStack[] onTray = new ItemStack[oven.places()];
        for (int slot = 0; slot < onTray.length; slot++) {
            onTray[slot] = oven.place(slot).copy();
        }
        // What lies there must be the start of the same recipe: nothing in a place the recipe leaves empty, nothing else in its places.
        for (int slot = 0; slot < onTray.length; slot++) {
            int index = indexOf(places, slot);
            if (index < 0 ? !onTray[slot].isEmpty() : !onTray[slot].isEmpty() && !wanted.get(index).test(onTray[slot])) {
                return;
            }
        }
        int most = Math.min(sets, Math.max(1, room(recipe) / Math.max(1, recipe.result().getCount())) - setsOnTray(onTray, places));
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
                oven.setPlace(place, onTray[place]);
            }
            inventory.setChanged();
        }
    }

    /** How many of the recipe's dish the first output slot still takes. */
    private int room(KitchenOven.Baking recipe) {
        ItemStack output = oven.output(0);
        return output.isEmpty() ? recipe.result().getMaxStackSize()
                : ItemStack.isSameItemSameComponents(output, recipe.result()) ? output.getMaxStackSize() - output.getCount() : 0;
    }

    /**
     * A recipe of no grid: its ingredients go on the tray laid out the way the oven reads it, whatever lies there of it already; the
     * picked recipe stays the oven's choice while the tray's food makes it. Never more sets than the output slot has room for.
     */
    private void fillLoose(Player player, KitchenOven.Baking recipe, int sets) {
        SlotStorage tray = tray();
        List<ItemStack> kinds = StationRecipes.kinds(tray);
        int[] parts = kinds.isEmpty() ? null : StationRecipes.parts(recipe.ingredients(), kinds);
        int already = parts == null ? 0 : StationRecipes.servings(kinds, parts);
        int most = Math.min(sets, Math.max(1, room(recipe) / Math.max(1, recipe.result().getCount())) - already);
        if (most <= 0) {
            return;
        }
        StationRecipes.Filled filled = StationRecipes.fill(player.getInventory(), recipe.ingredients(), kinds, oven.places(), most);
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
            if (count > KitchenOven.GRID * KitchenOven.GRID) {
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
        List<Integer> places = new ArrayList<>();
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

    /**
     * What the clients are told: how far it has baked (the place furthest on of those still baking, or the set), the ticks until the
     * next thing is done in whole seconds, each place's progress in hundredths, whether it is hot and whether things wait for heat.
     */
    private void refresh(StationContext station) {
        StationState state = station.state();
        float furthest = 0.0F;
        int left = 0;
        byte[] baked = new byte[oven.places()];
        for (int place = 0; place < oven.places(); place++) {
            float progress = oven.progress(place);
            int ticks = oven.ticksLeft(place);
            // In steps of two hundredths: the clients hear of it every few ticks, not every one.
            baked[place] = (byte) (Math.round(Mth.clamp(progress, 0.0F, 1.0F) * 50.0F) * 2);
            if (ticks > 0) {
                furthest = Math.max(furthest, progress);
                left = left == 0 ? ticks : Math.min(left, ticks);
            }
        }
        state.setControl(OvenStation.PROGRESS, furthest);
        CompoundTag own = state.data().copy();
        int seconds = left > 0 ? Mth.ceil(left / 20.0F) * 20 : 0;
        boolean hot = oven.isHot();
        boolean cold = oven.waitsForHeat();
        ItemStack wanted = wantedBakeware(station.level());
        String wants = wanted.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(wanted.getItem()).toString();
        if (own.getIntOr(OvenStation.LEFT, 0) != seconds || !Arrays.equals(own.getByteArray(OvenStation.BAKED).orElse(new byte[0]), baked)
                || own.getBooleanOr(OvenStation.HOT, false) != hot || own.getBooleanOr(OvenStation.COLD, false) != cold
                || !own.getString(OvenStation.WANTS).equals(wants)) {
            own.putInt(OvenStation.LEFT, seconds);
            own.putByteArray(OvenStation.BAKED, baked);
            own.putBoolean(OvenStation.HOT, hot);
            own.putBoolean(OvenStation.COLD, cold);
            if (wants.isEmpty()) {
                own.remove(OvenStation.WANTS);
            } else {
                own.putString(OvenStation.WANTS, wants);
            }
            state.setData(own);
        }
    }
}
