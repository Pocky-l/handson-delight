package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
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
import java.util.Optional;

/**
 * Extra Delight's chiller by hand: the slots and the tank are the chiller's own, setting, the melting of the ice and the dripping
 * into the pan are left to it. What the cook brings goes where it belongs: ice into the chest, a container onto the rack, a liquid
 * into the tank, everything else into the four places, laid out one of each as the chiller uses them. It also runs on the clients
 * near a chiller nobody works at; there it only reads.
 */
public final class ChillerLogic implements StationLogic {
    /** The liquid in the container on the rack and the melt water in the pan, as the scene draws them; coloured by the client. */
    public static final ResourceLocation SYRUP_LIQUID = HandsOnDelight.id("syrup");
    public static final ResourceLocation MELT_WATER_LIQUID = HandsOnDelight.id("melt_water");
    /** The most sets one pick in the book sets up. */
    private static final int FILL_MOST = 16;
    /** The cold left of the melting piece is told to the clients in this many steps. */
    public static final int CHILL_STEPS = 20;

    private final KitchenChiller chiller;
    /** The places are being shared out: their own changes do not share them out again. */
    private boolean arranging;
    /** The recipe the cook last picked in the book: the chiller's choice while its things make it, until it is empty. */
    private ResourceLocation picked;

    public ChillerLogic(StationContext station) {
        chiller = KitchenChiller.of(station.blockEntity());
        StationState state = station.state();
        state.bindSlots(ChillerStation.INGREDIENTS, storage(0, KitchenChiller.PLACES));
        state.bindSlots(ChillerStation.CONTAINER, storage(KitchenChiller.CONTAINER_SLOT, 1));
        state.bindSlots(ChillerStation.SERVED, storage(KitchenChiller.OUTPUT_SLOT, 1));
        state.bindSlots(ChillerStation.ICE, storage(KitchenChiller.ICE_SLOT, 1));
    }

