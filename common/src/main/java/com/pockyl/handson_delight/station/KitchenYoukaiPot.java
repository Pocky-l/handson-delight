package com.pockyl.handson_delight.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * One of Youkai's Feasts' cooking pots (its small iron pot, short iron pot and stockpot with water in them), as the station sees
 * it: mixed into their block entity by each loader that has that mod. Food goes in one at a time, only while some recipe of the pot
 * still takes it; when everything of a recipe is in, the pot cooks it for the recipe's time over heat, and the dish comes out: as
 * a block where the pot stood (a pot of a feast, a bowl of soup), or out over it when it is no block. Which pot a recipe needs is the
 * container its dish is served in. Every step goes through the pot's own code, so that what it allows, uses up and gives back
 * stays its own.
 */
public interface KitchenYoukaiPot {
    String MOD_ID = KettleStation.YOUKAI_MOD_ID;

    /** The pot of a block entity, or null for anything else. */
    static KitchenYoukaiPot of(BlockEntity entity) {
        return entity instanceof KitchenYoukaiPot pot ? pot : null;
    }

    /** A recipe of the pots: what goes in (in any order), the dish, the ticks it cooks over heat, and the pot it needs. */
    record Dish(ResourceLocation id, ItemStack result, List<Ingredient> ingredients, int ticks, Item pot) {
    }

    /**
     * The soup in the pot, client side: what it is now ({@code youkaisfeasts:water}, {@code soy_sauce}, {@code miso}, {@code cream},
     * {@code soul}: its soup bases), what it becomes as it cooks, and how far it has become that, 0 to 1.
     */
    record Soup(ResourceLocation now, ResourceLocation next, float toNext) {
    }

    /**
     * Who works at the pot now and what to tell them: the station sets it while somebody is there. {@code done} hears of a dish
     * the pot has finished, before it comes out; a dish that would come out over the pot (one that is no block) goes to
     * {@code player} instead, and {@code served} hears of it.
     */
    interface Cook {
        Player player();

        void done(ItemStack dish);

        void served(ItemStack dish);
    }

    BlockEntity handson$blockEntity();

    /** The pot's own item: the empty pot its dishes are served in, which picks its recipes. */
    Item handson$pot();

    /** What is in the pot, in the order it went in. Copies. */
    List<ItemStack> handson$contents();

    /** Whether the pot takes one of {@code stack} now: some recipe of this pot still has a place for it. */
    boolean handson$takes(ItemStack stack);

    /**
     * Puts one of {@code held} into the pot as its own click does: a bowl or bottle back to the cook, a slip bottle drained by one, a
     * tool worn; nothing is used up in creative mode. False when it took none.
     */
    boolean handson$put(Player player, ItemStack held);

    /**
     * Takes the last thing back out of the pot: what goes back to the cook, or null when nothing is there or it cannot come back
     * (it went in out of a bowl or bottle and the cook has none to take it back in, or it was a slip bottle's drink).
     */
    ItemStack handson$takeBack(Player player);

    /** The recipe cooking now, or null while what is in the pot is no whole recipe. */
    ResourceLocation handson$cooking();

    /** How many ticks it has cooked, and how many it takes; 0 and 0 while nothing cooks. */
    int handson$cooked();

    int handson$cookTime();

    /** The soup as the pot's own renderer would draw it now; client side only. */
    Soup handson$soup();

    /** Whether something under the pot heats it. */
    boolean handson$isHeated();

    /** Every recipe of every pot, in the recipe manager's order; {@link Dish#pot()} says which pot each needs. */
    List<Dish> handson$dishes();

    /** Who works at the pot now, or null. */
    void handson$setCook(Cook cook);

    Cook handson$cook();
}
