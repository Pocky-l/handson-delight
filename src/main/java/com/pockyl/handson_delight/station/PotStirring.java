package com.pockyl.handson_delight.station;

/** What the pot's block entity knows about stirring; mixed into Farmer's Delight's class. */
public interface PotStirring {
    /** The pot counts as stirred until the game time {@code until}. */
    void handson$stir(long until);

    boolean handson$isStirred(long gameTime);

    /** How many times as fast it cooks. */
    void handson$setSpeed(float speed);
}