    private SlotStorage storage(int start, int count) {
        return new SlotStorage() {
            @Override
            public int size() {
                return count;
            }

            @Override
            public ItemStack get(int index) {
                return chiller == null ? ItemStack.EMPTY : chiller.handson$item(start + index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                if (chiller != null) {
                    chiller.handson$setItem(start + index, stack);
                }
            }
        };
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        refresh(station);
    }

    @Override
    public void tick(StationContext station) {
        refresh(station);
    }

    /** Things in the places, a container on the rack, ice in the chest: each only what some recipe or the cold can use. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        KitchenChiller.Access access = KitchenChiller.access();
        if (chiller == null || access == null) {
            return false;
        }
        List<KitchenChiller.Setting> recipes = KitchenChiller.recipes(station.level());
        return switch (slot.group()) {
            case ChillerStation.INGREDIENTS -> KitchenChiller.isThing(recipes, stack);
            case ChillerStation.CONTAINER -> KitchenChiller.isContainer(recipes, stack);
            case ChillerStation.ICE -> access.chillOf(stack) > 0;
            default -> false;
        };
    }

    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(ChillerStation.SERVED_SLOT)) {
            stack.onCraftedBy(station.level(), player, stack.getCount());
        }
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        if (chiller != null && (slot.group().equals(ChillerStation.INGREDIENTS) || slot.group().equals(ChillerStation.CONTAINER))) {
            arrange(station.level());
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (chiller == null || KitchenChiller.access() == null) {
            return;
        }
        int from = data.contains(ChillerStation.FROM) ? data.getInt(ChillerStation.FROM) : -1;
        switch (action) {
            case ChillerStation.ACTION_PUT -> put(station.level(), player, from, data.getBoolean(ChillerStation.ALL));
            case ChillerStation.ACTION_FILL -> {
                ResourceLocation id = ResourceLocation.tryParse(data.getString(ChillerStation.DISH));
                KitchenChiller.recipes(station.level()).stream().filter(recipe -> recipe.id().equals(id)).findFirst()
                        .ifPresent(recipe -> fill(station.level(), player, recipe, data.getBoolean(ChillerStation.ALL) ? FILL_MOST : 1));
            }
            case ChillerStation.ACTION_PAN -> pan(station, player, from);
            default -> {
            }
        }
        refresh(station);
    }

    private static ItemStack source(Player player, int from) {
        return from >= 0 && from < Inventory.INVENTORY_SIZE ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
    }

    /** What is left of the stack the cook used goes back where it was; an emptied cursor is emptied. */
    private static void settle(Player player, int from, ItemStack stack) {
        if (from >= 0 && from < Inventory.INVENTORY_SIZE) {
            player.getInventory().setItem(from, stack.isEmpty() ? ItemStack.EMPTY : stack);
        } else {
            player.inventoryMenu.setCarried(stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        player.getInventory().setChanged();
    }

    private static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * What the cook brings goes where it belongs. Ice into the chest. An empty bottle, bowl or bucket that takes the liquid in the
     * tank draws it back out, unless what is set up sets in it and none of it stands on the rack yet. A recipe's container onto the
     * rack, the one standing there back to the cook. A liquid is poured into the tank, one container of it or with {@code all} as
     * many as fit; one that is also a thing of some recipe is laid in instead where the recipes of the container on the rack want it
     * so, or when the tank holds another liquid that sets with it as a thing. Anything else that some recipe sets goes into the four
     * places.
     */
    private void put(Level level, Player player, int from, boolean all) {
        ItemStack stack = source(player, from);
        if (stack.isEmpty()) {
            return;
        }
        KitchenChiller.Access access = KitchenChiller.access();
        List<KitchenChiller.Setting> recipes = KitchenChiller.recipes(level);
        ItemStack rack = chiller.handson$item(KitchenChiller.CONTAINER_SLOT);
        if (access.chillOf(stack) > 0) {
            settle(player, from, moveInto(KitchenChiller.ICE_SLOT, stack, all ? stack.getCount() : 1));
            return;
        }
        boolean container = KitchenChiller.isContainer(recipes, stack);
        boolean thing = KitchenChiller.isThing(recipes, stack);
        boolean setsIn = container && !ItemStack.isSameItem(rack, stack)
                && KitchenChiller.setsIn(recipes, places(), chiller.handson$fluid(), chiller.handson$amount(), stack);
        if (chiller.handson$amount() > 0 && !setsIn && access.liquidOf(stack).isEmpty()) {
            ItemStack full = chiller.handson$draw(stack);
            if (full != null) {
                stack.shrink(1);
                if (from < 0 && stack.isEmpty()) {
                    settle(player, from, full);
                } else {
                    settle(player, from, stack);
                    give(player, full);
                }
                return;
            }
        }
        if (container && (setsIn || !thing || rack.isEmpty())) {
            if (!rack.isEmpty() && !ItemStack.isSameItemSameComponents(rack, stack)) {
                chiller.handson$setItem(KitchenChiller.CONTAINER_SLOT, ItemStack.EMPTY);
                ItemStack left = moveInto(KitchenChiller.CONTAINER_SLOT, stack, all ? stack.getCount() : 1);
                settle(player, from, left);
                give(player, rack);
            } else {
                settle(player, from, moveInto(KitchenChiller.CONTAINER_SLOT, stack, all ? stack.getCount() : 1));
            }
            arrange(level);
            return;
        }
        Optional<KitchenEdVat.Liquid> liquid = access.liquidOf(stack);
        if (liquid.isPresent() && (!thing || KitchenChiller.pours(recipes, rack, stack, liquid.get().fluid()))) {
            int times = all ? stack.getCount() : 1;
            boolean carried = from < 0 || from >= Inventory.INVENTORY_SIZE;
            int poured = 0;
            while (poured < times) {
                ItemStack left = chiller.handson$pour(stack);
                if (left == null) {
                    break;
                }
                poured++;
                stack.shrink(1);
                if (stack.isEmpty()) {
                    // The last one emptied stays in the hand that poured it; from the inventory its place is freed for it first.
                    settle(player, from, carried ? left : ItemStack.EMPTY);
                    if (!carried) {
                        give(player, left);
                    }
                    return;
                }
                give(player, left);
            }
            if (poured > 0 || !KitchenChiller.isThingIn(recipes, chiller.handson$fluid(), stack)) {
                player.getInventory().setChanged();
                return;
            }
        }
        if (thing) {
            putInPlaces(level, stack, all);
            settle(player, from, stack);
        }
    }

    /** Moves up to {@code count} of {@code stack} onto what lies in {@code slot} of the same kind, or into it empty: what is left. */
    private ItemStack moveInto(int slot, ItemStack stack, int count) {
        ItemStack there = chiller.handson$item(slot);
        if (!there.isEmpty() && !ItemStack.isSameItemSameComponents(there, stack)) {
            return stack;
        }
        int moved = Math.min(Math.min(count, stack.getCount()), stack.getMaxStackSize() - there.getCount());
        if (moved <= 0) {
            return stack;
        }
        chiller.handson$setItem(slot, stack.copyWithCount(there.getCount() + moved));
        return stack.copyWithCount(stack.getCount() - moved);
    }

    private void putInPlaces(Level level, ItemStack stack, boolean all) {
        int place = -1;
        for (int slot = 0; slot < KitchenChiller.PLACES && place < 0; slot++) {
            ItemStack there = chiller.handson$item(slot);
            if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
                place = slot;
            }
        }
        for (int slot = 0; slot < KitchenChiller.PLACES && place < 0; slot++) {
            if (chiller.handson$item(slot).isEmpty()) {
                place = slot;
            }
        }
        if (place < 0) {
            return;
        }
        ItemStack there = chiller.handson$item(place);
        int moved = Math.min(all ? stack.getCount() : 1, stack.getMaxStackSize() - there.getCount());
        chiller.handson$setItem(place, there.isEmpty() ? stack.copyWithCount(moved) : there.copyWithCount(there.getCount() + moved));
        stack.shrink(moved);
        arrange(level);
    }

    /**
     * The drip pan. A container the cook brings is filled from it: a bucket only from a full pan, a bottle from a bottleful. With
     * an empty hand ({@code from} -2) a bucket from the inventory takes a full pan; with none, or a pan not full, the pan is tipped
     * out and the water is gone.
     */
    private void pan(StationContext station, Player player, int from) {
        if (from == -2) {
            int bucket = chiller.handson$pan() >= KitchenChiller.PAN ? slotOf(player.getInventory(), Items.BUCKET) : -1;
            if (bucket >= 0) {
                drawPan(player, bucket);
            } else if (chiller.handson$pan() > 0) {
                chiller.handson$tipPan();
                CompoundTag own = station.state().data().copy();
                own.putInt(ChillerStation.TIPPED, own.getInt(ChillerStation.TIPPED) + 1);
                station.state().setData(own);
            }
            return;
        }
        drawPan(player, from);
    }

    private void drawPan(Player player, int from) {
        ItemStack empty = source(player, from);
        ItemStack full = empty.isEmpty() ? null : chiller.handson$drawPan(empty);
        if (full == null) {
            return;
        }
        empty.shrink(1);
        if (from < 0 && empty.isEmpty()) {
            settle(player, from, full);
        } else {
            settle(player, from, empty);
            give(player, full);
        }
    }

    private static int slotOf(Inventory inventory, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    private ItemStack[] places() {
        ItemStack[] places = new ItemStack[KitchenChiller.PLACES];
        for (int slot = 0; slot < places.length; slot++) {
            places[slot] = chiller.handson$item(slot);
        }
        return places;
    }

    /** The chiller's places as slots; laying them out is no cook's change. */
    private SlotStorage placesAsSlots() {
        return new SlotStorage() {
            @Override
            public int size() {
                return KitchenChiller.PLACES;
            }

            @Override
            public ItemStack get(int index) {
                return chiller.handson$item(index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                arranging = true;
                try {
                    chiller.handson$setItem(index, stack);
                } finally {
                    arranging = false;
                }
            }
        };
    }

    /**
     * The chiller uses one of each place a set, so three berries laid in as one stack make no ice cream: what lies in it is laid out
     * over its places the way the recipe it makes wants it ({@link StationRecipes#choose}), a recipe of the container on the rack
     * before any other.
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
        List<KitchenChiller.Setting> recipes = KitchenChiller.recipes(level);
        ItemStack rack = chiller.handson$item(KitchenChiller.CONTAINER_SLOT);
        ItemStack[] places = places();
        ResourceLocation found = recipes.stream().filter(recipe -> recipe.made(places)).map(KitchenChiller.Setting::id).findFirst().orElse(null);
        StationRecipes.Choice<KitchenChiller.Setting> choice = null;
        if (!rack.isEmpty()) {
            List<KitchenChiller.Setting> ofRack = recipes.stream().filter(recipe -> recipe.isContainer(rack)).toList();
            choice = choose(ofRack, kinds, found);
        }
        if (choice == null) {
            choice = choose(recipes, kinds, found);
        }
        if (choice != null) {
            StationRecipes.lay(slots, kinds, choice.parts());
        }
    }

    private StationRecipes.Choice<KitchenChiller.Setting> choose(List<KitchenChiller.Setting> recipes, List<ItemStack> kinds,
            ResourceLocation found) {
        return StationRecipes.choose(StationRecipes.candidates(recipes, KitchenChiller.Setting::id, KitchenChiller.Setting::ingredients),
                kinds, KitchenChiller.PLACES, picked, found, false);
    }

    /**
     * Sets up to {@code sets} sets of the recipe up from the inventory: its container onto the rack (as many as the sets use up; another
     * one standing there goes back to the cook), its liquid as far as the tank lacks it (another liquid is first drawn back into the
     * cook's empty bottles or bucket), its things each in its own place (things of another recipe go back to the cook), and, when the
     * chest is empty and nothing melts, ice enough for it. Never more sets than the tank's liquid makes and the output has room for.
     */
    private void fill(Level level, Player player, KitchenChiller.Setting recipe, int sets) {
        Inventory inventory = player.getInventory();
        int most = recipe.wantsLiquid() ? Math.min(sets, KitchenChiller.TANK / recipe.amount()) : sets;
        ItemStack done = chiller.handson$item(KitchenChiller.OUTPUT_SLOT);
        int room = done.isEmpty() ? recipe.result().getMaxStackSize()
                : ItemStack.isSameItemSameComponents(done, recipe.result()) ? done.getMaxStackSize() - done.getCount() : 0;
        most = Math.max(1, Math.min(most, room / Math.max(1, recipe.result().getCount())));
        if (!recipe.container().isEmpty()) {
            most = Math.max(1, Math.min(most, recipe.container().getMaxStackSize() / Math.max(1, recipe.usedUp())));
        }

        SlotStorage slots = placesAsSlots();
        List<ItemStack> kinds = StationRecipes.kinds(slots);
        if (!kinds.isEmpty() && (recipe.ingredients().isEmpty() || StationRecipes.parts(recipe.ingredients(), kinds) == null)) {
            for (int slot = 0; slot < KitchenChiller.PLACES; slot++) {
                give(player, chiller.handson$item(slot));
                slots.set(slot, ItemStack.EMPTY);
            }
            kinds = List.of();
        }
        standContainer(player, recipe, most);
        pourFor(player, recipe, recipe.amount() * most);
        if (!recipe.ingredients().isEmpty()) {
            int[] parts = kinds.isEmpty() ? null : StationRecipes.parts(recipe.ingredients(), kinds);
            int already = parts == null ? 0 : StationRecipes.servings(kinds, parts);
            if (most > already) {
                StationRecipes.Filled filled = StationRecipes.fill(inventory, recipe.ingredients(), kinds, KitchenChiller.PLACES, most - already);
                if (filled != null) {
                    picked = recipe.id();
                    StationRecipes.lay(slots, filled.kinds(), filled.parts());
                }
            }
        }
        iceFor(player, recipe.ticks() * most);
        inventory.setChanged();
    }

    /** The recipe's container goes onto the rack from the inventory, as many as {@code sets} sets need; another goes back to the cook. */
    private void standContainer(Player player, KitchenChiller.Setting recipe, int sets) {
        ItemStack rack = chiller.handson$item(KitchenChiller.CONTAINER_SLOT);
        if (!rack.isEmpty() && !recipe.isContainer(rack)) {
            chiller.handson$setItem(KitchenChiller.CONTAINER_SLOT, ItemStack.EMPTY);
            give(player, rack);
            rack = ItemStack.EMPTY;
        }
        int lacking = recipe.containersFor(sets) - rack.getCount();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && lacking > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!recipe.isContainer(stack)) {
                continue;
            }
            ItemStack left = moveInto(KitchenChiller.CONTAINER_SLOT, stack, lacking);
            lacking -= stack.getCount() - left.getCount();
            inventory.setItem(slot, left.isEmpty() ? ItemStack.EMPTY : left);
        }
    }

    /**
     * Pours the recipe's liquid in from the inventory until the tank holds {@code wanted} mB of it, buckets while a bucket fits. A
     * liquid the recipe does not set from is drawn back out first, into the empty bottles and buckets the cook carries.
     */
    private void pourFor(Player player, KitchenChiller.Setting recipe, int wanted) {
        KitchenChiller.Access access = KitchenChiller.access();
        Inventory inventory = player.getInventory();
        if (chiller.handson$amount() > 0 && chiller.handson$fluid() != recipe.fluid()) {
            boolean drawn = true;
            while (chiller.handson$amount() > 0 && drawn) {
                drawn = false;
                for (int slot = 0; slot < Inventory.INVENTORY_SIZE && !drawn; slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack.isEmpty() || access.liquidOf(stack).isPresent() || !(stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE))) {
                        continue;
                    }
                    ItemStack full = chiller.handson$draw(stack);
                    if (full != null) {
                        stack.shrink(1);
                        give(player, full);
                        drawn = true;
                    }
                }
            }
        }
        if (!recipe.wantsLiquid() || chiller.handson$amount() > 0 && chiller.handson$fluid() != recipe.fluid()) {
            return;
        }
        wanted = Math.min(KitchenChiller.TANK, wanted);
        boolean poured = true;
        while (chiller.handson$amount() < wanted && poured) {
            poured = false;
            int lacking = wanted - chiller.handson$amount();
            int best = -1;
            int bestAmount = 0;
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                Optional<KitchenEdVat.Liquid> liquid = access.liquidOf(inventory.getItem(slot));
                if (liquid.isEmpty() || liquid.get().fluid() != recipe.fluid()) {
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
                ItemStack left = chiller.handson$pour(stack);
                if (left != null) {
                    stack.shrink(1);
                    give(player, left);
                    poured = true;
                }
            }
        }
    }

