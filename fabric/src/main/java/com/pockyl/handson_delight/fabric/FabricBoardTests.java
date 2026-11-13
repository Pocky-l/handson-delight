package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.gametest.BoardTests;
import com.pockyl.handson_delight.gametest.RecipeAudit;

/** {@link BoardTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}. */
public final class FabricBoardTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void burgersLeaveTheCraftingTable(GameTestHelper helper) {
        BoardTests.burgersLeaveTheCraftingTable(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bowlDishesLeaveTheCraftingTable(GameTestHelper helper) {
        BoardTests.bowlDishesLeaveTheCraftingTable(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aTaggedCraftOfAnyModComesToTheBoard(GameTestHelper helper) {
        BoardTests.aTaggedCraftOfAnyModComesToTheBoard(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyFoodCraftComesToTheBoard(GameTestHelper helper) {
        BoardTests.everyFoodCraftComesToTheBoard(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyRecipeReachesItsStation(GameTestHelper helper) {
        RecipeAudit.everyRecipeReachesItsStation(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void doughKeepsItsWater(GameTestHelper helper) {
        BoardTests.doughKeepsItsWater(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aPieGoesIntoItsCrust(GameTestHelper helper) {
        BoardTests.aPieGoesIntoItsCrust(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void milkGivesItsBucketBack(GameTestHelper helper) {
        BoardTests.milkGivesItsBucketBack(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void cookiesComeEightARound(GameTestHelper helper) {
        BoardTests.cookiesComeEightARound(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aFeastIsLaidFromTheBook(GameTestHelper helper) {
        BoardTests.aFeastIsLaidFromTheBook(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aSaladStartsFromItsBowl(GameTestHelper helper) {
        BoardTests.aSaladStartsFromItsBowl(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theBookLaysADishFromTheInventory(GameTestHelper helper) {
        BoardTests.theBookLaysADishFromTheInventory(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aBurgerIsBuiltFromTheBreadUp(GameTestHelper helper) {
        BoardTests.aBurgerIsBuiltFromTheBreadUp(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theTopLayerComesBack(GameTestHelper helper) {
        BoardTests.theTopLayerComesBack(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyCuttableFoodLiesDownToBeCut(GameTestHelper helper) {
        BoardTests.everyCuttableFoodLiesDownToBeCut(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void cutFoodBecomesItsDishWhenTheNextLayerComes(GameTestHelper helper) {
        BoardTests.cutFoodBecomesItsDishWhenTheNextLayerComes(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aKnifeIsNoLayer(GameTestHelper helper) {
        BoardTests.aKnifeIsNoLayer(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void aHamTakesTwoChopsAndFeedsTheCook(GameTestHelper helper) {
        BoardTests.aHamTakesTwoChopsAndFeedsTheCook(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void noKnifeNoCut(GameTestHelper helper) {
        BoardTests.noKnifeNoCut(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void anEmptyHandCutsWithTheKnifeInTheInventory(GameTestHelper helper) {
        BoardTests.anEmptyHandCutsWithTheKnifeInTheInventory(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void anEmptyHandWithoutAKnifeCutsNothing(GameTestHelper helper) {
        BoardTests.anEmptyHandWithoutAKnifeCutsNothing(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void sixteenAreCutAtOnce(GameTestHelper helper) {
        BoardTests.sixteenAreCutAtOnce(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void layersStayOnTheBoard(GameTestHelper helper) {
        BoardTests.layersStayOnTheBoard(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void woodStaysWithFarmersDelight(GameTestHelper helper) {
        BoardTests.woodStaysWithFarmersDelight(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void whatThePotServesOnIsCutAtTheStation(GameTestHelper helper) {
        BoardTests.whatThePotServesOnIsCutAtTheStation(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void sixteenBurgersAtOnce(GameTestHelper helper) {
        BoardTests.sixteenBurgersAtOnce(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void shiftClickLaysTheStackFromTheInventory(GameTestHelper helper) {
        BoardTests.shiftClickLaysTheStackFromTheInventory(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void marksAreCutInOrderAtNewPlaces(GameTestHelper helper) {
        BoardTests.marksAreCutInOrderAtNewPlaces(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void marksFollowWhatTheFoodFallsInto(GameTestHelper helper) {
        BoardTests.marksFollowWhatTheFoodFallsInto(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aToolStuckInTheBoardOpensTheStation(GameTestHelper helper) {
        BoardTests.aToolStuckInTheBoardOpensTheStation(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aToolIsStuckIntoTheBoardFromTheStation(GameTestHelper helper) {
        BoardTests.aToolIsStuckIntoTheBoardFromTheStation(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void foodMovesAFinishedDishAside(GameTestHelper helper) {
        BoardTests.foodMovesAFinishedDishAside(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void whatTheHandHeldStaysWhenTheBoardCannotTakeIt(GameTestHelper helper) {
        BoardTests.whatTheHandHeldStaysWhenTheBoardCannotTakeIt(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void aKnifeInTheOffHandCuts(GameTestHelper helper) {
        BoardTests.aKnifeInTheOffHandCuts(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void takingACarvedKnifeLeavesTheBoardBare(GameTestHelper helper) {
        BoardTests.takingACarvedKnifeLeavesTheBoardBare(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aBookPickLeavesTheFoodUntilTheRestIsThere(GameTestHelper helper) {
        BoardTests.aBookPickLeavesTheFoodUntilTheRestIsThere(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aStackItsRecipeRefusesComesBackOff(GameTestHelper helper) {
        BoardTests.aStackItsRecipeRefusesComesBackOff(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anIgnoredChopIsAnswered(GameTestHelper helper) {
        BoardTests.anIgnoredChopIsAnswered(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anotherCookKeepsTheMarks(GameTestHelper helper) {
        BoardTests.anotherCookKeepsTheMarks(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void choppersDelightsBoardsAreStations(GameTestHelper helper) {
        BoardTests.choppersDelightsBoardsAreStations(helper);
    }
}
