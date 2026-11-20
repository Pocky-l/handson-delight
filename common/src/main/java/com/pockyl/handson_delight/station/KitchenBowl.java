package com.pockyl.handson_delight.station;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.pockyl.handson_delight.KitchenRecipes;
import com.pockyl.handson_delight.RecipeParts;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extra Delight's mixing bowl, as the station sees it: mixed into its block entity on NeoForge, the only loader that mod has. Nine
 * places for what is mixed (one of each a set, stacks are sets), a place for the container the result goes into, and a tank of up
 * to six liquids, poured in 250 mB from a bottle or 1000 mB from a bucket. A recipe is mixed by its utensil (a spoon, a whisk)
 * {@code stirs} times; then the bowl gives the result to the cook, uses one set and the recipe's liquids, and its container. Every
 * step goes through the bowl's own code.
 */
public interface KitchenBowl {
    String MOD_ID = "extradelight";
    /** The places for what is mixed, and the place of the container. */
    int PLACES = 9;
    int CONTAINER_SLOT = 9;
    /** A bottle of a liquid, and a bucket, in millibuckets, as the bowl pours them. */
    int BOTTLE = 250;
    int BUCKET = 1000;

    /** The bowl of a block entity, or null for anything else. */
    static KitchenBowl of(BlockEntity entity) {
        return entity instanceof KitchenBowl bowl ? bowl : null;
    }

    /** A liquid in the bowl: the fluid's id and how much of it, millibuckets. */
    record Liquid(Identifier fluid, int amount) {
    }

    /** A liquid a recipe wants: any of {@code fluids}, at least {@code amount}; {@code shown} is what it is poured from (a bottle). */
    record LiquidNeed(List<Identifier> fluids, int amount, ItemStack shown) {
        public boolean takes(Identifier fluid) {
            return fluids.contains(fluid);
        }

        /** How many times what lies in the bowl holds this much of it. */
        int timesIn(List<Liquid> liquids) {
            int most = 0;
            for (Liquid liquid : liquids) {
                if (takes(liquid.fluid()) && amount > 0) {
                    most = Math.max(most, liquid.amount() / amount);
                }
            }
            return amount <= 0 ? Integer.MAX_VALUE : most;
        }
    }

    /**
     * A recipe of the bowl: what goes in (one of each, in any of the nine places), the liquids, the container the result goes into
     * (empty for none; {@link #containersPer} of it are used up a set), the utensil that mixes it, how many stirs, the result.
     */
    record Mixing(Identifier id, List<Ingredient> ingredients, List<LiquidNeed> liquids, ItemStack container, Ingredient utensil,
            int stirs, ItemStack result) {
        public boolean hasContainer() {
            return !container.isEmpty();
        }

        /** The containers a set takes: the bowl wants as many as the recipe names and uses as many as it makes. */
        public int containersPer() {
            return hasContainer() ? Math.max(container.getCount(), result.getCount()) : 0;
        }

        /** What the cook brings, for the book: the food, each liquid as what it is poured from, then the container. */
        public List<Ingredient> needed() {
            List<Ingredient> all = new ArrayList<>(ingredients);
            for (LiquidNeed liquid : liquids) {
                // A bucket once; bottles as many as it takes (three of water for 750 mB).
                boolean bucket = RecipeParts.remainder(liquid.shown().getItem()) == net.minecraft.world.item.Items.BUCKET;
                int times = bucket ? 1 : Math.max(1, (liquid.amount() + BOTTLE - 1) / BOTTLE);
                for (int i = 0; i < times && !liquid.shown().isEmpty(); i++) {
                    all.add(Ingredient.of(liquid.shown().getItem()));
                }
            }
            if (hasContainer()) {
                all.add(Ingredient.of(container.getItem()));
            }
            return all;
        }
    }

    /** What Extra Delight knows besides the bowl itself: its recipes, and its liquids in their containers. Set by its loader. */
    interface Reader {
        List<Mixing> read(Level level);

        /** What a liquid is poured from: a bottle of it, or for a bucketful its bucket; empty when Extra Delight knows none. */
        ItemStack shown(Identifier fluid, int amount);
    }

    /** The reader of the loader that has Extra Delight. */
    final class Mod {
        private static Reader reader;
        private static WeakReference<Object> readFrom = new WeakReference<>(null);
        private static List<Mixing> read = List.of();

        private Mod() {
        }
    }

    static void use(Reader reader) {
        Mod.reader = reader;
    }

