package com.pockyl.handson_delight.station;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.mixin.CookingPotBlockEntityAccessor;
import com.pockyl.handson_delight.platform.Platform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The rules the pot's menu has, the values its screen read and what is new: nothing cooks unless it is stirred. It also runs on the
 * clients around a pot nobody works at, to show what lies in it; there it only reads.
 */
public final class PotLogic implements StationLogic {
    /** How full the pot looks: there is always water, and a little more for every stack in it. */
    private static final int BROTH_BASE = 58;
    private static final int BROTH_PER_STACK = 6;
    /** A finished soup stands high in the pot and sinks serving by serving. */
    private static final int SOUP_BASE = 74;
    private static final int SOUP_PER_SERVING = 5;
    /** How long a stir of the spoon keeps the pot cooking, in ticks: the soup goes on turning for that long. */
    private static final int STIR_TICKS = 40;
    /**
     * How many times as fast as Farmer's Delight a serving cooks; a batch takes the square root of its servings times as long, up to
     * this many: sixteen take four times as long as one, a whole stack no longer.
     */
    private static final float PACE = 1.5F;
    private static final int SLOWEST_BATCH = 16;
    /** Ticks the spoon stays in one cook's hand after it last moved: one spoon, one hand at a time. */
    private static final int SPOON_FREE = 10;
    /** Servings of a dish a player has made, by any means, from which the second and the third star open for it. */
    private static final int SECOND_STAR = 8;
    private static final int THIRD_STAR = 32;

    private final CookingPotBlockEntity pot;
    private boolean unsorted;
    /** Who stirs the pot, or last opened it while nobody stirred: their stars are the ones that can fill. */
    private UUID cook;
    private long stirredAt = Long.MIN_VALUE / 2;
    /** The ingredients the result was last looked up for, and what they make: the lookup runs only when they change. */
    private final ItemStack[] resultFor = new ItemStack[CookingPotBlockEntity.MEAL_DISPLAY_SLOT];
    private ItemStack resultMade = ItemStack.EMPTY;
    private CookingPotRecipe recipeMade;

    public PotLogic(StationContext station) {
        pot = (CookingPotBlockEntity) station.blockEntity();
        StationState state = station.state();
        state.bindSlots(CookingPotStation.INGREDIENTS, storage(pot, 0, CookingPotBlockEntity.MEAL_DISPLAY_SLOT));
        state.bindSlots(CookingPotStation.MEAL, storage(pot, CookingPotBlockEntity.MEAL_DISPLAY_SLOT, 1));
        state.bindSlots(CookingPotStation.SERVED, storage(pot, CookingPotBlockEntity.OUTPUT_SLOT, 1));
    }

