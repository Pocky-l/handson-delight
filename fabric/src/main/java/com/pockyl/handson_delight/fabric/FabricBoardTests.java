package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.gametest.BoardTests;
import com.pockyl.handson_delight.gametest.FullInventoryTests;
import com.pockyl.handson_delight.gametest.RecipeAudit;
import com.pockyl.handson_delight.gametest.TestPlayers;

/**
 * {@link BoardTests} and the board's {@link FullInventoryTests} on Fabric: they run when the game starts with
 * {@code -Dfabric-api.gametest}.
 */
public final class FabricBoardTests {
    static {
        TestPlayers.useFakePlayers(FakePlayer::get);
    }

    @GameTest(maxTicks = 100)
    public void burgersLeaveTheCraftingTable(GameTestHelper helper) {
        BoardTests.burgersLeaveTheCraftingTable(helper);
    }

    @GameTest(maxTicks = 100)
    public void bowlDishesLeaveTheCraftingTable(GameTestHelper helper) {
        BoardTests.bowlDishesLeaveTheCraftingTable(helper);
    }

    @GameTest(maxTicks = 100)
    public void aKnifeThatBreaksOnTheLastCutLeavesThePieces(GameTestHelper helper) {
        FullInventoryTests.aKnifeThatBreaksOnTheLastCutLeavesThePieces(helper);
    }

    @GameTest(maxTicks = 100)
    public void aTaggedCraftOfAnyModComesToTheBoard(GameTestHelper helper) {
        BoardTests.aTaggedCraftOfAnyModComesToTheBoard(helper);
    }

    @GameTest(maxTicks = 100)
    public void aPouredWaterBottleLeavesItsBottle(GameTestHelper helper) {
        BoardTests.aPouredWaterBottleLeavesItsBottle(helper);
    }

    @GameTest(maxTicks = 100)
    public void everyFoodCraftComesToTheBoard(GameTestHelper helper) {
        BoardTests.everyFoodCraftComesToTheBoard(helper);
    }

    @GameTest(maxTicks = 100)
    public void everyRecipeReachesItsStation(GameTestHelper helper) {
        RecipeAudit.everyRecipeReachesItsStation(helper);
    }

    @GameTest(maxTicks = 100)
    public void doughKeepsItsWater(GameTestHelper helper) {
        BoardTests.doughKeepsItsWater(helper);
    }

    @GameTest(maxTicks = 100)
    public void aPieGoesIntoItsCrust(GameTestHelper helper) {
        BoardTests.aPieGoesIntoItsCrust(helper);
    }

    @GameTest(maxTicks = 100)
    public void milkGivesItsBucketBack(GameTestHelper helper) {
        BoardTests.milkGivesItsBucketBack(helper);
    }

    @GameTest(maxTicks = 100)
    public void cookiesComeEightARound(GameTestHelper helper) {
        BoardTests.cookiesComeEightARound(helper);
    }

    @GameTest(maxTicks = 100)
    public void aFeastIsLaidFromTheBook(GameTestHelper helper) {
        BoardTests.aFeastIsLaidFromTheBook(helper);
    }

    @GameTest(maxTicks = 100)
    public void aSaladStartsFromItsBowl(GameTestHelper helper) {
        BoardTests.aSaladStartsFromItsBowl(helper);
    }

    @GameTest(maxTicks = 100)
    public void theBookLaysADishFromTheInventory(GameTestHelper helper) {
        BoardTests.theBookLaysADishFromTheInventory(helper);
    }

    @GameTest(maxTicks = 100)
    public void aBurgerIsBuiltFromTheBreadUp(GameTestHelper helper) {
        BoardTests.aBurgerIsBuiltFromTheBreadUp(helper);
    }

    @GameTest(maxTicks = 100)
    public void theTopLayerComesBack(GameTestHelper helper) {
        BoardTests.theTopLayerComesBack(helper);
    }

    @GameTest(maxTicks = 100)
    public void everyCuttableFoodLiesDownToBeCut(GameTestHelper helper) {
        BoardTests.everyCuttableFoodLiesDownToBeCut(helper);
    }

    @GameTest(maxTicks = 100)
    public void cutFoodBecomesItsDishWhenTheNextLayerComes(GameTestHelper helper) {
        BoardTests.cutFoodBecomesItsDishWhenTheNextLayerComes(helper);
    }

    @GameTest(maxTicks = 100)
    public void aKnifeIsNoLayer(GameTestHelper helper) {
        BoardTests.aKnifeIsNoLayer(helper);
    }

    @GameTest(maxTicks = 100)
    public void everyDishIsLaidByHand(GameTestHelper helper) {
        BoardTests.everyDishIsLaidByHand(helper);
    }

    @GameTest(maxTicks = 100)
    public void anOrderTheBookDoesNotUseMakesTheDish(GameTestHelper helper) {
        BoardTests.anOrderTheBookDoesNotUseMakesTheDish(helper);
    }

    @GameTest(maxTicks = 100)
    public void aToolThatIsALayerCutsOnlyWhatItCuts(GameTestHelper helper) {
        BoardTests.aToolThatIsALayerCutsOnlyWhatItCuts(helper);
    }

    @GameTest(maxTicks = 100)
    public void aPickedDishWinsOverAnotherOfTheSameLayers(GameTestHelper helper) {
        BoardTests.aPickedDishWinsOverAnotherOfTheSameLayers(helper);
    }

