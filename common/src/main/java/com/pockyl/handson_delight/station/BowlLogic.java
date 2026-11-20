package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The mixing bowl's menu by hand. Its places are the bowl's own; liquids go into its own tank; the stirs are counted by the bowl as
 * well. What the station adds: a stir is a whole circle of the spoon round the bowl, a bowlful of several sets is mixed at once
 * (more circles, far fewer than a set at a time), and the container the result goes into is taken from the cook's inventory when
 * the last stir is done, never put into the bowl first.
 */
public final class BowlLogic implements StationLogic {
    /** The most sets of ingredients one pick in the book puts in. */
    private static final int FILL_MOST = 16;
    /** A bowlful of more sets takes more circles, by the square root of the sets, counted up to this many sets. */
    private static final int BATCH_MOST = 16;
    /** The spoon's end goes round only this near the wall to count: through the middle it is no circle. */
    private static final float ROUND = 0.3F;
    /** Between two reports of the spoon it goes at most this far round, radians: farther is a jump, not a stir. */
    private static final float MOST_TURN = 2.4F;
    /** A circle takes at least this many ticks: faster is no stirring a hand does. */
    private static final int FASTEST_CIRCLE = 4;
    /** The spoon held still this long, ticks, and the stirring stops for everybody. */
    private static final int STILL = 20;
    /** How full the bowl looks: hundredths of its inside for a bucket of liquid, the most, and what the food adds as it is mixed in. */
    private static final float LEVEL_PER_BUCKET = 45.0F;
    private static final int LEVEL_MOST = 80;
    private static final int LEVEL_PER_PLACE = 5;
    public static final TagKey<Item> EGGS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "eggs"));
    /** Extra Delight's egg white: what an egg cracked for its white gives. */
    public static final Identifier EGG_WHITE = Identifier.fromNamespaceAndPath(KitchenBowl.MOD_ID, "egg_white_fluid");

    private final KitchenBowl bowl;
    private boolean arranging;
    /** The recipe the cook last picked in the book: the bowl's choice while its food makes it, until it is empty. */
    private Identifier picked;
    /** Where the spoon's end was round the middle last, radians (NaN: not round the wall), and how far it has gone round since a stir. */
    private float lastAngle = Float.NaN;
    private float turned;
    private long lastStir;
    private long lastMove = -1000;
    private int pouredCount;

    public BowlLogic(StationContext station) {
        bowl = KitchenBowl.of(station.blockEntity());
        station.state().bindSlots(BowlStation.INGREDIENTS, places(false));
    }

    /** The bowl's nine places as slots; {@code laying}: a change is the station laying them out, no cook's. */
    private SlotStorage places(boolean laying) {
        return new SlotStorage() {
            @Override
            public int size() {
                return KitchenBowl.PLACES;
            }

            @Override
            public ItemStack get(int index) {
                return bowl == null ? ItemStack.EMPTY : bowl.handson$item(index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                if (bowl == null) {
                    return;
                }
                if (!laying) {
                    bowl.handson$setItem(index, stack);
                    return;
                }
                arranging = true;
                try {
                    bowl.handson$setItem(index, stack);
                } finally {
                    arranging = false;
                }
            }
        };
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        if (bowl != null && !station.level().isClientSide()) {
            giveBackContainers(station.level(), player);
        }
        refresh(station);
    }

    @Override
    public void tick(StationContext station) {
        if (bowl == null) {
            return;
        }
        long now = station.level().getGameTime();
        if (now - lastMove > STILL && station.state().getControl(BowlStation.STIRRING) > 0.5F) {
            station.state().setControl(BowlStation.STIRRING, 0.0F);
            lastAngle = Float.NaN;
        }
        refresh(station);
        // A bowlful stirred through waits for its containers: it is given as soon as a cook here has them.
        CompoundTag data = station.state().data();
        if (data.getIntOr(BowlStation.NEED, 0) > 0 && data.getIntOr(BowlStation.STIRRED, 0) >= data.getIntOr(BowlStation.NEED, 0) && now % 10 == 0) {
            for (Player player : List.copyOf(station.users())) {
                Optional<KitchenBowl.Mixing> recipe = making(station.level());
                if (recipe.isPresent() && finish(station, player, recipe.get(), utensilFor(player, recipe.get()))) {
                    break;
                }
            }
        }
    }

    /** What lies in the bowl may only go in: food a recipe takes. A liquid is poured, a spoon stirs, a container waits outside. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return bowl != null && kindOf(station.level(), stack) == Kind.FOOD;
    }

    /** What a stack brought to the bowl is to it. */
    public enum Kind {
        FOOD,
        LIQUID,
        UTENSIL,
        CONTAINER,
        NONE
    }

    /**
     * What {@code stack} is to the bowl: food when some recipe takes it as it is (an egg, a honey bottle); a liquid when the bowl
     * pours it; a utensil when some recipe is stirred with it; a container (a bowl, a bottle) when some recipe's result goes into it
     * or a liquid of the bowl goes into it.
     */
    Kind kindOf(Level level, ItemStack stack) {
        if (stack.isEmpty() || bowl == null) {
            return Kind.NONE;
        }
        return kind(KitchenBowl.recipes(level), bowl, stack);
    }

    public static Kind kind(List<KitchenBowl.Mixing> recipes, KitchenBowl bowl, ItemStack stack) {
        boolean utensil = false;
        boolean container = false;
        for (KitchenBowl.Mixing recipe : recipes) {
            if (recipe.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack))) {
                return Kind.FOOD;
            }
            utensil |= recipe.utensil().test(stack);
            container |= recipe.hasContainer() && ItemStack.isSameItem(recipe.container(), stack);
        }
        if (bowl.handson$liquidOf(stack) != null) {
            return Kind.LIQUID;
        }
        if (utensil) {
            return Kind.UTENSIL;
        }
        return container || bowl.handson$draw(stack, true) != null ? Kind.CONTAINER : Kind.NONE;
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        if (bowl != null && slot.group().equals(BowlStation.INGREDIENTS) && !arranging) {
            turned = 0.0F;
            arrange(station.level());
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (bowl == null) {
            return;
        }
        switch (action) {
            case BowlStation.ACTION_PUT -> put(station, player, data.contains(BowlStation.FROM) ? data.getIntOr(BowlStation.FROM, 0) : -1,
                    data.getBooleanOr(BowlStation.ONE, false), data.getBooleanOr(BowlStation.SEPARATE, false));
            case BowlStation.ACTION_DRAW -> draw(station, player, data.contains(BowlStation.FROM) ? data.getIntOr(BowlStation.FROM, 0) : -1,
                    data.getBooleanOr(BowlStation.ALL, false));
            case BowlStation.ACTION_FILL -> {
                Identifier id = Identifier.tryParse(data.getStringOr(BowlStation.DISH, ""));
                KitchenBowl.recipes(station.level()).stream().filter(recipe -> recipe.id().equals(id)).findFirst()
                        .ifPresent(recipe -> fill(station, player, recipe, data.getBooleanOr(BowlStation.ALL, false) ? FILL_MOST : 1));
            }
            case BowlStation.ACTION_STIR -> stir(station, player, data.getFloatOr(BowlStation.X, 0.0F), data.getFloatOr(BowlStation.Z, 0.0F),
                    data.contains(BowlStation.FROM) ? data.getIntOr(BowlStation.FROM, 0) : -1);
            default -> {
            }
        }
        refresh(station);
    }

    /** The stack the cook brings from inventory slot {@code from}, or from the cursor at -1; empty for a slot that is none. */
    private static ItemStack brought(Player player, int from) {
        if (from < 0) {
            return player.inventoryMenu.getCarried();
        }
        return from < player.getInventory().getContainerSize() ? player.getInventory().getItem(from) : ItemStack.EMPTY;
    }

    private static void taken(Player player, int from, ItemStack left) {
        if (from < 0) {
            player.inventoryMenu.setCarried(left.isEmpty() ? ItemStack.EMPTY : left);
        }
        player.getInventory().setChanged();
    }

    /**
     * The stack at {@code from} goes into the bowl. A liquid is poured, one container after the other, the empty ones back to the
     * cook (the last one on the cursor when it came from there); eggs with {@code separate} are cracked for their white; food goes
     * onto the same food or into a free place, the bowl's places laid out for the recipe it makes.
     */
    private void put(StationContext station, Player player, int from, boolean one, boolean separate) {
        ItemStack stack = brought(player, from);
        if (stack.isEmpty()) {
            return;
        }
        Level level = station.level();
        int count = one ? 1 : stack.getCount();
        List<String> poured = new ArrayList<>();
        if (separate && stack.is(EGGS)) {
            String egg = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            for (int i = 0; i < count && bowl.handson$separate(player); i++) {
                stack.shrink(1);
                poured.add(egg);
            }
            taken(player, from, stack);
            told(station, poured);
            return;
        }
        switch (kindOf(level, stack)) {
            case LIQUID -> {
                ItemStack lastEmpty = ItemStack.EMPTY;
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                for (int i = 0; i < count; i++) {
                    ItemStack empty = bowl.handson$pour(stack, false);
                    if (empty == null) {
                        break;
                    }
                    stack.shrink(1);
                    poured.add(id);
                    if (!lastEmpty.isEmpty()) {
                        give(player, lastEmpty);
                    }
                    lastEmpty = empty;
                }
                if (from < 0 && stack.isEmpty()) {
                    // The container poured last stays in the hand that poured it.
                    taken(player, from, lastEmpty);
                } else {
                    give(player, lastEmpty);
                    taken(player, from, stack);
                }
                told(station, poured);
            }
            case FOOD -> {
                SlotStorage slots = places(false);
                int place = -1;
                for (int slot = 0; slot < KitchenBowl.PLACES && place < 0; slot++) {
                    ItemStack there = slots.get(slot);
                    if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
                        place = slot;
                    }
                }
                for (int slot = 0; slot < KitchenBowl.PLACES && place < 0; slot++) {
                    if (slots.get(slot).isEmpty()) {
                        place = slot;
                    }
                }
                if (place < 0) {
                    return;
                }
                ItemStack there = slots.get(place);
                int moved = Math.min(count, stack.getMaxStackSize() - there.getCount());
                ItemStack laid = there.isEmpty() ? stack.copyWithCount(moved) : there.copyWithCount(there.getCount() + moved);
                stack.shrink(moved);
                taken(player, from, stack);
                arranging = true;
                try {
                    slots.set(place, laid);
                } finally {
                    arranging = false;
                }
                turned = 0.0F;
                arrange(level);
            }
            default -> {
            }
        }
    }

    /** Tells every client what was poured or cracked in, for it to show the pouring. */
    private void told(StationContext station, List<String> poured) {
        if (poured.isEmpty()) {
            return;
        }
        CompoundTag data = station.state().data();
        ListTag list = new ListTag();
        poured.forEach(id -> list.add(StringTag.valueOf(id)));
        pouredCount = Math.max(pouredCount, data.getIntOr(BowlStation.POURED_COUNT, 0)) + 1;
        data.putInt(BowlStation.POURED_COUNT, pouredCount);
        data.put(BowlStation.POURED, list);
        station.state().setData(data);
    }

    /**
     * A liquid is drawn back out of the bowl into the empty container at {@code from} (the cursor at -1): the one poured last that it
     * takes; one, or with {@code all} as many as there are containers and liquid. A full container on the cursor stays there when the
     * whole stack was drawn into, the rest go to the inventory.
     */
    private void draw(StationContext station, Player player, int from, boolean all) {
        ItemStack containers = brought(player, from);
        int drawn = 0;
        int most = all ? containers.getCount() : 1;
        List<ItemStack> filled = new ArrayList<>();
        while (drawn < most && !containers.isEmpty()) {
            ItemStack full = bowl.handson$draw(containers, false);
            if (full == null) {
                break;
            }
            containers.shrink(1);
            filled.add(full);
            drawn++;
        }
        if (filled.isEmpty()) {
            return;
        }
        if (from < 0 && containers.isEmpty() && filled.size() == 1) {
            taken(player, from, filled.get(0));
            return;
        }
        filled.forEach(full -> give(player, full));
        taken(player, from, containers);
    }

    private static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    /** What lies in the bowl as its places. */
    private ItemStack[] placesNow() {
        ItemStack[] places = new ItemStack[KitchenBowl.PLACES];
        for (int slot = 0; slot < places.length; slot++) {
            places[slot] = bowl.handson$item(slot);
        }
        return places;
    }

    /** The recipe what lies in the bowl makes now, its container left out. */
    public Optional<KitchenBowl.Mixing> making(Level level) {
        return bowl == null ? Optional.empty() : KitchenBowl.making(KitchenBowl.recipes(level), placesNow(), bowl.handson$liquids(),
                bowl.handson$item(KitchenBowl.CONTAINER_SLOT));
    }

    /** The circles a bowlful of {@code sets} takes: the recipe's stirs, times the square root of the sets. */
    public static int stirsFor(KitchenBowl.Mixing recipe, int sets) {
        return Mth.ceil(recipe.stirs() * Math.sqrt(Mth.clamp(sets, 1, BATCH_MOST)) - 1.0e-4);
    }

    /**
     * The spoon is at {@code (x, z)}: what it went round the middle since its last report counts, near the wall and one way; every
     * whole circle is a stir of the bowl. The bowlful stirred through is given.
     */
    private void stir(StationContext station, Player player, float x, float z, int from) {
        Optional<KitchenBowl.Mixing> recipe = making(station.level());
        ItemStack utensil = brought(player, from);
        if (recipe.isEmpty() || utensil.isEmpty() || !recipe.get().utensil().test(utensil)) {
            lastAngle = Float.NaN;
            return;
        }
        StationState state = station.state();
        float px = Mth.clamp(x, -1.0F, 1.0F);
        float pz = Mth.clamp(z, -1.0F, 1.0F);
        state.setControl(BowlStation.SPOON_X, px);
        state.setControl(BowlStation.SPOON_Z, pz);
        state.setControl(BowlStation.STIRRING, 1.0F);
        long now = station.level().getGameTime();
        lastMove = now;
        if (Math.hypot(px, pz) < ROUND) {
            lastAngle = Float.NaN;
            return;
        }
        float angle = (float) Mth.atan2(pz, px);
        if (!Float.isNaN(lastAngle)) {
            float step = Mth.wrapDegrees((angle - lastAngle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            if (Math.abs(step) <= MOST_TURN) {
                // Round the other way, a circle starts over.
                turned = turned != 0.0F && Math.signum(step) != Math.signum(turned) ? step : turned + step;
            }
        }
        lastAngle = angle;
        if (Math.abs(turned) < Mth.TWO_PI || now - lastStir < FASTEST_CIRCLE) {
            return;
        }
        turned -= Math.signum(turned) * Mth.TWO_PI;
        lastStir = now;
        int sets = KitchenBowl.sets(recipe.get(), placesNow(), bowl.handson$liquids());
        int need = stirsFor(recipe.get(), sets);
        int stirred = Math.min(need, bowl.handson$stirs() + 1);
        bowl.handson$setStirs(stirred);
        if (stirred >= need) {
            finish(station, player, recipe.get(), utensil);
        }
    }

    /** The utensil {@code player} has for {@code recipe}: on the cursor, in the hand or anywhere in the inventory; empty for none. */
    private static ItemStack utensilFor(Player player, KitchenBowl.Mixing recipe) {
        if (recipe.utensil().test(player.inventoryMenu.getCarried())) {
            return player.inventoryMenu.getCarried();
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (recipe.utensil().test(inventory.getItem(slot))) {
                return inventory.getItem(slot);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * The bowlful is stirred through: the bowl makes it a set at a time, by its own code, each set in its containers taken from the
     * cook's inventory. Without containers it waits, stirred, and the badge asks for them. True when something was made.
     */
    private boolean finish(StationContext station, Player player, KitchenBowl.Mixing recipe, ItemStack utensil) {
        if (utensil.isEmpty() || station.level().isClientSide()) {
            return false;
        }
        int sets = KitchenBowl.sets(recipe, placesNow(), bowl.handson$liquids());
        int made = 0;
        for (int set = 0; set < sets; set++) {
            if (recipe.hasContainer() && !bringContainers(player, recipe)) {
                break;
            }
            if (!bowl.handson$mix(player, utensil)) {
                break;
            }
            made++;
        }
        CompoundTag data = station.state().data();
        if (made == 0) {
            data.putString(BowlStation.WANTS, recipe.hasContainer() ? BuiltInRegistries.ITEM.getKey(recipe.container().getItem()).toString() : "");
            // Taking containers out of their place starts the bowl's stirs over: they stay done for the bowlful.
            bowl.handson$setStirs(stirsFor(recipe, sets));
            station.state().setData(data);
            return false;
        }
        data.remove(BowlStation.WANTS);
        data.putInt(BowlStation.MADE_COUNT, data.getIntOr(BowlStation.MADE_COUNT, 0) + 1);
        data.putString(BowlStation.MADE, BuiltInRegistries.ITEM.getKey(recipe.result().getItem()).toString());
        data.putInt(BowlStation.MADE_HOW_MANY, made * recipe.result().getCount());
        station.state().setData(data);
        turned = 0.0F;
        player.getInventory().setChanged();
        return true;
    }

    /**
     * Fills the bowl's container place with what a set of {@code recipe} uses, from the cook's inventory; what lay there that is
     * something else goes back to the cook. False when they have not enough.
     */
    private boolean bringContainers(Player player, KitchenBowl.Mixing recipe) {
        ItemStack there = bowl.handson$item(KitchenBowl.CONTAINER_SLOT);
        if (!there.isEmpty() && !ItemStack.isSameItem(there, recipe.container())) {
            bowl.handson$setItem(KitchenBowl.CONTAINER_SLOT, ItemStack.EMPTY);
            give(player, there);
            there = ItemStack.EMPTY;
        }
        int want = recipe.containersPer() - there.getCount();
        if (want <= 0) {
            return true;
        }
        Inventory inventory = player.getInventory();
        int have = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItem(stack, recipe.container()) && stack.getComponentsPatch().isEmpty()) {
                have += stack.getCount();
            }
        }
        if (have < want) {
            return false;
        }
        int got = there.getCount();
        for (int slot = 0; slot < inventory.getContainerSize() && want > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItem(stack, recipe.container()) && stack.getComponentsPatch().isEmpty()) {
                int take = Math.min(want, stack.getCount());
                stack.shrink(take);
                want -= take;
                got += take;
            }
        }
        bowl.handson$setItem(KitchenBowl.CONTAINER_SLOT, recipe.container().copyWithCount(got));
        return true;
    }

    /**
     * Containers left in the bowl's container place (its menu, a hopper) that the recipe of what lies in it does not want: the bowl
     * makes nothing with them there. They go back to the cook; the station brings the right ones when the stirring is done.
     */
    private void giveBackContainers(Level level, Player player) {
        ItemStack there = bowl.handson$item(KitchenBowl.CONTAINER_SLOT);
        if (there.isEmpty()) {
            return;
        }
        Optional<KitchenBowl.Mixing> recipe = making(level);
        if (recipe.isPresent() && (!recipe.get().hasContainer() || !ItemStack.isSameItem(recipe.get().container(), there))) {
            bowl.handson$setItem(KitchenBowl.CONTAINER_SLOT, ItemStack.EMPTY);
            give(player, there);
        }
    }

    /**
     * The bowl uses one of each place a set, so two eggs dropped in as one stack make no egg mix: what lies in it is laid out over its
     * places the way the recipe it makes wants it ({@link StationRecipes#choose}).
     */
    private void arrange(Level level) {
        SlotStorage slots = places(true);
        List<ItemStack> kinds = StationRecipes.kinds(slots);
        if (kinds.isEmpty()) {
            picked = null;
            return;
        }
        List<KitchenBowl.Mixing> recipes = KitchenBowl.recipes(level);
        List<KitchenBowl.Liquid> liquids = bowl.handson$liquids();
        // Recipes whose liquids are all in the bowl first: the same food may make two things, by what was poured.
        List<KitchenBowl.Mixing> wet = recipes.stream().filter(recipe -> KitchenBowl.liquidsFit(recipe, liquids)).toList();
        Identifier found = making(level).map(KitchenBowl.Mixing::id).orElse(null);
        StationRecipes.Choice<KitchenBowl.Mixing> choice = StationRecipes.choose(StationRecipes.candidates(wet, KitchenBowl.Mixing::id,
                KitchenBowl.Mixing::ingredients), kinds, KitchenBowl.PLACES, picked, found, false);
        if (choice == null) {
            choice = StationRecipes.choose(StationRecipes.candidates(recipes, KitchenBowl.Mixing::id, KitchenBowl.Mixing::ingredients), kinds,
                    KitchenBowl.PLACES, picked, found, false);
        }
        if (choice != null) {
            StationRecipes.lay(slots, kinds, choice.parts());
        }
    }

    /**
     * A recipe picked in the book: up to {@code sets} sets of its food from the inventory, laid out in its places, and its liquids
     * poured from their containers in the inventory as far as the bowl has less than the sets want; egg white comes from eggs cracked
     * for it. Food the recipe does not use in the bowl takes nothing.
     */
    private void fill(StationContext station, Player player, KitchenBowl.Mixing recipe, int sets) {
        picked = recipe.id();
        int want = sets;
        if (!recipe.ingredients().isEmpty()) {
            SlotStorage slots = places(true);
            List<ItemStack> kinds = StationRecipes.kinds(slots);
            int[] parts = kinds.isEmpty() ? null : StationRecipes.parts(recipe.ingredients(), kinds);
            int already = parts == null ? 0 : StationRecipes.servings(kinds, parts);
            StationRecipes.Filled filled = StationRecipes.fill(player.getInventory(), recipe.ingredients(), kinds, KitchenBowl.PLACES,
                    Math.max(0, sets - already));
            if (filled != null) {
                StationRecipes.lay(slots, filled.kinds(), filled.parts());
                want = already + filled.sets();
            } else if (already > 0) {
                want = already;
            } else {
                return;
            }
            turned = 0.0F;
        }
        List<String> poured = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (KitchenBowl.LiquidNeed need : recipe.liquids()) {
            int target = need.amount() * want;
            while (inBowl(need) < target) {
                int slot = source(inventory, need);
                if (slot >= 0) {
                    ItemStack full = inventory.getItem(slot);
                    String id = BuiltInRegistries.ITEM.getKey(full.getItem()).toString();
                    ItemStack empty = bowl.handson$pour(full, false);
                    if (empty == null) {
                        break;
                    }
                    full.shrink(1);
                    give(player, empty);
                    poured.add(id);
                } else if (need.takes(EGG_WHITE) && (slot = eggs(inventory)) >= 0 && bowl.handson$separate(player)) {
                    ItemStack egg = inventory.getItem(slot);
                    poured.add(BuiltInRegistries.ITEM.getKey(egg.getItem()).toString());
                    egg.shrink(1);
                } else {
                    break;
                }
            }
        }
        inventory.setChanged();
        told(station, poured);
    }

    private int inBowl(KitchenBowl.LiquidNeed need) {
        int amount = 0;
        for (KitchenBowl.Liquid liquid : bowl.handson$liquids()) {
            if (need.takes(liquid.fluid())) {
                amount = Math.max(amount, liquid.amount());
            }
        }
        return amount;
    }

    /** The inventory slot of a container of a liquid {@code need} takes, which the bowl has room for; -1 for none. */
    private int source(Inventory inventory, KitchenBowl.LiquidNeed need) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            KitchenBowl.Liquid liquid = stack.isEmpty() ? null : bowl.handson$liquidOf(stack);
            if (liquid != null && need.takes(liquid.fluid()) && bowl.handson$pour(stack, true) != null) {
                return slot;
            }
        }
        return -1;
    }

    private static int eggs(Inventory inventory) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot).is(EGGS)) {
                return slot;
            }
        }
        return -1;
    }

    /** How full the bowl looks: its liquids, and the food in it as it is mixed in. */
    static int level(List<KitchenBowl.Liquid> liquids, ItemStack[] places, float progress) {
        int amount = 0;
        for (KitchenBowl.Liquid liquid : liquids) {
            amount += liquid.amount();
        }
        int food = 0;
        for (ItemStack stack : places) {
            food += stack.isEmpty() ? 0 : LEVEL_PER_PLACE;
        }
        float level = amount / (float) KitchenBowl.BUCKET * LEVEL_PER_BUCKET + food * progress;
        return Math.min(LEVEL_MOST, Math.round(level));
    }

    /** The station's state from the bowl: how far it is stirred, for how many sets, and how full it looks. */
    private void refresh(StationContext station) {
        if (bowl == null) {
            return;
        }
        StationState state = station.state();
        Optional<KitchenBowl.Mixing> recipe = making(station.level());
        ItemStack[] places = placesNow();
        List<KitchenBowl.Liquid> liquids = bowl.handson$liquids();
        int sets = recipe.map(mixing -> KitchenBowl.sets(mixing, places, liquids)).orElse(0);
        int need = recipe.map(mixing -> stirsFor(mixing, sets)).orElse(0);
        int stirred = recipe.isPresent() ? Math.min(need, bowl.handson$stirs()) : 0;
        float progress = need > 0 ? (float) stirred / need : 0.0F;
        state.setControl(BowlStation.PROGRESS, progress);
        int level = level(liquids, places, progress);
        TankContents tank = level > 0 ? TankContents.of(BowlStation.BATTER_LIQUID, level) : TankContents.EMPTY;
        if (!state.getTank(BowlStation.BATTER).equals(tank)) {
            state.setTank(BowlStation.BATTER, tank);
        }
        CompoundTag data = state.data();
        String making = recipe.map(mixing -> mixing.id().toString()).orElse("");
        boolean changed = data.getIntOr(BowlStation.NEED, 0) != need || data.getIntOr(BowlStation.STIRRED, 0) != stirred
                || data.getIntOr(BowlStation.SETS, 0) != sets || !data.getStringOr(BowlStation.MAKING, "").equals(making);
        if (stirred < need && data.contains(BowlStation.WANTS)) {
            data.remove(BowlStation.WANTS);
            changed = true;
        }
        if (changed) {
            data.putInt(BowlStation.NEED, need);
            data.putInt(BowlStation.STIRRED, stirred);
            data.putInt(BowlStation.SETS, sets);
            data.putString(BowlStation.MAKING, making);
            state.setData(data);
        }
    }
}
