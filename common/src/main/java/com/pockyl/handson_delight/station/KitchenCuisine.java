package com.pockyl.handson_delight.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * Youkai's Feasts' cuisine board, as the station sees it: mixed into its block entity by each loader that has that mod. What lies
 * on it is a list of steps in the order they went on, and each step is checked against its recipes as it goes on, as its own clicks
 * check it: cooked rice, then rice shaped by hand into nigiri and a topping; kelp, rice and fillings rolled up by hand into a maki;
 * a bamboo tube filled and closed. Every step goes through the board's own code, so that what it allows, uses up, gives back and
 * rewards stays its own.
 */
public interface KitchenCuisine {
    String MOD_ID = KettleStation.YOUKAI_MOD_ID;

    /** The board of a block entity, or null for anything else. */
    static KitchenCuisine of(BlockEntity entity) {
        return entity instanceof KitchenCuisine board ? board : null;
    }

    /** A recipe of the board: every step in the order it goes on, an empty ingredient for a step done by hand, and the dish. */
    record Dish(ResourceLocation id, ItemStack result, List<Ingredient> steps) {
        /** How many of the steps are done by hand: shaping, rolling, closing. */
        public long handSteps() {
            return steps.stream().filter(Ingredient::isEmpty).count();
        }
    }

    BlockEntity handson$blockEntity();

    /** What lies on the board, in the order it went on; an empty stack is a step done by hand. Copies. */
    List<ItemStack> handson$contents();

    /** How many of {@code stack} the board takes as its next step (its first step may take several), 0 when it takes none. */
    int handson$takes(ItemStack stack);

    /** Whether the board's next step can be done by hand: rice shaped into nigiri, a sheet rolled up, a bamboo tube closed. */
    boolean handson$byHand();

    /**
     * Puts the next step of {@code held} on the board as its own click does: as many as it takes, a sauce's bottle back to the cook,
     * a slip bottle drained by one; nothing is used up in creative mode. Returns how many went on, 0 when it took none.
     */
    int handson$put(Player player, ItemStack held);

    /** Does the next step by hand; false when there is none. */
    boolean handson$shape(Player player);

    /** The dish as it lies now when it is done (and could be taken), else an empty stack. */
    ItemStack handson$finished();

    /** Gives the finished dish to {@code player} as its empty-hand click does; false when nothing is done. */
    boolean handson$take(Player player);

    /**
     * Cuts the finished dish with {@code tool} as its own knife does (Farmer's Delight's cutting recipe of the dish), throwing the
     * pieces where the board throws them; false when the tool cuts nothing there.
     */
    boolean handson$cut(ItemStack tool);

    /**
     * Takes the last step back off the board: what goes back to the cook (an empty stack for a step done by hand), or null when
     * nothing lies there. What came in a container (rice in its bowl, a sauce in its bottle) goes back into one from the cook's
     * inventory, as many as it took; without them it stays (null).
     */
    ItemStack handson$takeBack(Player player);

    /** The models the board's own renderer draws for what lies on it, in its own block space (facing south). */
    List<ResourceLocation> handson$models();

    /** The models of {@code contents} laid on an empty board, or null when the board would not take them in that order. */
    List<ResourceLocation> handson$modelsOf(List<ItemStack> contents);

    /** What the board takes next; an empty ingredient is the hand. */
    List<Ingredient> handson$hints();

    /** Every recipe of the board, in the recipe manager's order. */
    List<Dish> handson$dishes();
}