    /** The bowl's recipes as this level knows them (a client knows them too), in the order the bowl tries them. */
    static List<Mixing> recipes(Level level) {
        synchronized (Mod.class) {
            if (Mod.reader == null) {
                return List.of();
            }
            Object manager = KitchenRecipes.source(level);
            if (Mod.readFrom.get() != manager) {
                Mod.read = List.copyOf(Mod.reader.read(level));
                Mod.readFrom = new WeakReference<>(manager);
            }
            return Mod.read;
        }
    }

    /** What {@code fluid} is poured from, for the book and the colour of the liquid. */
    static ItemStack shown(Identifier fluid, int amount) {
        return Mod.reader == null ? ItemStack.EMPTY : Mod.reader.shown(fluid, amount);
    }

    /**
     * The recipe what lies in the bowl mixes into, as the bowl reads it but for its container: the food one a place with nothing over,
     * every liquid it wants in the bowl. A recipe whose container lies in the container place comes first, as the bowl itself wants it
     * there.
     */
    static Optional<Mixing> making(List<Mixing> recipes, ItemStack[] places, List<Liquid> liquids, ItemStack container) {
        Mixing loose = null;
        List<ItemStack> food = new ArrayList<>();
        for (ItemStack stack : places) {
            if (!stack.isEmpty()) {
                food.add(stack);
            }
        }
        for (Mixing recipe : recipes) {
            if (!foodFits(recipe, food) || !liquidsFit(recipe, liquids)) {
                continue;
            }
            boolean containerFits = recipe.hasContainer() ? ItemStack.isSameItem(recipe.container(), container)
                    && container.getCount() >= recipe.container().getCount() : container.isEmpty();
            if (containerFits) {
                return Optional.of(recipe);
            }
            if (loose == null) {
                loose = recipe;
            }
        }
        return Optional.ofNullable(loose);
    }

    private static boolean foodFits(Mixing recipe, List<ItemStack> food) {
        return recipe.ingredients().isEmpty() ? food.isEmpty() : StationRecipes.matches(recipe.ingredients(), food);
    }

    static boolean liquidsFit(Mixing recipe, List<Liquid> liquids) {
        return recipe.liquids().stream().allMatch(need -> need.timesIn(liquids) >= 1);
    }

    /** How many sets of {@code recipe} what lies in the bowl makes: the smallest stack, and each liquid as often as it is there. */
    static int sets(Mixing recipe, ItemStack[] places, List<Liquid> liquids) {
        int sets = Integer.MAX_VALUE;
        for (ItemStack stack : places) {
            if (!stack.isEmpty()) {
                sets = Math.min(sets, stack.getCount());
            }
        }
        for (LiquidNeed need : recipe.liquids()) {
            sets = Math.min(sets, need.timesIn(liquids));
        }
        // Liquids alone (whipped cream of milk): as often as they are there, but never more than a stack of the result.
        return sets == Integer.MAX_VALUE ? 1 : Math.max(0, Math.min(sets, 64));
    }

    BlockEntity handson$blockEntity();

    /** A place (0 to 8, or {@link #CONTAINER_SLOT}). */
    ItemStack handson$item(int slot);

    /** Puts a stack in a place, as the bowl's own menu does: it starts its stirs over and finds its recipe anew. */
    void handson$setItem(int slot, ItemStack stack);

    /** The liquids in the bowl, in the order they were poured. */
    List<Liquid> handson$liquids();

    /**
     * Pours one of {@code full} into the bowl as its own menu would (a bottle 250 mB, a bucket 1000 mB, any container with a liquid in
     * it as much as goes): returns what is left of the container (an empty bottle, a bucket), or null when nothing can be poured.
     */
    ItemStack handson$pour(ItemStack full, boolean simulate);

    /** The liquid one of {@code full} pours into the bowl, and how much; null when it pours none. */
    Liquid handson$liquidOf(ItemStack full);

    /** Fills one of {@code empty} with a liquid of the bowl: the full container, or null when it takes none of them. */
    ItemStack handson$draw(ItemStack empty, boolean simulate);

    /** Cracks one egg over the bowl as its own click does: the white into the bowl, the yolk to {@code player}. False when it is full. */
    boolean handson$separate(Player player);

    /** The stirs the bowl has counted for what lies in it; it starts over whenever a place changes. */
    int handson$stirs();

    void handson$setStirs(int stirs);

    /**
     * The last stir of a set: the bowl's own {@code mix} with {@code utensil}, its stirs counted full, which gives the result to
     * {@code player}, the empty containers of what went in too, and uses one of each place, the liquids and the container. False
     * when it made nothing (no recipe of its own fits what lies in it, or not that utensil).
     */
    boolean handson$mix(Player player, ItemStack utensil);
}
