package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.pockyl.diegesis.api.station.StationContext;

import com.pockyl.handson_delight.KitchenRecipes;
import com.pockyl.handson_delight.RecipeParts;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Extra Delight's oven, as the oven station sees it: a small brick oven, open in front, standing over Farmer's Delight's heat. What
 * bakes lies in bakeware that stands in it (a sheet, a tray, a pie dish, a square pan, a loaf pan, a muffin tin, a bowl or a skillet:
 * every recipe names one, which stays in the oven unless the recipe uses it up). The nine places are one set a bake, one of each place,
 * stacks are sets; the set bakes into the output. The station pulls the bakeware out onto the oven's front for the cook; while it is
 * out the heat does not reach it, so its count of how far it has baked stays where it was ({@link #whileDoor}).
 */
public final class EdOven implements KitchenOven {
    public static final String MOD_ID = "extradelight";
    public static final Identifier OVEN_ID = Identifier.fromNamespaceAndPath(MOD_ID, "oven");
    public static final Identifier BAKING = Identifier.fromNamespaceAndPath(MOD_ID, "oven");
    public static final int PLACES = 9;
    public static final int BAKEWARE_SLOT = 10;
    public static final int OUTPUT_SLOT = 11;
    /** As its slots hold: a whole stack a place. */
    public static final int PER_PLACE = 64;

    /** What the recipes were listed from: see {@link KitchenRecipes#source}. */
    private static Object listedFor;
    private static List<Baking> listed = List.of();
    private static Set<Item> bakewareItems = Set.of();

    private final KitchenEdOven oven;
    /** While the bakeware is out: how far it had baked, which it stays at until it is back in; -1 with it in. */
    private int heldAt = -1;
    /** What the set in it makes, as found in game tick {@link #madeAt}. */
    private Optional<Baking> made = Optional.empty();
    private long madeAt = Long.MIN_VALUE;

    private EdOven(KitchenEdOven oven) {
        this.oven = oven;
    }

    /** The oven of a block entity, or null for anything else. */
    public static EdOven of(BlockEntity entity) {
        return entity instanceof KitchenEdOven oven ? new EdOven(oven) : null;
    }

    @Override
    public BlockEntity blockEntity() {
        return oven.handson$entity();
    }

    private void setItem(int slot, ItemStack stack) {
        oven.handson$setItem(slot, stack);
    }

    @Override
    public int places() {
        return PLACES;
    }

    @Override
    public ItemStack place(int index) {
        return oven.handson$item(index);
    }

    @Override
    public void setPlace(int index, ItemStack stack) {
        setItem(index, stack);
    }

    @Override
    public int outputs() {
        return 1;
    }

    @Override
    public ItemStack output(int index) {
        return oven.handson$item(OUTPUT_SLOT);
    }

    @Override
    public void setOutput(int index, ItemStack stack) {
        setItem(OUTPUT_SLOT, stack);
    }

    @Override
    public boolean usesBakeware() {
        return true;
    }

    @Override
    public ItemStack bakeware() {
        return oven.handson$item(BAKEWARE_SLOT);
    }

    @Override
    public void setBakeware(ItemStack stack) {
        setItem(BAKEWARE_SLOT, stack);
    }

    /** What one of its recipes bakes in. */
    @Override
    public boolean isBakeware(ItemStack stack) {
        Level level = blockEntity().getLevel();
        return !stack.isEmpty() && level != null && isBakeware(level, stack);
    }

    public static boolean isBakeware(Level level, ItemStack stack) {
        recipesOf(level);
        synchronized (EdOven.class) {
            return bakewareItems.contains(stack.getItem());
        }
    }

    @Override
    public boolean bakesEachPlace() {
        return false;
    }

    @Override
    public int perPlace() {
        return PER_PLACE;
    }

    /** Anything goes into its places but bakeware, which stands under them: what it makes depends on the whole set. */
    @Override
    public boolean takes(ItemStack stack) {
        return !stack.isEmpty() && !isBakeware(stack);
    }

    /** The recipe the set in it bakes into, in the bakeware standing there; found once a tick. */
    private Optional<Baking> making() {
        Level level = blockEntity().getLevel();
        if (level == null) {
            return Optional.empty();
        }
        if (madeAt != level.getGameTime()) {
            ItemStack[] grid = new ItemStack[PLACES];
            for (int place = 0; place < PLACES; place++) {
                grid[place] = place(place);
            }
            made = KitchenOven.matching(recipes(level), grid, bakeware());
            madeAt = level.getGameTime();
        }
        return made;
    }

    /** Ticks the set takes: its recipe's, else what the oven counted last. */
    private int total() {
        return making().map(Baking::ticks).orElse(oven.handson$cookTimeTotal());
    }

    /** The whole set bakes as one: each place that holds something is as far on as the set. */
    @Override
    public float progress(int place) {
        int time = oven.handson$cookTime();
        int total = time > 0 ? total() : 0;
        return place(place).isEmpty() || time <= 0 || total <= 0 ? 0.0F : Math.min(1.0F, (float) time / total);
    }

    @Override
    public int ticksLeft(int place) {
        int time = oven.handson$cookTime();
        int total = time > 0 ? total() : 0;
        return place(place).isEmpty() || time <= 0 || total <= 0 ? 0 : Math.max(0, total - time);
    }

    @Override
    public boolean isHot() {
        return oven.handson$isHeated();
    }

    /** A set that makes a dish lies in its bakeware, and no heat under it. */
    @Override
    public boolean waitsForHeat() {
        return !isHot() && making().isPresent();
    }

    /** Its own reward for what is taken out: experience and the recipes unlocked, as from its menu. */
    @Override
    public void onTaken(Player player, ItemStack stack) {
        KitchenOven.super.onTaken(player, stack);
        if (!player.level().isClientSide()) {
            oven.handson$award(player);
        }
    }

    /** With the bakeware out the heat does not reach it: its count of how far it has baked stays where it was. */
    @Override
    public void whileDoor(StationContext station, boolean open) {
        if (!open) {
            heldAt = -1;
            return;
        }
        int time = oven.handson$cookTime();
        if (heldAt < 0 || time < heldAt) {
            heldAt = time;
        }
        if (time != heldAt) {
            oven.handson$setCookTime(heldAt);
        }
    }

    @Override
    public List<Baking> recipes(Level level) {
        return recipesOf(level);
    }

    /**
     * Its recipes as this level knows them (a client knows them too), each with its bakeware: the book's tab is the bakeware's item id.
     * Listed once a recipe manager.
     */
    public static synchronized List<Baking> recipesOf(Level level) {
        Object manager = KitchenRecipes.source(level);
        if (manager == listedFor) {
            return listed;
        }
        List<Baking> recipes = new ArrayList<>();
        Set<Item> bakeware = new HashSet<>();
        for (RecipeHolder<?> holder : KitchenRecipes.recipes(level)) {
            Recipe<?> recipe = holder.value();
            if (!BAKING.equals(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()))) {
                continue;
            }
            try {
                ItemStack result = RecipeParts.result(recipe);
                List<Ingredient> ingredients = List.copyOf(RecipeParts.ingredients(recipe));
                ItemStack container = (ItemStack) recipe.getClass().getMethod("getOutputContainer").invoke(recipe);
                int ticks = (Integer) recipe.getClass().getMethod("getCookTime").invoke(recipe);
                boolean usesUp = (Boolean) recipe.getClass().getMethod("shouldConsumeContainer").invoke(recipe);
                if (result.isEmpty() || ingredients.isEmpty() || ingredients.size() > PLACES || container == null || container.isEmpty()
                        || ingredients.stream().anyMatch(Ingredient::isEmpty)) {
                    continue;
                }
                String kind = BuiltInRegistries.ITEM.getKey(container.getItem()).toString();
                recipes.add(new Baking(holder.id().identifier(), ingredients, 0, 0, result.copy(), ticks, kind, container.copyWithCount(1), usesUp));
                bakeware.add(container.getItem());
            } catch (ReflectiveOperationException | RuntimeException e) {
                // A recipe of a shape this does not know is left to the oven's own menu.
            }
        }
        listedFor = manager;
        listed = List.copyOf(recipes);
        bakewareItems = Set.copyOf(bakeware);
        return listed;
    }
}
