package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.List;
import java.util.Optional;

/**
 * Extra Delight's vat by hand: the slots and the tank are the vat's own, aging is left to the vat. The lid is Extra Delight's lid block
 * on top of it: lifted off and set aside while the cook works (kept in the vat, not in the world), and put where the stage under way
 * wants it when the last cook leaves: back on, or into the cook's hands for a stage that ages open to the air; a stage that wants it
 * on while there is none takes the one the cook carries. It also runs on the clients near a vat nobody works at; there it only reads.
 */
public final class EdVatLogic implements StationLogic {
    /** The most sets of ingredients one pick in the book puts in. */
    private static final int FILL_MOST = 16;
    /** How full the vat looks, hundredths of its inside from the floor to the rim: a full tank stands this high, well under the rim. */
    private static final int BREW_MOST = 62;
    private static final int SERVINGS_FIRST = 40;
    private static final int SERVINGS_MORE = 2;

    private final KitchenEdVat vat;
    /** The places are being shared out: their own changes do not share them out again. */
    private boolean arranging;
    /** The recipe the cook last picked in the book: the vat's choice while its food makes it, until it is empty. */
    private Identifier picked;

    public EdVatLogic(StationContext station) {
        vat = KitchenEdVat.of(station.blockEntity());
        StationState state = station.state();
        state.bindSlots(EdVatStation.INGREDIENTS, storage(vat, 0, KitchenEdVat.INGREDIENTS));
        state.bindSlots(EdVatStation.CONTAINER, storage(vat, KitchenEdVat.CONTAINER_SLOT, 1));
        state.bindSlots(EdVatStation.STAGE, storage(vat, KitchenEdVat.STAGE_SLOT, 1));
        state.bindSlots(EdVatStation.SERVED, storage(vat, KitchenEdVat.OUTPUT_SLOT, 1));
        state.bindSlots(EdVatStation.SERVINGS, new SlotStorage() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public ItemStack get(int index) {
                return vat == null ? ItemStack.EMPTY : vat.handson$servings();
            }

            @Override
            public void set(int index, ItemStack stack) {
                if (vat != null) {
                    vat.handson$setServings(stack);
                }
            }
        });
    }

    private static SlotStorage storage(KitchenEdVat vat, int start, int count) {
        return new SlotStorage() {
            @Override
            public int size() {
                return count;
            }

            @Override
            public ItemStack get(int index) {
                return vat == null ? ItemStack.EMPTY : vat.handson$item(start + index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                if (vat != null) {
                    vat.handson$setItem(start + index, stack);
                }
            }
        };
    }

    // The lid.

    /** Where the vat's lid is: on it, set aside by the station, or nowhere. */
    public static int lid(Level level, BlockPos pos, KitchenEdVat vat) {
        if (KitchenEdVat.lidOn(level, pos)) {
            return EdVatStation.LID_ON;
        }
        return vat != null && !vat.handson$lidAside().isEmpty() ? EdVatStation.LID_ASIDE : EdVatStation.LID_NONE;
    }

    private int lid(StationContext station) {
        return lid(station.level(), station.pos(), vat);
    }

    /** The lid on top comes off and is set aside: the block goes, the station keeps it. */
    private void lift(StationContext station, Player player) {
        BlockPos above = station.pos().above();
        BlockState lid = station.level().getBlockState(above);
        if (!KitchenEdVat.isLid(lid)) {
            return;
        }
        ItemStack item = asItem(lid);
        station.level().removeBlock(above, false);
        if (vat.handson$lidAside().isEmpty()) {
            vat.handson$setLidAside(item);
        } else if (player != null) {
            give(player, item);
        } else {
            Block.popResource(station.level(), above, item);
        }
    }

    /**
     * A lid goes on: the one set aside, else one from the cook ({@code from}: an inventory slot, -1 the cursor, or the first lid
     * found). Not where something stands on the vat.
     */
    private boolean cover(StationContext station, Player player, int from) {
        BlockPos above = station.pos().above();
        if (KitchenEdVat.lidOn(station.level(), station.pos()) || !station.level().getBlockState(above).canBeReplaced()) {
            return false;
        }
        ItemStack lid = vat.handson$lidAside();
        boolean aside = !lid.isEmpty();
        ItemStack source = ItemStack.EMPTY;
        if (!aside) {
            if (player == null) {
                return false;
            }
            source = from >= 0 && from < Inventory.INVENTORY_SIZE ? player.getInventory().getItem(from)
                    : from == -1 ? player.inventoryMenu.getCarried() : ItemStack.EMPTY;
            if (!KitchenEdVat.isLid(source)) {
                int slot = lidSlot(player.getInventory());
                source = slot < 0 ? ItemStack.EMPTY : player.getInventory().getItem(slot);
            }
            if (!KitchenEdVat.isLid(source)) {
                return false;
            }
            lid = source.copyWithCount(1);
        }
        station.level().setBlock(above, asBlock(lid), Block.UPDATE_ALL);
        if (aside) {
            vat.handson$setLidAside(ItemStack.EMPTY);
        } else {
            source.shrink(1);
            player.getInventory().setChanged();
        }
        return true;
    }

    /** The first inventory slot holding Extra Delight's lid, or -1. */
    public static int lidSlot(Inventory inventory) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (KitchenEdVat.isLid(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /** The lid block as its item, its look (Extra Delight's style) kept, as taking it up by hand gives it. */
    private static ItemStack asItem(BlockState lid) {
        ItemStack item = new ItemStack(lid.getBlock());
        BlockItemStateProperties properties = BlockItemStateProperties.EMPTY;
        for (Property<?> property : lid.getProperties()) {
            properties = with(properties, property, lid);
        }
        item.set(DataComponents.BLOCK_STATE, properties);
        return item;
    }

    private static <T extends Comparable<T>> BlockItemStateProperties with(BlockItemStateProperties properties, Property<T> property,
            BlockState state) {
        return properties.with(property, state.getValue(property));
    }

    /** The lid item as the block it places. */
    public static BlockState asBlock(ItemStack lid) {
        BlockState state = KitchenEdVat.lid().defaultBlockState();
        BlockItemStateProperties properties = lid.get(DataComponents.BLOCK_STATE);
        return properties == null ? state : properties.apply(state);
    }

    /**
     * Whether the vat wants its lid on now: true while a stage under way ages under the lid, false while one ages open to the air,
     * null while nothing ages (it stays as the cook found it).
     */
    public Boolean wantsLid(Level level) {
        return current(level).flatMap(recipe -> recipe.stage(vat.handson$stage())).map(KitchenEdVat.Stage::lid).orElse(null);
    }

    /** Whether the vat ages right now as it stands: a recipe, its liquid, the stage's thing in place and the lid as the stage wants it. */
    private boolean ages(StationContext station) {
        Optional<KitchenEdVat.Aging> recipe = current(station.level());
        if (recipe.isEmpty()) {
            return false;
        }
        Optional<KitchenEdVat.Stage> stage = recipe.get().stage(vat.handson$stage());
        return stage.isPresent() && stage.get().fits(vat.handson$item(KitchenEdVat.STAGE_SLOT))
                && stage.get().lid() == (lid(station) == EdVatStation.LID_ON);
    }

    /** The recipe what lies in the vat makes with the liquid in it, if any. */
    public Optional<KitchenEdVat.Aging> current(Level level) {
        if (vat == null) {
            return Optional.empty();
        }
        return KitchenEdVat.making(KitchenEdVat.recipes(level), places(), vat.handson$fluid(), vat.handson$amount())
                .filter(recipe -> recipe.takes(vat.handson$fluid(), vat.handson$amount()));
    }

    /**
     * The last cook goes: the lid goes where the stage under way wants it. Set aside, it goes back on, or to the cook for a stage that
     * ages open; on, it comes off for such a stage; wanted on and nowhere, the cook's own goes on. Nothing ageing, a lid set aside
     * goes back on, as the vat was found.
     */
    private void settle(StationContext station, Player player) {
        Boolean wanted = wantsLid(station.level());
        int lid = lid(station);
        if (wanted == null && lid == EdVatStation.LID_ASIDE || Boolean.TRUE.equals(wanted) && lid != EdVatStation.LID_ON) {
            cover(station, player, -2);
        } else if (Boolean.FALSE.equals(wanted) && lid == EdVatStation.LID_ON) {
            lift(station, player);
        }
        // What must stay off, or could not go back on (something stands on the vat), goes to the cook, or onto the vat's top
        // where nobody is left to take it.
        ItemStack aside = vat.handson$lidAside();
        if (!aside.isEmpty()) {
            vat.handson$setLidAside(ItemStack.EMPTY);
            if (player != null) {
                give(player, aside);
            } else {
                Block.popResource(station.level(), station.pos().above(), aside);
            }
        }
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        if (vat == null) {
            return;
        }
        if (!station.level().isClientSide()) {
            // The cook comes to work at it: the lid comes off, unless what is in it ages under it as it should.
            if (lid(station) == EdVatStation.LID_ON && !ages(station)) {
                lift(station, player);
            }
            giveBackContainers(station.level(), player);
        }
        refresh(station);
    }

    @Override
    public void onClose(StationContext station, Player player) {
        if (vat != null && !station.level().isClientSide() && station.users().stream().allMatch(user -> user == player)) {
            settle(station, player);
        }
    }

    @Override
    public void tick(StationContext station) {
        if (vat == null) {
            return;
        }
        if (!station.level().isClientSide() && station.users().isEmpty() && !vat.handson$lidAside().isEmpty()) {
            // Nobody left to take the lid: it goes as the vat wants it.
            settle(station, null);
        }
        refresh(station);
    }

    /** Nothing goes in or comes out through the lid; containers, liquids and lids never lie in the places. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (lid(station) == EdVatStation.LID_ON || !slot.group().equals(EdVatStation.INGREDIENTS)) {
            return false;
        }
        List<KitchenEdVat.Aging> recipes = KitchenEdVat.recipes(station.level());
        return !KitchenEdVat.isContainer(recipes, stack) && !KitchenEdVat.isStageItem(recipes, stack) && !KitchenEdVat.isLid(stack)
                && !isLiquid(stack);
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return lid(station) != EdVatStation.LID_ON;
    }

    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(EdVatStation.SERVED_SLOT)) {
            stack.onCraftedBy(player, stack.getCount());
        }
    }

    private static boolean isLiquid(ItemStack stack) {
        KitchenEdVat.Access access = KitchenEdVat.access();
        return access != null && access.liquidOf(stack).isPresent();
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (vat == null) {
            return;
        }
        int from = data.contains(EdVatStation.FROM) ? data.getIntOr(EdVatStation.FROM, 0) : -1;
        switch (action) {
            case EdVatStation.ACTION_LID -> {
                if (lid(station) == EdVatStation.LID_ON) {
                    lift(station, player);
                } else {
                    cover(station, player, from);
                }
            }
            case EdVatStation.ACTION_PUT -> put(station, player, from, data.getBooleanOr(EdVatStation.ALL, false));
            case EdVatStation.ACTION_FILL -> {
                Identifier id = Identifier.tryParse(data.getStringOr(EdVatStation.DISH, ""));
                KitchenEdVat.recipes(station.level()).stream().filter(recipe -> recipe.id().equals(id)).findFirst().ifPresent(recipe -> {
                    // A recipe picked in the book takes the lid off for its things to go in.
                    lift(station, player);
                    fill(station, player, recipe, data.getBooleanOr(EdVatStation.ALL, false) ? FILL_MOST : 1);
                    giveBackContainers(station.level(), player);
                });
            }
            case EdVatStation.ACTION_TAKE -> {
                if (lid(station) != EdVatStation.LID_ON) {
                    take(station, player, data.getBooleanOr(EdVatStation.ALL, false), from);
                }
            }
            case EdVatStation.ACTION_SCOOP -> {
                if (lid(station) != EdVatStation.LID_ON) {
                    scoop(player, from);
                }
            }
            default -> {
            }
        }
        refresh(station);
    }

    private static ItemStack source(Player player, int from) {
        return from >= 0 && from < Inventory.INVENTORY_SIZE ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
    }

    /**
     * What the cook brings goes in: a lid onto the vat; with the lid off for it, a liquid poured into the tank (one container, or the
     * whole stack), the thing of a stage into the stage place (one; one there before goes back), anything else onto the same thing
     * where it lies or into the first free place. Containers never go in: the client draws what is made into them instead.
     */
    private void put(StationContext station, Player player, int from, boolean all) {
        ItemStack stack = source(player, from);
        if (stack.isEmpty()) {
            return;
        }
        if (KitchenEdVat.isLid(stack)) {
            cover(station, player, from);
            return;
        }
        List<KitchenEdVat.Aging> recipes = KitchenEdVat.recipes(station.level());
        if (KitchenEdVat.isContainer(recipes, stack)) {
            return;
        }
        lift(station, player);
        KitchenEdVat.Access access = KitchenEdVat.access();
        if (access != null && access.liquidOf(stack).isPresent()) {
            int times = all ? stack.getCount() : 1;
            for (int i = 0; i < times && !stack.isEmpty(); i++) {
                ItemStack left = vat.handson$pour(stack);
                if (left == null) {
                    break;
                }
                stack.shrink(1);
                if (!left.isEmpty()) {
                    give(player, left);
                }
            }
        } else if (KitchenEdVat.isStageItem(recipes, stack)) {
            ItemStack there = vat.handson$item(KitchenEdVat.STAGE_SLOT);
            if (ItemStack.isSameItemSameComponents(there, stack)) {
                return;
            }
            vat.handson$setItem(KitchenEdVat.STAGE_SLOT, stack.split(1));
            if (!there.isEmpty()) {
                give(player, there);
            }
        } else if (recipes.stream().anyMatch(recipe -> recipe.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack)))) {
            putInPlaces(station.level(), stack, all);
        }
        player.getInventory().setChanged();
        if (from == -1) {
            player.inventoryMenu.setCarried(stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
    }

    private void putInPlaces(Level level, ItemStack stack, boolean all) {
        int place = -1;
        for (int slot = 0; slot < KitchenEdVat.INGREDIENTS && place < 0; slot++) {
            ItemStack there = vat.handson$item(slot);
            if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
                place = slot;
            }
        }
        for (int slot = 0; slot < KitchenEdVat.INGREDIENTS && place < 0; slot++) {
            if (vat.handson$item(slot).isEmpty()) {
                place = slot;
            }
        }
        if (place < 0) {
            return;
        }
        ItemStack there = vat.handson$item(place);
        int moved = Math.min(all ? stack.getCount() : 1, stack.getMaxStackSize() - there.getCount());
        vat.handson$setItem(place, there.isEmpty() ? stack.copyWithCount(moved) : there.copyWithCount(there.getCount() + moved));
        stack.shrink(moved);
        arrange(level);
    }

    /**
     * What is made goes to the cook. A serving waiting in the vat is drawn into each container they bring: those on their cursor
     * ({@code from} -1) or in inventory slot {@code from}; one, or with shift as many as they brought containers for. Without a
     * container nothing is drawn. Anything in the output is taken as it lies, one or all.
     */
    private void take(StationContext station, Player player, boolean all, int from) {
        ItemStack servings = vat.handson$servings();
        if (!servings.isEmpty()) {
            boolean carried = from < 0 || from >= Inventory.INVENTORY_SIZE;
            ItemStack containers = carried ? player.inventoryMenu.getCarried() : player.getInventory().getItem(from);
            if (!KitchenEdVat.draws(KitchenEdVat.recipes(station.level()), servings, containers)) {
                return;
            }
            int drawn = Math.min(all ? servings.getCount() : 1, containers.getCount());
            boolean wholeStack = drawn == containers.getCount();
            containers.shrink(drawn);
            vat.handson$setServings(servings.copyWithCount(servings.getCount() - drawn));
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
            player.getInventory().setChanged();
            return;
        }
        ItemStack served = vat.handson$item(KitchenEdVat.OUTPUT_SLOT);
        if (served.isEmpty()) {
            return;
        }
        int count = all ? served.getCount() : 1;
        ItemStack given = served.copyWithCount(count);
        vat.handson$setItem(KitchenEdVat.OUTPUT_SLOT, served.copyWithCount(served.getCount() - count));
        given.onCraftedBy(player, count);
        give(player, given);
        player.getInventory().setChanged();
    }

    /** An empty container the cook brings ({@code from}) draws the liquid back out: a bucket a bucketful, a bottle a bottleful. */
    private void scoop(Player player, int from) {
        ItemStack empty = source(player, from);
        if (empty.isEmpty()) {
            return;
        }
        ItemStack full = vat.handson$draw(empty);
        if (full == null) {
            return;
        }
        empty.shrink(1);
        if (from < 0 && empty.isEmpty()) {
            player.inventoryMenu.setCarried(full);
        } else {
            give(player, full);
        }
        player.getInventory().setChanged();
    }

    /**
     * Containers put in the vat's container slot elsewhere (its own menu, a hopper) that are not all the recipe wants stop it from
     * ageing: they go back to the cook. What is made is drawn into containers from the cook's inventory, so the station puts none there.
     */
    private void giveBackContainers(Level level, Player player) {
        ItemStack there = vat.handson$item(KitchenEdVat.CONTAINER_SLOT);
        if (there.isEmpty()) {
            return;
        }
        Optional<KitchenEdVat.Aging> making = KitchenEdVat.matchingPlaces(KitchenEdVat.recipes(level), places());
        boolean fits = making.isPresent() && ItemStack.isSameItem(making.get().container(), there)
                && there.getCount() >= making.get().container().getCount();
        if (making.isPresent() && !fits) {
            vat.handson$setItem(KitchenEdVat.CONTAINER_SLOT, ItemStack.EMPTY);
            give(player, there);
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        if (vat == null || !slot.group().equals(EdVatStation.INGREDIENTS)) {
            return;
        }
        arrange(station.level());
        if (!station.level().isClientSide() && StationRecipes.kinds(placesAsSlots()).isEmpty() && vat.handson$stage() > 0) {
            // Emptied, the vat starts its next batch from the first stage: Extra Delight would go on at the stage it had reached.
            vat.handson$restart();
        }
    }

    /** The vat's places as slots; laying them out is no cook's change. */
    private SlotStorage placesAsSlots() {
        return new SlotStorage() {
            @Override
            public int size() {
                return KitchenEdVat.INGREDIENTS;
            }

            @Override
            public ItemStack get(int index) {
                return vat.handson$item(index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                arranging = true;
                try {
                    vat.handson$setItem(index, stack);
                } finally {
                    arranging = false;
                }
            }
        };
    }

    /**
     * The vat ages one of each place at a time, so four cucumbers dropped in as one stack make no gherkins: what lies in it is laid out
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
        List<KitchenEdVat.Aging> recipes = KitchenEdVat.recipes(level);
        Identifier found = KitchenEdVat.matchingPlaces(recipes, places()).map(KitchenEdVat.Aging::id).orElse(null);
        StationRecipes.Choice<KitchenEdVat.Aging> choice = StationRecipes.choose(StationRecipes.candidates(recipes, KitchenEdVat.Aging::id,
                KitchenEdVat.Aging::ingredients), kinds, KitchenEdVat.INGREDIENTS, picked, found, false);
        if (choice != null) {
            StationRecipes.lay(slots, kinds, choice.parts());
        }
    }

    private ItemStack[] places() {
        ItemStack[] places = new ItemStack[KitchenEdVat.INGREDIENTS];
        for (int slot = 0; slot < places.length; slot++) {
            places[slot] = vat.handson$item(slot);
        }
        return places;
    }

    /**
     * Puts up to {@code sets} sets of the recipe in from the inventory: its liquid as far as the tank lacks it (never another liquid
     * over the one in it), its ingredients each in its own place, and the thing its stage wants now when the stage place is empty.
     * Never more sets than the tank's liquid makes and the servings have room for.
     */
    private void fill(StationContext station, Player player, KitchenEdVat.Aging recipe, int sets) {
        Inventory inventory = player.getInventory();
        int most = Math.max(1, Math.min(sets, KitchenEdVat.CAPACITY / Math.max(1, recipe.amount())));
        ItemStack done = vat.handson$servings().isEmpty() ? vat.handson$item(KitchenEdVat.OUTPUT_SLOT) : vat.handson$servings();
        int room = done.isEmpty() ? recipe.result().getMaxStackSize()
                : ItemStack.isSameItemSameComponents(done, recipe.result()) ? done.getMaxStackSize() - done.getCount() : 0;
        most = Math.min(most, Math.max(1, room / Math.max(1, recipe.result().getCount())));
        SlotStorage slots = placesAsSlots();
        List<ItemStack> kinds = StationRecipes.kinds(slots);
        int[] parts = kinds.isEmpty() ? null : StationRecipes.parts(recipe.ingredients(), kinds);
        int already = parts == null ? 0 : StationRecipes.servings(kinds, parts);
        pourFor(player, recipe, recipe.amount() * Math.max(most, already));
        int more = most - already;
        if (more > 0) {
            StationRecipes.Filled filled = StationRecipes.fill(inventory, recipe.ingredients(), kinds, KitchenEdVat.INGREDIENTS, more);
            if (filled != null) {
                picked = recipe.id();
                StationRecipes.lay(slots, filled.kinds(), filled.parts());
            }
        }
        if (vat.handson$item(KitchenEdVat.STAGE_SLOT).isEmpty()) {
            int stage = vat.handson$stage() < recipe.stages().size() ? vat.handson$stage() : 0;
            recipe.stage(stage).filter(KitchenEdVat.Stage::wantsItem).ifPresent(wanted -> {
                for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (!stack.isEmpty() && wanted.ingredient().test(stack)) {
                        vat.handson$setItem(KitchenEdVat.STAGE_SLOT, stack.split(1));
                        break;
                    }
                }
            });
        }
        inventory.setChanged();
    }

    /** Pours the recipe's liquid in from the inventory until the tank holds {@code wanted} mB of it, buckets while a bucket fits. */
    private void pourFor(Player player, KitchenEdVat.Aging recipe, int wanted) {
        KitchenEdVat.Access access = KitchenEdVat.access();
        Fluid inside = vat.handson$fluid();
        if (access == null || inside != Fluids.EMPTY && recipe.fluids().stream().noneMatch(fluid -> fluid.isSame(inside))) {
            return;
        }
        Inventory inventory = player.getInventory();
        wanted = Math.min(KitchenEdVat.CAPACITY, wanted);
        boolean poured = true;
        while (vat.handson$amount() < wanted && poured) {
            poured = false;
            int lacking = wanted - vat.handson$amount();
            int best = -1;
            int bestAmount = 0;
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                ItemStack stack = inventory.getItem(slot);
                Optional<KitchenEdVat.Liquid> liquid = access.liquidOf(stack);
                if (liquid.isEmpty() || !recipe.fluids().stream().anyMatch(fluid -> fluid.isSame(liquid.get().fluid()))) {
                    continue;
                }
                int amount = liquid.get().amount();
                // The biggest container that does not overshoot what is lacking, else the smallest there is.
                boolean better = best < 0 || amount <= lacking && (bestAmount > lacking || amount > bestAmount)
                        || amount > lacking && bestAmount > lacking && amount < bestAmount;
                if (better) {
                    best = slot;
                    bestAmount = amount;
                }
            }
            if (best >= 0) {
                ItemStack stack = inventory.getItem(best);
                ItemStack left = vat.handson$pour(stack);
                if (left != null) {
                    stack.shrink(1);
                    if (!left.isEmpty()) {
                        give(player, left);
                    }
                    poured = true;
                }
            }
        }
    }

    /** How full the vat looks: its liquid, and what was made waiting in it, a little higher for every serving. */
    static int brew(int amount, ItemStack servings) {
        int liquid = Math.round((float) BREW_MOST * Mth.clamp(amount, 0, KitchenEdVat.CAPACITY) / KitchenEdVat.CAPACITY);
        int waiting = servings.isEmpty() ? 0 : Math.min(BREW_MOST, SERVINGS_FIRST + SERVINGS_MORE * (servings.getCount() - 1));
        return Math.max(liquid, waiting);
    }

    private void refresh(StationContext station) {
        StationState state = station.state();
        int time = vat.handson$time();
        int total = vat.handson$timeTotal();
        state.setControl(EdVatStation.PROGRESS, total > 0 ? Mth.clamp((float) time / total, 0.0F, 1.0F) : 0.0F);
        state.setControl(EdVatStation.LID, lid(station));
        int brew = brew(vat.handson$amount(), vat.handson$servings());
        TankContents tank = brew > 0 ? TankContents.of(VatLogic.BREW_LIQUID, brew) : TankContents.EMPTY;
        if (!state.getTank(EdVatStation.BREW).equals(tank)) {
            state.setTank(EdVatStation.BREW, tank);
        }
        Optional<KitchenEdVat.Aging> making = current(station.level());
        CompoundTag own = state.data().copy();
        CompoundTag next = own.copy();
        next.putInt(EdVatStation.STAGE_NOW, vat.handson$stage());
        next.putInt(EdVatStation.STAGES, making.map(recipe -> recipe.stages().size()).orElse(0));
        // In whole seconds, as the badge tells it.
        next.putInt(EdVatStation.LEFT, total > 0 ? Mth.ceil(Math.max(0, total - time) / 20.0F) * 20 : 0);
        Fluid fluid = vat.handson$fluid();
        next.putString(EdVatStation.FLUID, fluid == Fluids.EMPTY ? "" : BuiltInRegistries.FLUID.getKey(fluid).toString());
        next.putInt(EdVatStation.AMOUNT, vat.handson$amount());
        next.putString(EdVatStation.MAKING, making.map(recipe -> recipe.id().toString()).orElse(""));
        if (!next.equals(own)) {
            state.setData(next);
        }
    }
}