    @GameTest(maxTicks = 100)
    public void aHamTakesTwoChopsAndFeedsTheCook(GameTestHelper helper) {
        BoardTests.aHamTakesTwoChopsAndFeedsTheCook(helper);
    }

    @GameTest(maxTicks = 100)
    public void rawChickenFallsIntoItsCuts(GameTestHelper helper) {
        BoardTests.rawChickenFallsIntoItsCuts(helper);
    }

    @GameTest(maxTicks = 100)
    public void aClientPlansTheMarksItsServerPlans(GameTestHelper helper) {
        BoardTests.aClientPlansTheMarksItsServerPlans(helper);
    }

    @GameTest(maxTicks = 100)
    public void noKnifeNoCut(GameTestHelper helper) {
        BoardTests.noKnifeNoCut(helper);
    }

    @GameTest(maxTicks = 100)
    public void anEmptyHandCutsWithTheKnifeInTheInventory(GameTestHelper helper) {
        BoardTests.anEmptyHandCutsWithTheKnifeInTheInventory(helper);
    }

    @GameTest(maxTicks = 100)
    public void anEmptyHandWithoutAKnifeCutsNothing(GameTestHelper helper) {
        BoardTests.anEmptyHandWithoutAKnifeCutsNothing(helper);
    }

    @GameTest(maxTicks = 100)
    public void sixteenAreCutAtOnce(GameTestHelper helper) {
        BoardTests.sixteenAreCutAtOnce(helper);
    }

    @GameTest(maxTicks = 100)
    public void layersStayOnTheBoard(GameTestHelper helper) {
        BoardTests.layersStayOnTheBoard(helper);
    }

    @GameTest(maxTicks = 100)
    public void everyCutIsTheStations(GameTestHelper helper) {
        BoardTests.everyCutIsTheStations(helper);
    }

    @GameTest(maxTicks = 100)
    public void whatThePotServesOnIsCutAtTheStation(GameTestHelper helper) {
        BoardTests.whatThePotServesOnIsCutAtTheStation(helper);
    }

    @GameTest(maxTicks = 100)
    public void sixteenBurgersAtOnce(GameTestHelper helper) {
        BoardTests.sixteenBurgersAtOnce(helper);
    }

    @GameTest(maxTicks = 100)
    public void shiftClickLaysTheStackFromTheInventory(GameTestHelper helper) {
        BoardTests.shiftClickLaysTheStackFromTheInventory(helper);
    }

    @GameTest(maxTicks = 100)
    public void marksAreCutInOrderAtNewPlaces(GameTestHelper helper) {
        BoardTests.marksAreCutInOrderAtNewPlaces(helper);
    }

    @GameTest(maxTicks = 100)
    public void marksFollowWhatTheFoodFallsInto(GameTestHelper helper) {
        BoardTests.marksFollowWhatTheFoodFallsInto(helper);
    }

    @GameTest(maxTicks = 100)
    public void aToolStuckInTheBoardOpensTheStation(GameTestHelper helper) {
        BoardTests.aToolStuckInTheBoardOpensTheStation(helper);
    }

    @GameTest(maxTicks = 100)
    public void aToolIsStuckIntoTheBoardFromTheStation(GameTestHelper helper) {
        BoardTests.aToolIsStuckIntoTheBoardFromTheStation(helper);
    }

    @GameTest(maxTicks = 100)
    public void foodMovesAFinishedDishAside(GameTestHelper helper) {
        BoardTests.foodMovesAFinishedDishAside(helper);
    }

    @GameTest(maxTicks = 100)
    public void whatTheHandHeldStaysWhenTheBoardCannotTakeIt(GameTestHelper helper) {
        BoardTests.whatTheHandHeldStaysWhenTheBoardCannotTakeIt(helper);
    }

    @GameTest(maxTicks = 100)
    public void aKnifeInTheOffHandCuts(GameTestHelper helper) {
        BoardTests.aKnifeInTheOffHandCuts(helper);
    }

    @GameTest(maxTicks = 100)
    public void takingACarvedKnifeLeavesTheBoardBare(GameTestHelper helper) {
        BoardTests.takingACarvedKnifeLeavesTheBoardBare(helper);
    }

    @GameTest(maxTicks = 100)
    public void aBookPickLeavesTheFoodUntilTheRestIsThere(GameTestHelper helper) {
        BoardTests.aBookPickLeavesTheFoodUntilTheRestIsThere(helper);
    }

    @GameTest(maxTicks = 100)
    public void aStackItsRecipeRefusesComesBackOff(GameTestHelper helper) {
        BoardTests.aStackItsRecipeRefusesComesBackOff(helper);
    }

    @GameTest(maxTicks = 100)
    public void anIgnoredChopIsAnswered(GameTestHelper helper) {
        BoardTests.anIgnoredChopIsAnswered(helper);
    }

    @GameTest(maxTicks = 100)
    public void anotherCookKeepsTheMarks(GameTestHelper helper) {
        BoardTests.anotherCookKeepsTheMarks(helper);
    }

    @GameTest(maxTicks = 100)
    public void choppersDelightsBoardsAreStations(GameTestHelper helper) {
        BoardTests.choppersDelightsBoardsAreStations(helper);
    }
}
