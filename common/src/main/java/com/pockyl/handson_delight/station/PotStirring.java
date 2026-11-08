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

    /** A cook works at the pot's station in this tick: food coming in now is put in by hand. */
    void handson$attend(long gameTime);

    /** Every tick of the pot: sees whether food came in, and whether by hand or by itself (a hopper, a pipe, a menu). */
    void handson$watchFood(long gameTime);
}
