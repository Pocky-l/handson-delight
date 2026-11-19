package com.pockyl.handson_delight.station;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A chopping block that is the stump of a tree (Sable Trees'): a board that cuts with Farmer's Delight's recipes, but holds one
 * stack and no dish, one log at a time, splits a log into planks with an axe, and grows too dirty for food after so many cuts.
 * Its rules are the addon's, run through its own code.
 */
public interface KitchenStump extends KitchenBoard {
    /** Whether so much food was cut on it that it takes no more food. */
    boolean handson$isDirty();

    /** How many food items were cut on it. */
    int handson$foodCuts();

    /** The log lying there split into planks with {@code axe}, as the stump's own axe stroke splits it; false when it cannot be. */
    boolean handson$split(ItemStack axe, Player player);

    /** Tells the cook why food is refused, in the addon's own words. */
    void handson$tellDirty(Player player);

    /** Whether the stump will not have {@code stack} now: food once it is too dirty. */
    default boolean handson$refuses(ItemStack stack) {
        return stack.has(DataComponents.FOOD) && handson$isDirty();
    }

    /** The stump of a block entity, or null for anything else. */
    static KitchenStump of(Object entity) {
        return entity instanceof KitchenStump stump ? stump : null;
    }
}
