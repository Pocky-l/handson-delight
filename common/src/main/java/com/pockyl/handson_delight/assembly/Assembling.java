package com.pockyl.handson_delight.assembly;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A dish made of the layers on the board by its own recipe, round after round as the crafting table would make it: each round takes
 * an item of every layer, and what the recipe leaves of it (a bucket, a bottle) goes back onto that layer when it is the same thing
 * (FD's dough keeps its water) or to the cook. A potion poured out leaves its bottle too, though the crafting table keeps it.
 */
public final class Assembling {
    private Assembling() {
    }

    /** What the rounds made: the dishes, what is left of every layer, and what the recipe left that is no layer's. */
    public record Made(List<ItemStack> dishes, List<ItemStack> layers, List<ItemStack> leftovers, int rounds) {
    }

    /**
     * The dish {@code layers} make here: they are all of it, and its own recipe makes it of them. A recipe may ask more than its
     * ingredients show (an addon's own check, a tag inside an item): layers it refuses make no dish, so a click takes them off again
     * instead of trying in vain.
     */
    public static Optional<AssemblyRecipes.Assembly> dishOf(List<ItemStack> layers, Level level) {
        return dishOf(layers, level, null);
    }

    /**
     * {@link #dishOf(List, Level)}, the dish {@code picked} in the book first (null: none): it wins over another dish of the same layers
     * (Ender's Delight's and Vanilla Delights' chorus pies are one craft).
     */
    public static Optional<AssemblyRecipes.Assembly> dishOf(List<ItemStack> layers, Level level, Identifier picked) {
        List<AssemblyRecipes.Assembly> all = AssemblyRecipes.all();
        for (AssemblyRecipes.Assembly dish : all) {
            if (dish.id().equals(picked) && makes(dish, layers, level)) {
                return Optional.of(dish);
            }
        }
        for (AssemblyRecipes.Assembly dish : all) {
            if (makes(dish, layers, level)) {
                return Optional.of(dish);
            }
        }
        return Optional.empty();
    }

    private static boolean makes(AssemblyRecipes.Assembly dish, List<ItemStack> layers, Level level) {
        return AssemblyRecipes.assign(dish, layers, true) != null && make(dish, layers, level, 1).rounds() > 0;
    }

    /** Up to {@code most} rounds of {@code dish} from {@code layers} (bottom first, not changed). */
    public static Made make(AssemblyRecipes.Assembly dish, List<ItemStack> layers, Level level, int most) {
        List<ItemStack> left = new ArrayList<>();
        layers.forEach(layer -> left.add(layer.copy()));
        List<ItemStack> dishes = new ArrayList<>();
        List<ItemStack> leftovers = new ArrayList<>();
        int[] taken = AssemblyRecipes.assign(dish, left, true);
        int rounds = 0;
        while (taken != null && rounds < most && left.stream().noneMatch(ItemStack::isEmpty)) {
            AssemblyGrid grid = new AssemblyGrid(dish, left, taken);
            if (!dish.recipe().matches(grid.input(), level)) {
                break;
            }
            ItemStack made = dish.recipe().assemble(grid.input());
            if (made.isEmpty()) {
                break;
            }
            NonNullList<ItemStack> remainders = dish.recipe().getRemainingItems(grid.input());
            for (int layer = 0; layer < left.size(); layer++) {
                ItemStack stack = left.get(layer);
                ItemStack remainder = leftOf(stack, grid.remainder(remainders, AssemblyGrid.slot(dish, taken, layer)));
                stack.shrink(1);
                if (remainder.isEmpty()) {
                    continue;
                }
                if (stack.isEmpty()) {
                    left.set(layer, remainder.copy());
                } else if (ItemStack.isSameItemSameComponents(stack, remainder)
                        && stack.getCount() + remainder.getCount() <= stack.getMaxStackSize()) {
                    stack.grow(remainder.getCount());
                } else {
                    leftovers.add(remainder.copy());
                }
            }
            add(dishes, made);
            rounds++;
        }
        // A layer that turned into something else (milk's bucket) is no ingredient any more: it goes to the cook.
        for (int layer = 0; layer < left.size(); layer++) {
            ItemStack stack = left.get(layer);
            if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, layers.get(layer))) {
                leftovers.add(stack);
                left.set(layer, ItemStack.EMPTY);
            }
        }
        return new Made(dishes, left, leftovers, rounds);
    }

    /**
     * What making the dish leaves of {@code layer}: what its recipe says, and a potion's glass bottle the crafting table keeps. Water
     * poured from a bottle into the dough leaves the empty bottle with the cook, as a water bucket leaves its bucket.
     */
    private static ItemStack leftOf(ItemStack layer, ItemStack remainder) {
        return remainder.isEmpty() && layer.is(Items.POTION) ? new ItemStack(Items.GLASS_BOTTLE) : remainder;
    }

    /** Adds {@code stack} to the first stack it joins, else as a new one. */
    private static void add(List<ItemStack> stacks, ItemStack stack) {
        for (ItemStack there : stacks) {
            if (ItemStack.isSameItemSameComponents(there, stack)) {
                there.grow(stack.getCount());
                return;
            }
        }
        stacks.add(stack.copy());
    }
}
