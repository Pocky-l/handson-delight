package com.pockyl.handson_delight.station;

/** A skillet of Farmer's Delight that fries by hand too: a mixin on its block entity keeps the state. */
public interface FryingPan {
    Frying handson$frying();

    /** Fire Aspect on the skillet item it was placed from: Farmer's Delight's cooking time is shorter by it. */
    int handson$fireAspect();

    /**
     * Farmer's Delight cooks one item of what is in the pan at once, through its own code, so that what other mods add to it happens
     * (experience, quality); it throws the result out of the pan, which {@link CaughtDrops} can catch.
     */
    void handson$cookOneNow(net.minecraft.world.level.Level level);
}