    /**
     * Ice for {@code ticks} of cold goes into the chest from the inventory, when the chest is empty and nothing melts: of the kinds
     * the cook carries enough of, the one that needs the fewest pieces; with enough of none, the kind that lasts longest in all.
     */
    private void iceFor(Player player, int ticks) {
        KitchenChiller.Access access = KitchenChiller.access();
        if (!chiller.handson$item(KitchenChiller.ICE_SLOT).isEmpty() || chiller.handson$chill() > 0) {
            return;
        }
        Inventory inventory = player.getInventory();
        int best = -1;
        int bestPieces = 0;
        boolean bestEnough = false;
        int bestCold = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            int chill = access.chillOf(stack);
            if (chill <= 0) {
                continue;
            }
            int pieces = Mth.positiveCeilDiv(ticks, chill);
            boolean enough = stack.getCount() >= pieces;
            int cold = chill * Math.min(stack.getCount(), pieces);
            boolean better = best < 0 || enough && !bestEnough || enough == bestEnough && (enough ? pieces < bestPieces : cold > bestCold);
            if (better) {
                best = slot;
                bestPieces = pieces;
                bestEnough = enough;
                bestCold = cold;
            }
        }
        if (best >= 0) {
            ItemStack stack = inventory.getItem(best);
            ItemStack left = moveInto(KitchenChiller.ICE_SLOT, stack, bestPieces);
            inventory.setItem(best, left.isEmpty() ? ItemStack.EMPTY : left);
        }
    }

    /** How full the container on the rack looks: the tank's liquid against what one setting of the recipe set up takes. */
    static int liquidShown(int amount, Optional<KitchenChiller.Setting> wanted) {
        if (amount <= 0) {
            return 0;
        }
        int one = wanted.filter(KitchenChiller.Setting::wantsLiquid).map(KitchenChiller.Setting::amount).orElse(KitchenChiller.BOTTLE);
        return Mth.clamp(Math.round(amount * 100.0F / one), 10, 100);
    }

    private void refresh(StationContext station) {
        if (chiller == null) {
            return;
        }
        StationState state = station.state();
        int time = chiller.handson$time();
        int total = chiller.handson$timeTotal();
        state.setControl(ChillerStation.PROGRESS, total > 0 ? Mth.clamp((float) time / total, 0.0F, 1.0F) : 0.0F);
        Fluid fluid = chiller.handson$fluid();
        int amount = chiller.handson$amount();
        ItemStack[] places = places();
        ItemStack rack = chiller.handson$item(KitchenChiller.CONTAINER_SLOT);
        List<KitchenChiller.Setting> recipes = KitchenChiller.recipes(station.level());
        Optional<KitchenChiller.Setting> wanted = KitchenChiller.wanted(recipes, places, rack, fluid, amount);
        boolean sets = KitchenChiller.making(recipes, places, rack, fluid, amount).isPresent();
        int shown = liquidShown(amount, wanted);
        TankContents liquid = shown > 0 ? TankContents.of(SYRUP_LIQUID, shown) : TankContents.EMPTY;
        if (!state.getTank(ChillerStation.LIQUID).equals(liquid)) {
            state.setTank(ChillerStation.LIQUID, liquid);
        }
        int pan = chiller.handson$pan();
        int water = pan <= 0 ? 0 : Mth.clamp(Math.round(pan * 100.0F / KitchenChiller.PAN), 1, 100);
        TankContents melt = water > 0 ? TankContents.of(MELT_WATER_LIQUID, water) : TankContents.EMPTY;
        if (!state.getTank(ChillerStation.MELT).equals(melt)) {
            state.setTank(ChillerStation.MELT, melt);
        }
        CompoundTag own = state.data().copy();
        CompoundTag next = own.copy();
        next.putString(ChillerStation.FLUID, fluid == Fluids.EMPTY || amount <= 0 ? "" : BuiltInRegistries.FLUID.getKey(fluid).toString());
        next.putInt(ChillerStation.AMOUNT, amount);
        int chill = chiller.handson$chill();
        int chillTotal = Math.max(chill, chiller.handson$chillTotal());
        next.putInt(ChillerStation.CHILL, chill <= 0 ? 0 : Mth.clamp(Mth.positiveCeilDiv(chill * CHILL_STEPS, chillTotal), 1, CHILL_STEPS));
        next.putInt(ChillerStation.CHILL_TOTAL, chill <= 0 ? 0 : chillTotal);
        // In tenths of a bottle, and whole seconds, as the scene and the badge tell them.
        next.putInt(ChillerStation.PAN, pan >= KitchenChiller.PAN ? KitchenChiller.PAN : pan / 25 * 25);
        next.putInt(ChillerStation.LEFT, sets && total > 0 ? Mth.ceil(Math.max(0, total - time) / 20.0F) * 20 : 0);
        next.putString(ChillerStation.MAKING, wanted.map(recipe -> recipe.id().toString()).orElse(""));
        next.putBoolean(ChillerStation.SETS, sets);
        if (!next.equals(own)) {
            state.setData(next);
        }
    }
}
