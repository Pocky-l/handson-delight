package com.pockyl.handson_delight.station;

/** What the pot's block entity knows about stirring; mixed into Farmer's Delight's class. */
public interface PotStirring {
    /** The pot counts as stirred until the game time {@code until}. */
    void handson$stir(long until);

    boolean handson$isStirred(long gameTime);

    StirSkill handson$skill();

    /** The stars the last batch was done with. */
    int handson$grade();

    /** The servings the stars of the last batch added. */
    int handson$bonus();

    /** Sets of ingredients the last batch saved, once: they are the cook's again. */
    int handson$takeSaved();

    /** A cook works at the pot's station in this tick. */
    void handson$attend(long gameTime);

    /** A cook put food into the pot at the station: what comes in before the pot's next tick is put in by hand. */
    void handson$feedByHand();

    /**
     * What the ingredients left (a milk bucket's bucket) while a cook was at the station, taken once: the cook gets it instead of
     * Farmer's Delight throwing it out beside the pot.
     */
    java.util.List<net.minecraft.world.item.ItemStack> handson$takeLeftovers();

    /** Every tick of the pot: sees whether food came in, and whether by hand or by itself (a hopper, a pipe, a menu). */
    void handson$watchFood(long gameTime);

    /** Right after the pot's own cooking step in a tick: when it served a stirred batch's first serving, the rest follows. */
    void handson$afterCooking();
}