    private static SlotStorage storage(CookingPotBlockEntity pot, int start, int count) {
        return new SlotStorage() {
            @Override
            public int size() {
                return count;
            }

            @Override
            public ItemStack get(int index) {
                return Platform.INSTANCE.getItem(pot, start + index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                Platform.INSTANCE.setItem(pot, start + index, stack);
            }
        };
    }

    /**
     * Whether the block entity is Farmer's Delight's own cooking pot, the one this mod opens. Other mods may build their pots on its
     * class; those keep cooking as Farmer's Delight does, with no spoon to stir them.
     */
    public static boolean isOurs(CookingPotBlockEntity pot) {
        return pot.getBlockState().is(FarmersDelightContent.COOKING_POT.get());
    }

    /** Whether a player works at the pot through Farmer's Delight's own menu: a client without this mod cannot stir. */
    public static boolean isInMenu(CookingPotBlockEntity pot) {
        if (pot.getLevel() != null) {
            for (Player player : pot.getLevel().players()) {
                if (player.containerMenu instanceof CookingPotMenu menu && menu.blockEntity == pot) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        ((PotStirring) pot).handson$attend(station.level().getGameTime());
        if (!isStirring(station)) {
            cook = player.getUUID();
        }
        refresh(station);
    }

    private boolean isStirring(StationContext station) {
        return station.level().getGameTime() - stirredAt < SPOON_FREE;
    }

    private Player cook(StationContext station) {
        return cook == null ? null : station.level().getPlayerByUUID(cook);
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        unsorted = true;
    }

    @Override
    public void tick(StationContext station) {
        if (!station.level().isClientSide()) {
            ((PotStirring) pot).handson$attend(station.level().getGameTime());
        }
        if (unsorted && !station.level().isClientSide()) {
            unsorted = false;
            shareOut(station);
        }
        if (!station.level().isClientSide()) {
            giveBackSaved(station);
            giveBackLeftovers(station);
        }
        refresh(station);
    }

    /** The containers emptied into the soup (a milk bucket's bucket) go back to the cook, or to anyone at the pot. */
    private void giveBackLeftovers(StationContext station) {
        List<ItemStack> leftovers = ((PotStirring) pot).handson$takeLeftovers();
        if (leftovers.isEmpty()) {
            return;
        }
        Player to = null;
        for (Player user : station.users()) {
            if (to == null || user.getUUID().equals(cook)) {
                to = user;
            }
        }
        for (ItemStack leftover : leftovers) {
            if (to == null) {
                net.minecraft.world.Containers.dropItemStack(station.level(), station.pos().getX() + 0.5, station.pos().getY() + 1.0,
                        station.pos().getZ() + 0.5, leftover);
            } else if (!to.getInventory().add(leftover)) {
                to.drop(leftover, false);
            }
        }
    }

    /**
     * A full stack of the meal took fewer sets than were in the pot, thanks to the stars: the cook gets the sets saved back, so they do
     * not stay in the pot looking uncooked. With the cook gone they stay and cook next.
     */
    private void giveBackSaved(StationContext station) {
        int saved = ((PotStirring) pot).handson$takeSaved();
        Player cook = saved <= 0 ? null : station.users().stream().filter(user -> user.getUUID().equals(this.cook)).findFirst().orElse(null);
        if (cook == null) {
            return;
        }
        for (int i = 0; i < CookingPotBlockEntity.MEAL_DISPLAY_SLOT; i++) {
            ItemStack stack = Platform.INSTANCE.getItem(pot, i);
            if (!stack.isEmpty()) {
                ItemStack back = stack.copyWithCount(Math.min(saved, stack.getCount()));
                Platform.INSTANCE.setItem(pot, i, stack.copyWithCount(stack.getCount() - back.getCount()));
                if (!cook.getInventory().add(back)) {
                    cook.drop(back, false);
                }
            }
        }
    }

    /**
     * A recipe wants two tomatoes in two slots, and a stack thrown into the pot lies in one: when what lies in the pot makes a recipe that way,
     * the stacks are shared out over as many slots as the recipe asks for.
     */
    private void shareOut(StationContext station) {
        Level level = station.level();
        List<ItemStack> inPot = new ArrayList<>();
        for (int i = 0; i < CookingPotBlockEntity.MEAL_DISPLAY_SLOT; i++) {
            inPot.add(Platform.INSTANCE.getItem(pot, i));
        }
        if (Platform.INSTANCE.findRecipe(level, inPot).isPresent()) {
            return;
        }
        List<ItemStack> kinds = new ArrayList<>();
        for (ItemStack stack : inPot) {
            if (!stack.isEmpty()) {
                kinds.stream().filter(kind -> ItemStack.isSameItemSameTags(kind, stack)).findFirst()
                        .ifPresentOrElse(kind -> kind.grow(stack.getCount()), () -> kinds.add(stack.copy()));
            }
        }
        List<CookingPotRecipe> recipes = Platform.INSTANCE.cookingRecipes(level);
        for (CookingPotRecipe recipe : recipes) {
            int[] parts = parts(recipe, kinds, false);
            if (parts != null) {
                lay(pot, kinds, parts);
                return;
            }
        }
        // More than any recipe's slots hold (cooked rice has one ingredient, so one slot: a second stack of rice lay beside it and
        // nothing cooked). The recipe that keeps the most gets it, the rest goes back to the cook; with nobody there it stays.
        if (station.users().isEmpty()) {
            return;
        }
        int[] best = null;
        int bestKept = 0;
        for (CookingPotRecipe recipe : recipes) {
            int[] parts = parts(recipe, kinds, true);
            int kept = parts == null ? 0 : kept(kinds, parts);
            if (kept > bestKept) {
                best = parts;
                bestKept = kept;
            }
        }
        if (best != null) {
            handBack(station, lay(pot, kinds, best));
        }
    }

    /** How many items the slots {@code parts} gives every kind hold. */
    private static int kept(List<ItemStack> kinds, int[] parts) {
        int kept = 0;
        for (int kind = 0; kind < parts.length; kind++) {
            kept += Math.min(kinds.get(kind).getCount(), parts[kind] * kinds.get(kind).getMaxStackSize());
        }
        return kept;
    }

    /** Gives what the pot cannot hold to the cook, or to anyone at the pot. */
    private void handBack(StationContext station, List<ItemStack> rest) {
        Player to = null;
        for (Player user : station.users()) {
            if (to == null || user.getUUID().equals(cook)) {
                to = user;
            }
        }
        for (ItemStack kind : rest) {
            // One kind may be more than a stack: the inventory takes any count, a dropped item only one stack.
            for (int count = kind.getCount(); count > 0; count -= kind.getMaxStackSize()) {
                ItemStack stack = kind.copyWithCount(Math.min(count, kind.getMaxStackSize()));
                if (!to.getInventory().add(stack)) {
                    to.drop(stack, false);
                }
            }
        }
    }

    /**
     * In how many slots every kind must lie for the recipe; null when what is there does not make it, all of it and nothing else. With
     * {@code overflow}, a kind may hold more than its slots take.
     */
    private static int[] parts(CookingPotRecipe recipe, List<ItemStack> kinds, boolean overflow) {
        int[] parts = new int[kinds.size()];
        for (Ingredient ingredient : recipe.getIngredients()) {
            int fits = -1;
            for (int kind = 0; kind < parts.length; kind++) {
                boolean free = parts[kind] < kinds.get(kind).getCount();
                if (free && ingredient.test(kinds.get(kind)) && (fits < 0 || parts[kind] < parts[fits])) {
                    fits = kind;
                }
            }
            if (fits < 0) {
                return null;
            }
            parts[fits]++;
        }
        for (int kind = 0; kind < parts.length; kind++) {
            // What will not go into the slots it gets stays as it is.
            if (parts[kind] == 0 || !overflow && (kinds.get(kind).getCount() + parts[kind] - 1) / parts[kind] > kinds.get(kind).getMaxStackSize()) {
                return null;
            }
        }
        return parts;
    }

    /** Lays every kind over its slots, at most a full stack a slot; returns what did not fit. */
    private static List<ItemStack> lay(CookingPotBlockEntity pot, List<ItemStack> kinds, int[] parts) {
        List<ItemStack> rest = new ArrayList<>();
        int slot = 0;
        for (int kind = 0; kind < parts.length; kind++) {
            ItemStack stack = kinds.get(kind);
            int left = Math.min(stack.getCount(), parts[kind] * stack.getMaxStackSize());
            if (left < stack.getCount()) {
                rest.add(stack.copyWithCount(stack.getCount() - left));
            }
            for (int part = parts[kind]; part > 0; part--) {
                int share = (left + part - 1) / part;
                Platform.INSTANCE.setItem(pot, slot++, stack.copyWithCount(share));
                left -= share;
            }
        }
        while (slot < CookingPotBlockEntity.MEAL_DISPLAY_SLOT) {
            Platform.INSTANCE.setItem(pot, slot++, ItemStack.EMPTY);
        }
        return rest;
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return !slot.equals(CookingPotStation.MEAL_SLOT);
    }

    /** What a soup is served in does not go into the water, unless some recipe cooks with it. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        boolean serves = false;
        for (CookingPotRecipe recipe : Platform.INSTANCE.cookingRecipes(station.level())) {
            if (recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(stack))) {
                return true;
            }
            serves |= ItemStack.isSameItem(recipe.getOutputContainer(), stack);
        }
        return !serves;
    }

    /** How many times the ingredients in the pot make the recipe: every slot gives one item to a serving. */
    private static int servingsLeft(CookingPotBlockEntity pot, CookingPotRecipe recipe) {
        List<ItemStack> stacks = new ArrayList<>();
        int left = Integer.MAX_VALUE;
        for (int i = 0; i < CookingPotBlockEntity.MEAL_DISPLAY_SLOT; i++) {
            ItemStack stack = Platform.INSTANCE.getItem(pot, i);
            if (!stack.isEmpty()) {
                stacks.add(stack);
                left = Math.min(left, stack.getCount());
            }
        }
        if (stacks.isEmpty()) {
            return 0;
        }
        // Two of an ingredient may lie in one slot when a mod matches stacks (Autochef's Delight): never more servings than the
        // ingredients make, whichever way they lie.
        while (left > 0 && !makes(recipe.getIngredients(), stacks, left)) {
            left--;
        }
        return left;
    }

    /** Whether {@code stacks} hold every ingredient {@code times} times over, each item going to one ingredient. */
    static boolean makes(List<Ingredient> ingredients, List<ItemStack> stacks, int times) {
        int[] room = new int[stacks.size()];
        for (int i = 0; i < room.length; i++) {
            room[i] = stacks.get(i).getCount();
        }
        int[][] taken = new int[ingredients.size()][stacks.size()];
        for (int ingredient = 0; ingredient < ingredients.size(); ingredient++) {
            if (ingredients.get(ingredient).isEmpty()) {
                continue;
            }
            for (int n = 0; n < times; n++) {
                if (!take(ingredient, ingredients, stacks, room, taken, new boolean[ingredients.size()])) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * One more item for {@code ingredient}: from a stack with room, or from a used-up stack whose item another ingredient gives up for one
     * from elsewhere.
     */
    private static boolean take(int ingredient, List<Ingredient> ingredients, List<ItemStack> stacks, int[] room, int[][] taken,
            boolean[] seen) {
        seen[ingredient] = true;
        for (int stack = 0; stack < stacks.size(); stack++) {
            if (ingredients.get(ingredient).test(stacks.get(stack)) && room[stack] > 0) {
                room[stack]--;
                taken[ingredient][stack]++;
                return true;
            }
        }
        for (int stack = 0; stack < stacks.size(); stack++) {
            if (!ingredients.get(ingredient).test(stacks.get(stack))) {
                continue;
            }
            for (int other = 0; other < ingredients.size(); other++) {
                if (!seen[other] && taken[other][stack] > 0 && take(other, ingredients, stacks, room, taken, seen)) {
                    taken[other][stack]--;
                    taken[ingredient][stack]++;
                    return true;
                }
            }
        }
        return false;
    }

    /** How many servings of {@code recipe} cook together: all that the ingredients make, as far as the meal's stack has room. */
    public static int batch(CookingPotBlockEntity pot, CookingPotRecipe recipe) {
        ItemStack result = recipe.getResultItem(pot.getLevel() == null ? RegistryAccess.EMPTY : pot.getLevel().registryAccess());
        ItemStack meal = pot.getMeal();
        if (result.isEmpty() || !meal.isEmpty() && !ItemStack.isSameItemSameTags(meal, result)) {
            return 0;
        }
        return Math.max(0, Math.min(servingsLeft(pot, recipe), (result.getMaxStackSize() - meal.getCount()) / result.getCount()));
    }

    /** How many times as fast as one serving a batch cooks: sixteen servings take four times as long as one, so a full pot is still work. */
    public static float speed(int batch) {
        return PACE / (float) Math.sqrt(Mth.clamp(batch, 1, SLOWEST_BATCH));
    }

    /** How many stars a player can fill for a dish: one the first time, more the more of it they have made. */
    public static int openStars(Player player, ItemStack dish) {
        if (!(player instanceof ServerPlayer server) || dish.isEmpty()) {
            return 1;
        }
        return openStars(server.getStats().getValue(Stats.ITEM_CRAFTED.get(dish.getItem())));
    }

    /** How many stars {@code made} servings of a dish open. */
    public static int openStars(int made) {
        return made >= THIRD_STAR ? 3 : made >= SECOND_STAR ? 2 : 1;
    }

    /** How far the next star is, 0 to 1, after {@code made} servings; 1 once all are open. */
    public static float nextStar(int made) {
        return made >= THIRD_STAR ? 1.0F : made >= SECOND_STAR
                ? (float) (made - SECOND_STAR) / (THIRD_STAR - SECOND_STAR) : (float) made / SECOND_STAR;
    }

    /** The share of a batch the most stars a cook has opened can add, in whole percent. */
    public static int bonusPercent(int stars) {
        return stars >= 3 ? 50 : stars == 2 ? 25 : 0;
    }

    /** The share of a serving that each well stirred one brings: two stars one more for every four, three stars one more for every two. */
    public static float bonusRate(int stars) {
        return bonusPercent(stars) / 100.0F;
    }

    /**
     * The whole servings that well stirred {@code cooked} ones bring, with {@code carried}, the part of a serving earned by the batches
     * before. Rounded down and carried on: a single serving at three stars brings half of one, and the next one makes it whole, so no
     * size of batch earns more than another.
     */
    public static int bonus(int cooked, int stars, float carried) {
        return Mth.floor(cooked * bonusRate(stars) + carried + 1.0e-4F);
    }

    /** What the menu's result slot does when a player takes a serving: experience, recipes unlocked. */
    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.equals(CookingPotStation.SERVED_SLOT)) {
            served(station, player, stack);
        }
    }

    private void served(StationContext station, Player player, ItemStack stack) {
        stack.onCraftedBy(station.level(), player, stack.getCount());
        pot.awardUsedRecipes(player, pot.getDroppableInventory());
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (CookingPotStation.ACTION_SCOOP.equals(action)) {
            if (data.getBoolean(CookingPotStation.FROM_INVENTORY)) {
                scoopFromInventory(station, player, data.getBoolean(CookingPotStation.SCOOP_ALL));
            } else {
                scoop(station, player, data.getBoolean(CookingPotStation.SCOOP_ALL));
            }
        } else if (CookingPotStation.ACTION_FILL.equals(action)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString(CookingPotStation.DISH));
            Platform.INSTANCE.cookingRecipes(station.level()).stream().filter(recipe -> recipe.getId().equals(id)).findFirst()
                    .ifPresent(recipe -> fill(player, recipe, data.getBoolean(CookingPotStation.SCOOP_ALL) ? CookingPotStation.FILL_MOST : 1));
        } else if (CookingPotStation.ACTION_STIR.equals(action)) {
            long now = station.level().getGameTime();
            if (isStirring(station) && !player.getUUID().equals(cook)) {
                return;
            }
            stirredAt = now;
            ((PotStirring) pot).handson$stir(now + STIR_TICKS);
            float x = spoon(data.getFloat(CookingPotStation.STIR_X));
            float z = spoon(data.getFloat(CookingPotStation.STIR_Z));
            ItemStack dish = result(station);
            cook = player.getUUID();
            float at = data.contains(CookingPotStation.STIR_AT) && Float.isFinite(data.getFloat(CookingPotStation.STIR_AT))
                    ? data.getFloat(CookingPotStation.STIR_AT) : Float.NaN;
            ((PotStirring) pot).handson$skill().move(now, at, x, z, pot.isHeated() && batch(station) > 0, openStars(player, dish));
            station.state().setControl(CookingPotStation.SPOON_X, x);
            station.state().setControl(CookingPotStation.SPOON_Z, z);
        }
    }

    /** Where the client says the spoon is, -1 to 1 across the pot; a broken value is the middle, never a NaN kept in the stars. */
    private static float spoon(float at) {
        return Float.isFinite(at) ? Mth.clamp(at, -1.0F, 1.0F) : 0.0F;
    }

    /**
     * Puts up to {@code sets} sets of the recipe's ingredients from the player's inventory into the pot, one slot per ingredient as
     * Farmer's Delight lays them, only whole sets. An empty pot takes any dish; one that holds food only more of what it makes.
     */
    private void fill(Player player, CookingPotRecipe recipe, int sets) {
        List<Ingredient> ingredients = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        if (ingredients.isEmpty() || ingredients.size() > CookingPotBlockEntity.MEAL_DISPLAY_SLOT) {
            return;
        }
        ItemStack[] inPot = new ItemStack[CookingPotBlockEntity.MEAL_DISPLAY_SLOT];
        for (int i = 0; i < inPot.length; i++) {
            inPot[i] = Platform.INSTANCE.getItem(pot, i).copy();
            boolean fits = i < ingredients.size() ? inPot[i].isEmpty() || ingredients.get(i).test(inPot[i]) : inPot[i].isEmpty();
            if (!fits) {
                return;
            }
        }
        Inventory inventory = player.getInventory();
        int done = 0;
        while (done < sets) {
            int[] from = new int[ingredients.size()];
            for (int i = 0; i < from.length; i++) {
                from[i] = source(inventory, ingredients.get(i), inPot[i], from, i);
                if (from[i] < 0) {
                    break;
                }
            }
            if (Arrays.stream(from).anyMatch(slot -> slot < 0)) {
                break;
            }
            for (int i = 0; i < from.length; i++) {
                ItemStack one = inventory.removeItem(from[i], 1);
                if (inPot[i].isEmpty()) {
                    inPot[i] = one;
                } else {
                    inPot[i].grow(1);
                }
            }
            done++;
        }
        if (done == 0) {
            return;
        }
        for (int i = 0; i < ingredients.size(); i++) {
            Platform.INSTANCE.setItem(pot, i, inPot[i]);
        }
        unsorted = true;
    }

    /**
     * The inventory slot that gives the next item for the pot's slot {@code index}: one the ingredient takes, alike to what lies there,
     * with room left in the pot's stack, and not wanted by an earlier ingredient of this set beyond what it holds; -1 for none.
     */
    private static int source(Inventory inventory, Ingredient ingredient, ItemStack there, int[] taken, int index) {
        if (!there.isEmpty() && there.getCount() >= there.getMaxStackSize()) {
            return -1;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !ingredient.test(stack) || !there.isEmpty() && !ItemStack.isSameItemSameTags(there, stack)) {
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

    /** A container on the player's cursor takes a serving, or all of them do while there is soup; the filled ones go to the inventory. */
    private void scoop(StationContext station, Player player, boolean all) {
        int scooped = 0;
        boolean filled;
        do {
            ItemStack carried = player.inventoryMenu.getCarried();
            ItemStack serving = pot.useHeldItemOnMeal(carried);
            filled = !serving.isEmpty();
            if (filled) {
                scooped++;
                served(station, player, serving);
                if (carried.isEmpty()) {
                    player.inventoryMenu.setCarried(serving);
                } else if (!player.getInventory().add(serving)) {
                    player.drop(serving, false);
                }
            }
        } while (all && filled);
        if (scooped > 0) {
            station.effect(CookingPotStation.EFFECT_SCOOPED, "broth", new CompoundTag());
        }
    }

    /**
     * An empty hand over the soup: a container from the inventory takes a serving, or, with {@code all}, every one there is room
     * for. The servings go into the inventory; the hand stays empty.
     */
    private void scoopFromInventory(StationContext station, Player player, boolean all) {
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        int scooped = 0;
        int slot;
        while ((slot = containerSlot(pot, inventory)) >= 0) {
            ItemStack serving = pot.useHeldItemOnMeal(inventory.getItem(slot));
            if (serving.isEmpty()) {
                break;
            }
            scooped++;
            served(station, player, serving);
            if (!inventory.add(serving)) {
                player.drop(serving, false);
            }
            if (!all) {
                break;
            }
        }
        if (scooped > 0) {
            inventory.setChanged();
            station.effect(CookingPotStation.EFFECT_SCOOPED, "broth", new CompoundTag());
        }
    }

    /** The inventory slot whose stack the soup is served in: the one in hand first, then the hotbar and the rest; -1 without one. */
    public static int containerSlot(CookingPotBlockEntity pot, Inventory inventory) {
        if (pot.getMeal().isEmpty()) {
            return -1;
        }
        if (!inventory.getSelected().isEmpty() && pot.isContainerValid(inventory.getSelected())) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (!inventory.getItem(slot).isEmpty() && pot.isContainerValid(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /** What the ingredients in the pot make. */
    private ItemStack result(StationContext station) {
        recipe(station);
        return resultMade;
    }

    /** The recipe the ingredients in the pot make, or null. */
    private CookingPotRecipe recipe(StationContext station) {
        ItemStack[] inPot = station.state().getItems(CookingPotStation.INGREDIENTS);
        boolean same = true;
        for (int i = 0; i < resultFor.length && same; i++) {
            same = resultFor[i] != null && ItemStack.matches(resultFor[i], inPot[i]);
        }
        if (!same) {
            for (int i = 0; i < resultFor.length; i++) {
                resultFor[i] = inPot[i].copy();
            }
            recipeMade = Platform.INSTANCE.findRecipe(station.level(), List.of(inPot)).orElse(null);
            resultMade = recipeMade == null ? ItemStack.EMPTY : recipeMade.getResultItem(station.level().registryAccess());
        }
        return recipeMade;
    }

    /** How many servings the ingredients in the pot cook together. */
    private int batch(StationContext station) {
        CookingPotRecipe recipe = recipe(station);
        return recipe == null ? 0 : batch(pot, recipe);
    }

    private void refresh(StationContext station) {
        StationState state = station.state();
        ContainerData data = ((CookingPotBlockEntityAccessor) pot).handson$data();
        int time = data.get(0);
        int total = data.get(1);
        // The whole batch cooks at once, and the dial shows how far it is.
        ItemStack dish = result(station);
        int batch = batch(station);
        float speed = speed(batch);
        float progress = batch > 0 && total > 0 ? Math.min(1.0F, (float) time / total) : 0.0F;
        state.setControl(CookingPotStation.PROGRESS, progress);
        state.setControl(CookingPotStation.HEATED, pot.isHeated() ? 1.0F : 0.0F);
        long now = station.level().getGameTime();
        boolean stirred = ((PotStirring) pot).handson$isStirred(now);
        state.setControl(CookingPotStation.STIRRING, stirred ? 1.0F : 0.0F);
        if (!station.level().isClientSide()) {
            state.setControl(CookingPotStation.STARS, ((PotStirring) pot).handson$skill().stars(now));
        }
        if (!stirred) {
            state.setControl(CookingPotStation.SPOON_X, 0.0F);
            state.setControl(CookingPotStation.SPOON_Z, 0.0F);
        }
        CompoundTag own = state.data().copy();
        // In whole seconds, as the badge tells it: the clients hear of it once a second, not every tick.
        own.putInt(CookingPotStation.COOK_LEFT, Mth.ceil(total * (1.0F - progress) / speed / 20.0F) * 20);
        if (!station.level().isClientSide()) {
            own.putInt(CookingPotStation.GRADE, ((PotStirring) pot).handson$grade());
            own.putInt(CookingPotStation.BONUS, ((PotStirring) pot).handson$bonus());
            if (!dish.isEmpty()) {
                // A finished soup keeps the stars of the dish it was.
                own.putInt(CookingPotStation.OPEN_STARS, openStars(cook(station), dish));
            }
        }
        state.setData(own);
        int stacks = 0;
        for (ItemStack stack : state.getItems(CookingPotStation.INGREDIENTS)) {
            stacks += stack.isEmpty() ? 0 : 1;
        }
        int level = BROTH_BASE + BROTH_PER_STACK * stacks;
        if (!pot.getMeal().isEmpty()) {
            level = Math.max(level, Math.min(100, SOUP_BASE + SOUP_PER_SERVING * pot.getMeal().getCount()));
        }
        state.setTank(CookingPotStation.BROTH, TankContents.of(HandsOnDelight.id("broth"), level));
    }
}
