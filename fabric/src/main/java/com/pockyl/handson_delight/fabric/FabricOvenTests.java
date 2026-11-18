package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.gametest.OvenTests;

/**
 * {@link OvenTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}, and pass at once without Cultural
 * Delights ({@code -Paddons} puts it in).
 */
public final class FabricOvenTests {
    @GameTest(maxTicks = 100)
    public void everyLooseBakingFromItsFoodLaidOn(GameTestHelper helper) {
        OvenTests.everyLooseBakingFromItsFoodLaidOn(helper);
    }

    @GameTest(maxTicks = 100)
    public void theOvenReadsItsRecipes(GameTestHelper helper) {
        OvenTests.theOvenReadsItsRecipes(helper);
    }

    @GameTest(maxTicks = 100)
    public void theDoorKeepsTheTrayShut(GameTestHelper helper) {
        OvenTests.theDoorKeepsTheTrayShut(helper);
    }

    @GameTest(maxTicks = 400)
    public void breadBakesWithTheDoorShut(GameTestHelper helper) {
        OvenTests.breadBakesWithTheDoorShut(helper);
    }

    @GameTest(maxTicks = 100)
    public void aPieIsLaidOutFromTheBook(GameTestHelper helper) {
        OvenTests.aPieIsLaidOutFromTheBook(helper);
    }

    @GameTest(maxTicks = 100)
    public void aStackIsSharedOutForItsRecipe(GameTestHelper helper) {
        OvenTests.aStackIsSharedOutForItsRecipe(helper);
    }
}
