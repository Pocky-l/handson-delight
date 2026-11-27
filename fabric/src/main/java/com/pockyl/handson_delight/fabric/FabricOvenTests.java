package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.gametest.CulturalOvenTests;
import com.pockyl.handson_delight.gametest.OvenTests;

/**
 * {@link CulturalOvenTests} and {@link OvenTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}, and pass
 * at once without Cultural Delights or Cooking for Blockheads ({@code -Paddons} puts them in).
 */
public final class FabricOvenTests {
    @GameTest(maxTicks = 100)
    public void everyLooseBakingFromItsFoodLaidOn(GameTestHelper helper) {
        CulturalOvenTests.everyLooseBakingFromItsFoodLaidOn(helper);
    }

    @GameTest(maxTicks = 100)
    public void theOvenReadsItsRecipes(GameTestHelper helper) {
        CulturalOvenTests.theOvenReadsItsRecipes(helper);
    }

    @GameTest(maxTicks = 100)
    public void theDoorKeepsTheTrayShut(GameTestHelper helper) {
        CulturalOvenTests.theDoorKeepsTheTrayShut(helper);
    }

    @GameTest(maxTicks = 400)
    public void breadBakesWithTheDoorShut(GameTestHelper helper) {
        CulturalOvenTests.breadBakesWithTheDoorShut(helper);
    }

    @GameTest(maxTicks = 100)
    public void aPieIsLaidOutFromTheBook(GameTestHelper helper) {
        CulturalOvenTests.aPieIsLaidOutFromTheBook(helper);
    }

    @GameTest(maxTicks = 100)
    public void aStackIsSharedOutForItsRecipe(GameTestHelper helper) {
        CulturalOvenTests.aStackIsSharedOutForItsRecipe(helper);
    }

    @GameTest(maxTicks = 100)
    public void doughIsSharedOutForBread(GameTestHelper helper) {
        CulturalOvenTests.doughIsSharedOutForBread(helper);
    }

    @GameTest(maxTicks = 400)
    public void sausagesAndMashLaidByHandBake(GameTestHelper helper) {
        CulturalOvenTests.sausagesAndMashLaidByHandBake(helper);
    }

    @GameTest(maxTicks = 600)
    public void twoSetsLaidByHandBakeBoth(GameTestHelper helper) {
        CulturalOvenTests.twoSetsLaidByHandBakeBoth(helper);
    }

    @GameTest(maxTicks = 600)
    public void twoSetsFromTheBookBakeBoth(GameTestHelper helper) {
        CulturalOvenTests.twoSetsFromTheBookBakeBoth(helper);
    }

    @GameTest(maxTicks = 400)
    public void aWaitingDishMakesWayForTheNext(GameTestHelper helper) {
        CulturalOvenTests.aWaitingDishMakesWayForTheNext(helper);
    }

    @GameTest(maxTicks = 400)
    public void aWaitingDishGoesWithTheCookWhoLeaves(GameTestHelper helper) {
        CulturalOvenTests.aWaitingDishGoesWithTheCookWhoLeaves(helper);
    }

    @GameTest(maxTicks = 100)
    public void everyLooseBakingLaidByHand(GameTestHelper helper) {
        CulturalOvenTests.everyLooseBakingLaidByHand(helper);
    }

    @GameTest(maxTicks = 100)
    public void berriesAreSharedOutForTheCheesecake(GameTestHelper helper) {
        CulturalOvenTests.berriesAreSharedOutForTheCheesecake(helper);
    }

    @GameTest(maxTicks = 100)
    public void sausagesAndMashInAnyOrder(GameTestHelper helper) {
        CulturalOvenTests.sausagesAndMashInAnyOrder(helper);
    }

    @GameTest(maxTicks = 100)
    public void theClientIsSentTheOvenRecipes(GameTestHelper helper) {
        CulturalOvenTests.theClientIsSentTheOvenRecipes(helper);
    }

    @GameTest(maxTicks = 100)
    public void theCfbOvenReadsItsRecipes(GameTestHelper helper) {
        OvenTests.theOvenReadsItsRecipes(helper);
    }

    @GameTest(maxTicks = 100)
    public void theCfbOvenDoorKeepsTheRackShut(GameTestHelper helper) {
        OvenTests.theDoorKeepsTheRackShut(helper);
    }

    @GameTest(maxTicks = 400)
    public void foodBakesOnTheCfbOvenRack(GameTestHelper helper) {
        OvenTests.foodBakesOnTheRack(helper);
    }

    @GameTest(maxTicks = 100)
    public void cfbOvenFuelComesFromTheInventory(GameTestHelper helper) {
        OvenTests.fuelComesFromTheInventory(helper);
    }

    @GameTest(maxTicks = 100)
    public void aPickInTheBookFillsTheCfbOvenRack(GameTestHelper helper) {
        OvenTests.aPickInTheBookFillsTheRack(helper);
    }

    @GameTest(maxTicks = 100)
    public void theCfbOvenDoorShutsBehindTheLastCook(GameTestHelper helper) {
        OvenTests.theDoorShutsBehindTheLastCook(helper);
    }

    @GameTest(maxTicks = 100)
    public void aSkilletStandsOnACfbOvenBurner(GameTestHelper helper) {
        OvenTests.aSkilletStandsOnABurner(helper);
    }

    @GameTest(maxTicks = 700)
    public void hoppersFeedAndEmptyTheCfbOven(GameTestHelper helper) {
        OvenTests.hoppersFeedAndEmptyTheOven(helper);
    }
}
