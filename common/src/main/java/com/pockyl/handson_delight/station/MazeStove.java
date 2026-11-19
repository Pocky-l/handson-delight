package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Twilight's Flavor & Delight's maze stove cooks the food of the Twilight Forest and of its own mod in half the time (its
 * {@code placeFood} halves the recipe's time when the dish comes from one of them; 1.20.1's looks at the raw food instead). Pieces
 * laid at the station never pass that method, so the station halves their time itself, and so does its book.
 */
public final class MazeStove {
    private static final Identifier BLOCK = Identifier.fromNamespaceAndPath("twilightdelight", "maze_stove");
    private static final Set<String> FAST = Set.of("twilightforest", "twilightdelight");

    private MazeStove() {
    }

    /** The ticks a recipe of {@code ticks} that makes {@code dish} takes on {@code stove}: half on the maze stove for Twilight dishes. */
    public static int ticks(BlockState stove, ItemStack dish, int ticks) {
        if (!dish.isEmpty() && BuiltInRegistries.BLOCK.getKey(stove.getBlock()).equals(BLOCK)
                && FAST.contains(BuiltInRegistries.ITEM.getKey(dish.getItem()).getNamespace())) {
            return ticks / 2;
        }
        return ticks;
    }
}
