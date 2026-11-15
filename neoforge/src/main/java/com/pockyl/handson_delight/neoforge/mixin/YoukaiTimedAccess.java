package com.pockyl.handson_delight.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * How far a timed recipe of Youkai's Feasts has gone (its kettle, pots, fermentation tank share this base): the ticks done and the
 * ticks it takes. Reached by name: the class builds on the L2 libraries nested in that mod's jar.
 */
@Pseudo
@Mixin(targets = "dev.xkmc.youkaishomecoming.content.pot.base.TimedRecipeBlockEntity", remap = false)
public interface YoukaiTimedAccess {
    @Accessor("recipeProgress")
    int handson$recipeProgress();

    @Accessor("recipeProgress")
    void handson$setRecipeProgress(int ticks);

    @Accessor("totalTime")
    int handson$totalTime();
}
