package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.gametest.OvenTests;

/**
 * {@link OvenTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}, and pass at once without Cultural
 * Delights ({@code -Paddons} puts it in).
 */
public final class FabricOvenTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyLooseBakingFromItsFoodLaidOn(GameTestHelper helper) {
        OvenTests.everyLooseBakingFromItsFoodLaidOn(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theOvenReadsItsRecipes(GameTestHelper helper) {
        OvenTests.theOvenReadsItsRecipes(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theDoorKeepsTheTrayShut(GameTestHelper helper) {
        OvenTests.theDoorKeepsTheTrayShut(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void breadBakesWithTheDoorShut(GameTestHelper helper) {
        OvenTests.breadBakesWithTheDoorShut(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aPieIsLaidOutFromTheBook(GameTestHelper helper) {
        OvenTests.aPieIsLaidOutFromTheBook(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aStackIsSharedOutForItsRecipe(GameTestHelper helper) {
        OvenTests.aStackIsSharedOutForItsRecipe(helper);
    }
}
