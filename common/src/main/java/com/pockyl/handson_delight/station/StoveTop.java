package com.pockyl.handson_delight.station;

import net.minecraft.world.item.ItemStack;

/**
 * A stove's top as the station works it: the grill state kept in the stove's block entity (a mixin), its own counts and its slots.
 * Farmer's Delight's stove and the addon stoves built on its class have it by one mixin; an addon stove with a copy of that class of
 * its own (Nether's Delight's blackstone stove) by a mixin of its own.
 */
public interface StoveTop {
    Grill handson$grill();

    /** The stove's ticks cooked of each slot: kept at nothing for a piece grilled by hand, so it never pops it out. */
    int[] handson$progress();

    /** The stove's ticks each slot takes: set when its own right click puts food on, endless for a piece grilled by hand. */
    int[] handson$times();

    /** Places whose piece the stove's own code cooks this tick for the station (not saved): see {@link StoveCooking}. */
    boolean[] handson$cookingNow();

    /** What lies on {@code slot} of the stove's own inventory. */
    ItemStack handson$item(int slot);

    void handson$setItem(int slot, ItemStack stack);

    /** How many slots the stove's own inventory has. */
    int handson$slots();

    /** Whether something stands on the stove (a pot, a skillet): then its top is no grill, and the stove drops what lay there. */
    boolean handson$covered();

    /**
     * Whether the stove's own code cooks a piece the station finished, so that what other mods add to it still happens. A stove whose
     * code throws its food where nobody catches it has the station finish the piece itself.
     */
    default boolean handson$cooksForTheStation() {
        return true;
    }
}
