package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.gametest.CulturalOvenTests;

/**
 * {@link CulturalOvenTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}, and pass at once without Cultural
 * Delights ({@code -Paddons} puts it in).
 */
public final class FabricOvenTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyLooseBakingFromItsFoodLaidOn(GameTestHelper helper) {
        CulturalOvenTests.everyLooseBakingFromItsFoodLaidOn(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theOvenReadsItsRecipes(GameTestHelper helper) {
        CulturalOvenTests.theOvenReadsItsRecipes(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theDoorKeepsTheTrayShut(GameTestHelper helper) {
        CulturalOvenTests.theDoorKeepsTheTrayShut(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void breadBakesWithTheDoorShut(GameTestHelper helper) {
        CulturalOvenTests.breadBakesWithTheDoorShut(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aPieIsLaidOutFromTheBook(GameTestHelper helper) {
        CulturalOvenTests.aPieIsLaidOutFromTheBook(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aStackIsSharedOutForItsRecipe(GameTestHelper helper) {
        CulturalOvenTests.aStackIsSharedOutForItsRecipe(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void sausagesAndMashLaidByHandBake(GameTestHelper helper) {
        CulturalOvenTests.sausagesAndMashLaidByHandBake(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 600)
    public void twoSetsLaidByHandBakeBoth(GameTestHelper helper) {
        CulturalOvenTests.twoSetsLaidByHandBakeBoth(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 600)
    public void twoSetsFromTheBookBakeBoth(GameTestHelper helper) {
        CulturalOvenTests.twoSetsFromTheBookBakeBoth(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void aWaitingDishMakesWayForTheNext(GameTestHelper helper) {
        CulturalOvenTests.aWaitingDishMakesWayForTheNext(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void aWaitingDishGoesWithTheCookWhoLeaves(GameTestHelper helper) {
        CulturalOvenTests.aWaitingDishGoesWithTheCookWhoLeaves(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void everyLooseBakingLaidByHand(GameTestHelper helper) {
        CulturalOvenTests.everyLooseBakingLaidByHand(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void berriesAreSharedOutForTheCheesecake(GameTestHelper helper) {
        CulturalOvenTests.berriesAreSharedOutForTheCheesecake(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void sausagesAndMashInAnyOrder(GameTestHelper helper) {
        CulturalOvenTests.sausagesAndMashInAnyOrder(helper);
    }
}
