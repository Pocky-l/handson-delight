package com.pockyl.handson_delight.station;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.crafting.RecipeHolder;
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
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.pockyl.handson_delight.Cookable;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.KitchenTags;

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

    private final KitchenPot pot;
    private final PotCooking cooking;
    private boolean unsorted;
    /** Who stirs the pot, or last opened it while nobody stirred: their stars are the ones that can fill. */
    private UUID cook;
    private long stirredAt = Long.MIN_VALUE / 2;
    /** The ingredients the result was last looked up for, and what they make: the lookup runs only when they change. */
    private final ItemStack[] resultFor;
    private ItemStack resultMade = ItemStack.EMPTY;
    private CookingPotRecipe recipeMade;
    /** The dish the cook last picked in the book: the pot's choice while its food makes it, until the pot is empty. */
    private ResourceLocation wanted;

    public PotLogic(StationContext station) {
        pot = (KitchenPot) station.blockEntity();
        cooking = pot.handson$cooking();
        resultFor = new ItemStack[pot.handson$slots()];
        StationState state = station.state();
        state.bindSlots(CookingPotStation.INGREDIENTS, storage(pot, 0, pot.handson$slots()));
        state.bindSlots(CookingPotStation.MEAL, storage(pot, pot.handson$slots(), 1));
        // After the meal lie the container and then the served dish.
        state.bindSlots(CookingPotStation.SERVED, storage(pot, pot.handson$slots() + 2, 1));
    }

    private static SlotStorage storage(KitchenPot pot, int start, int count) {
        return new SlotStorage() {
            @Override
            public int size() {
                return count;
            }

            @Override
            public ItemStack get(int index) {
                return pot.handson$getItem(start + index);
            }

            @Override
            public void set(int index, ItemStack stack) {
                pot.handson$setItem(start + index, stack);
            }
        };
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        cooking.attend(station.level().getGameTime());
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
        if (slot.group().equals(CookingPotStation.INGREDIENTS) && !station.level().isClientSide()) {
            cooking.feedByHand();
        }
    }

    @Override
    public void tick(StationContext station) {
        if (!station.level().isClientSide()) {
            cooking.attend(station.level().getGameTime());
        }
        if (wanted != null && PotRecipes.kinds(pot).isEmpty()) {
            wanted = null;
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
        List<ItemStack> leftovers = cooking.takeLeftovers();
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
        int saved = cooking.takeSaved();
        Player cook = saved <= 0 ? null : station.users().stream().filter(user -> user.getUUID().equals(this.cook)).findFirst().orElse(null);
        if (cook == null) {
            return;
        }
        for (int i = 0; i < pot.handson$slots(); i++) {
            ItemStack stack = pot.handson$getItem(i);
            if (!stack.isEmpty()) {
                ItemStack back = stack.copyWithCount(Math.min(saved, stack.getCount()));
                pot.handson$setItem(i, stack.copyWithCount(stack.getCount() - back.getCount()));
                if (!cook.getInventory().add(back)) {
                    cook.drop(back, false);
                }
            }
        }
    }

    /**
     * After a cook's change, the food is laid out the way Farmer's Delight reads the recipe it makes ({@link PotRecipes#choose}): two
     * sugars thrown in lie in one slot, and the dish of sugar, sugar and dye wants them in two. Food that makes nothing stays as it lies.
     * More than a recipe's slots hold (cooked rice has one ingredient, so one slot) goes back to the cook; with nobody there it stays.
     */
    private void shareOut(StationContext station) {
        List<ItemStack> kinds = PotRecipes.kinds(pot);
        List<ItemStack> inPot = new ArrayList<>();
        for (int i = 0; i < pot.handson$slots(); i++) {
            inPot.add(pot.handson$getItem(i));
        }
        ResourceLocation found = pot.handson$findRecipe(station.level(), inPot).map(RecipeHolder::id).orElse(null);
        PotRecipes.Choice choice = PotRecipes.choose(pot.handson$recipes(station.level()), kinds, pot.handson$slots(), wanted, found,
                !station.users().isEmpty());
        if (choice != null) {
            List<ItemStack> rest = lay(pot, kinds, choice.parts());
            if (!rest.isEmpty()) {
                handBack(station, rest);
            }
        }
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
     * Lays every kind evenly over its {@code parts} slots, at most a full stack a slot; returns what did not fit. A kind keeps the slots
     * it lay in, so that what floats in the pot stays where it was; a slot whose stack is already right is left alone.
     */
    private static List<ItemStack> lay(KitchenPot pot, List<ItemStack> kinds, int[] parts) {
        return StationRecipes.lay(storage(pot, 0, pot.handson$slots()), kinds, parts);
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return !slot.equals(CookingPotStation.MEAL_SLOT);
    }

    /** What a soup is served in does not go into the water, unless some recipe cooks with it. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        boolean serves = pot.handson$servesIn(stack);
        for (RecipeHolder<CookingPotRecipe> holder : pot.handson$recipes(station.level())) {
            CookingPotRecipe recipe = holder.value();
            if (recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(stack))) {
                return true;
            }
            serves |= ItemStack.isSameItem(recipe.getOutputContainer(), stack);
        }
        return !serves;
    }

    /**
     * Whether {@code recipe} makes its dish of whatever lies in the pot instead of a list of ingredients: Modest Meals' soups, curries
     * and jams take any food beside their base, as Farmer's Delight's pot takes any item.
     */
    public static boolean takesAnything(CookingPotRecipe recipe) {
        return recipe.getIngredients().stream().allMatch(Ingredient::isEmpty);
    }

    /**
     * What the inventory offers at the pot, out of {@code recipes}: what some recipe cooks with, and what a soup is served in. With a recipe
     * that takes anything, everything the pot takes: else the food it wants could never be put in at the station.
     */
    public static boolean offered(List<CookingPotRecipe> recipes, ItemStack stack) {
        boolean anything = false;
        for (CookingPotRecipe recipe : recipes) {
            if (ItemStack.isSameItem(recipe.getOutputContainer(), stack) || recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(stack))) {
                return true;
            }
            anything |= takesAnything(recipe);
        }
        return anything;
    }

    /** What {@code recipe} cooks of what lies in the pot now, as the pot's own step makes it: see {@link KitchenPot#handson$cooked}. */
    public static ItemStack cooked(KitchenPot pot, CookingPotRecipe recipe) {
        return pot.handson$cooked(recipe);
    }

    /** How many times the ingredients in the pot make the recipe: every slot gives one item to a serving. */
    private static int servingsLeft(KitchenPot pot, CookingPotRecipe recipe) {
        List<ItemStack> stacks = new ArrayList<>();
        int left = Integer.MAX_VALUE;
        for (int i = 0; i < pot.handson$slots(); i++) {
            ItemStack stack = pot.handson$getItem(i);
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
    public static int batch(KitchenPot pot, CookingPotRecipe recipe) {
        return batch(pot, recipe, cooked(pot, recipe));
    }

    /** {@link #batch(KitchenPot, CookingPotRecipe)} with {@code result}, what the recipe cooks of what lies in the pot. */
    public static int batch(KitchenPot pot, CookingPotRecipe recipe, ItemStack result) {
        ItemStack meal = pot.handson$meal();
        if (result.isEmpty() || !meal.isEmpty() && !ItemStack.isSameItemSameComponents(meal, result)) {
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

    /** Whether anything lies in the pot: ingredients or a meal. */
    public static boolean hasFood(KitchenPot pot) {
        for (int slot = 0; slot <= pot.handson$slots(); slot++) {
            if (!pot.handson$getItem(slot).isEmpty()) {
                return true;
            }
        }
        return false;
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
        pot.handson$awardRecipes(player);
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
            pot.handson$recipes(station.level()).stream().filter(recipe -> recipe.id().equals(id)).findFirst()
                    .ifPresent(recipe -> fill(player, recipe, data.getBoolean(CookingPotStation.SCOOP_ALL) ? CookingPotStation.FILL_MOST : 1));
        } else if (CookingPotStation.ACTION_TAKE_BACK.equals(action)) {
            takeBack(player);
        } else if (CookingPotStation.ACTION_STIR.equals(action)) {
            long now = station.level().getGameTime();
            if (isStirring(station) && !player.getUUID().equals(cook)) {
                return;
            }
            stirredAt = now;
            cooking.stir(now + STIR_TICKS);
            float x = spoon(data.getFloat(CookingPotStation.STIR_X));
            float z = spoon(data.getFloat(CookingPotStation.STIR_Z));
            ItemStack dish = result(station);
            cook = player.getUUID();
            float at = data.contains(CookingPotStation.STIR_AT) && Float.isFinite(data.getFloat(CookingPotStation.STIR_AT))
                    ? data.getFloat(CookingPotStation.STIR_AT) : Float.NaN;
            cooking.skill().move(now, at, x, z, pot.handson$isHeated() && batch(station) > 0, openStars(player, dish));
            station.state().setControl(CookingPotStation.SPOON_X, x);
            station.state().setControl(CookingPotStation.SPOON_Z, z);
        }
    }

    /**
     * What went in without floating, so that no click on it can take it (milk poured, an egg cracked, sugar dissolved), comes back
     * out whole to the inventory, the last first: nothing put in by mistake stays in the pot for good, and milk comes back in its
     * bucket.
     */
    private void takeBack(Player player) {
        for (int i = pot.handson$slots() - 1; i >= 0; i--) {
            ItemStack stack = pot.handson$getItem(i);
            if (!stack.isEmpty() && sinks(stack)) {
                pot.handson$setItem(i, ItemStack.EMPTY);
                for (int count = stack.getCount(); count > 0; count -= stack.getMaxStackSize()) {
                    ItemStack back = stack.copyWithCount(Math.min(count, stack.getMaxStackSize()));
                    if (!player.getInventory().add(back)) {
                        player.drop(back, false);
                    }
                }
                return;
            }
        }
    }

    /** Whether {@code stack} goes into the soup without floating in it: a liquid poured, an egg cracked, what dissolves. */
    public static boolean sinks(ItemStack stack) {
        return KitchenTags.is(stack, KitchenTags.POURED) || KitchenTags.is(stack, KitchenTags.CRACKED)
                || KitchenTags.is(stack, KitchenTags.DISSOLVES);
    }

    /** Where the client says the spoon is, -1 to 1 across the pot; a broken value is the middle, never a NaN kept in the stars. */
    private static float spoon(float at) {
        return Float.isFinite(at) ? Mth.clamp(at, -1.0F, 1.0F) : 0.0F;
    }

    /**
     * Puts up to {@code sets} whole sets of the recipe's ingredients from the player's inventory into the pot and lays them out the way
     * Farmer's Delight reads the recipe; the picked dish stays the pot's choice while the food makes it. An empty pot takes any dish,
     * one that holds food only more of what it makes; nothing goes in beyond what the slots hold.
     */
    private void fill(Player player, RecipeHolder<CookingPotRecipe> recipe, int sets) {
        StationRecipes.Filled filled = StationRecipes.fill(player.getInventory(), PotRecipes.ingredients(recipe.value()), PotRecipes.kinds(pot),
                pot.handson$slots(), sets);
        if (filled == null) {
            return;
        }
        wanted = recipe.id();
        lay(pot, filled.kinds(), filled.parts());
        cooking.feedByHand();
    }

    /** A container on the player's cursor takes a serving, or all of them do while there is soup; the filled ones go to the inventory. */
    private void scoop(StationContext station, Player player, boolean all) {
        int scooped = 0;
        boolean filled;
        do {
            ItemStack carried = player.inventoryMenu.getCarried();
            ItemStack serving = pot.handson$serve(carried);
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
            ItemStack serving = pot.handson$serve(inventory.getItem(slot));
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
    public static int containerSlot(KitchenPot pot, Inventory inventory) {
        if (pot.handson$meal().isEmpty()) {
            return -1;
        }
        if (!inventory.getSelected().isEmpty() && pot.handson$isContainerValid(inventory.getSelected())) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (!inventory.getItem(slot).isEmpty() && pot.handson$isContainerValid(inventory.getItem(slot))) {
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
            recipeMade = pot.handson$findRecipe(station.level(), List.of(inPot)).map(RecipeHolder::value).orElse(null);
            resultMade = recipeMade == null ? ItemStack.EMPTY : pot.handson$cooked(recipeMade);
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
        ContainerData data = pot.handson$data();
        int time = data.get(0);
        int total = data.get(1);
        // The whole batch cooks at once, and the dial shows how far it is.
        ItemStack dish = result(station);
        int batch = batch(station);
        float speed = speed(batch);
        float progress = batch > 0 && total > 0 ? Math.min(1.0F, (float) time / total) : 0.0F;
        state.setControl(CookingPotStation.PROGRESS, progress);
        state.setControl(CookingPotStation.HEATED, pot.handson$isHeated() ? 1.0F : 0.0F);
        long now = station.level().getGameTime();
        boolean stirred = cooking.isStirred(now);
        state.setControl(CookingPotStation.STIRRING, stirred ? 1.0F : 0.0F);
        if (!station.level().isClientSide()) {
            state.setControl(CookingPotStation.STARS, cooking.skill().stars(now));
        }
        if (!stirred) {
            state.setControl(CookingPotStation.SPOON_X, 0.0F);
            state.setControl(CookingPotStation.SPOON_Z, 0.0F);
        }
        CompoundTag own = state.data().copy();
        // In whole seconds, as the badge tells it: the clients hear of it once a second, not every tick.
        own.putInt(CookingPotStation.COOK_LEFT, Mth.ceil(total * (1.0F - progress) / speed / 20.0F) * 20);
        if (!station.level().isClientSide()) {
            own.putInt(CookingPotStation.GRADE, cooking.grade());
            own.putInt(CookingPotStation.BONUS, cooking.bonus());
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
        if (!pot.handson$meal().isEmpty()) {
            level = Math.max(level, Math.min(100, SOUP_BASE + SOUP_PER_SERVING * pot.handson$meal().getCount()));
        }
        state.setTank(CookingPotStation.BROTH, TankContents.of(HandsOnDelight.id("broth"), level));
    }
}
