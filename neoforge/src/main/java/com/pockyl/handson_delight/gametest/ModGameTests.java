package com.pockyl.handson_delight.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.handson_delight.HandsOnDelight;

/** {@link PotTests}, {@link BoardTests}, {@link SkilletTests}, {@link StoveTests} and {@link RecipeAudit} on NeoForge. */
@GameTestHolder(HandsOnDelight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModGameTests {
    private ModGameTests() {
    }

    @GameTest(template = "empty")
    public static void slotsAreThePotsOwn(GameTestHelper helper) {
        PotTests.slotsAreThePotsOwn(helper);
    }

    @GameTest(template = "empty")
    public static void theMealStaysInThePot(GameTestHelper helper) {
        PotTests.theMealStaysInThePot(helper);
    }

    @GameTest(template = "empty")
    public static void aBowlClickedOnTheSoupFills(GameTestHelper helper) {
        PotTests.aBowlClickedOnTheSoupFills(helper);
    }

    @GameTest(template = "empty")
    public static void anEmptyHandScoopsWithBowlsFromTheInventory(GameTestHelper helper) {
        PotTests.anEmptyHandScoopsWithBowlsFromTheInventory(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anEmptyHandCutsWithTheKnifeInTheInventory(GameTestHelper helper) {
        BoardTests.anEmptyHandCutsWithTheKnifeInTheInventory(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anEmptyHandWithoutAKnifeCutsNothing(GameTestHelper helper) {
        BoardTests.anEmptyHandWithoutAKnifeCutsNothing(helper);
    }

    @GameTest(template = "empty")
    public static void aStackIsSharedOutForARecipeOfTwoAlike(GameTestHelper helper) {
        PotTests.aStackIsSharedOutForARecipeOfTwoAlike(helper);
    }

    @GameTest(template = "empty")
    public static void dishesStayOutOfTheWater(GameTestHelper helper) {
        PotTests.dishesStayOutOfTheWater(helper);
    }

    @GameTest(template = "empty")
    public static void shiftFillsTheWholeStack(GameTestHelper helper) {
        PotTests.shiftFillsTheWholeStack(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 340)
    public static void theWholeBatchCooksAtOnce(GameTestHelper helper) {
        PotTests.theWholeBatchCooksAtOnce(helper);
    }

    @GameTest(template = "empty")
    public static void theCooksClockJudgesThePace(GameTestHelper helper) {
        PotTests.theCooksClockJudgesThePace(helper);
    }

    @GameTest(template = "empty")
    public static void theBookFillsThePot(GameTestHelper helper) {
        PotTests.theBookFillsThePot(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aBonusIsCarriedOn(GameTestHelper helper) {
        PotTests.aBonusIsCarriedOn(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void theSetsSavedGoBack(GameTestHelper helper) {
        PotTests.theSetsSavedGoBack(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void theEmptiedContainerGoesToTheCook(GameTestHelper helper) {
        PotTests.theEmptiedContainerGoesToTheCook(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 360)
    public static void aPotLeftByItsCookCooksOn(GameTestHelper helper) {
        PotTests.aPotLeftByItsCookCooksOn(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void aCookWhoKnowsTheDishGetsMore(GameTestHelper helper) {
        PotTests.aCookWhoKnowsTheDishGetsMore(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void theFirstTimeHasOneStar(GameTestHelper helper) {
        PotTests.theFirstTimeHasOneStar(helper);
    }

    @GameTest(template = "empty")
    public static void onlyGoodStirringEarnsStars(GameTestHelper helper) {
        PotTests.onlyGoodStirringEarnsStars(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nothingCooksWithoutStirring(GameTestHelper helper) {
        PotTests.nothingCooksWithoutStirring(helper);
    }

    @GameTest(template = "empty")
    public static void theStationReadsHeat(GameTestHelper helper) {
        PotTests.theStationReadsHeat(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void theSmallestStackSetsTheBatch(GameTestHelper helper) {
        PotTests.theSmallestStackSetsTheBatch(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void aHopperFedPotCooksByItself(GameTestHelper helper) {
        PotTests.aHopperFedPotCooksByItself(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void oneSpoonOneHand(GameTestHelper helper) {
        PotTests.oneSpoonOneHand(helper);
    }

    @GameTest(template = "empty")
    public static void aServingTakenByHandCounts(GameTestHelper helper) {
        PotTests.aServingTakenByHandCounts(helper);
    }

    @GameTest(template = "empty")
    public static void aCarvedKnifeStaysFarmersDelights(GameTestHelper helper) {
        BoardTests.aCarvedKnifeStaysFarmersDelights(helper);
    }

    @GameTest(template = "empty")
    public static void anIgnoredChopIsAnswered(GameTestHelper helper) {
        BoardTests.anIgnoredChopIsAnswered(helper);
    }

    @GameTest(template = "empty")
    public static void anotherCookKeepsTheMarks(GameTestHelper helper) {
        BoardTests.anotherCookKeepsTheMarks(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void aModdedRecipeCooksAsABatch(GameTestHelper helper) {
        PotTests.aModdedRecipeCooksAsABatch(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void aDishWithoutAContainerComesOutWhole(GameTestHelper helper) {
        PotTests.aDishWithoutAContainerComesOutWhole(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void burgersLeaveTheCraftingTable(GameTestHelper helper) {
        BoardTests.burgersLeaveTheCraftingTable(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bowlDishesLeaveTheCraftingTable(GameTestHelper helper) {
        BoardTests.bowlDishesLeaveTheCraftingTable(helper);
    }

    @GameTest(template = "empty")
    public static void everyFoodCraftComesToTheBoard(GameTestHelper helper) {
        BoardTests.everyFoodCraftComesToTheBoard(helper);
    }

    @GameTest(template = "empty")
    public static void doughKeepsItsWater(GameTestHelper helper) {
        BoardTests.doughKeepsItsWater(helper);
    }

    @GameTest(template = "empty")
    public static void aPieGoesIntoItsCrust(GameTestHelper helper) {
        BoardTests.aPieGoesIntoItsCrust(helper);
    }

    @GameTest(template = "empty")
    public static void milkGivesItsBucketBack(GameTestHelper helper) {
        BoardTests.milkGivesItsBucketBack(helper);
    }

    @GameTest(template = "empty")
    public static void cookiesComeEightARound(GameTestHelper helper) {
        BoardTests.cookiesComeEightARound(helper);
    }

    @GameTest(template = "empty")
    public static void aFeastIsLaidFromTheBook(GameTestHelper helper) {
        BoardTests.aFeastIsLaidFromTheBook(helper);
    }

    @GameTest(template = "empty")
    public static void aSaladStartsFromItsBowl(GameTestHelper helper) {
        BoardTests.aSaladStartsFromItsBowl(helper);
    }

    @GameTest(template = "empty")
    public static void theBookLaysADishFromTheInventory(GameTestHelper helper) {
        BoardTests.theBookLaysADishFromTheInventory(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aBurgerIsBuiltFromTheBreadUp(GameTestHelper helper) {
        BoardTests.aBurgerIsBuiltFromTheBreadUp(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theTopLayerComesBack(GameTestHelper helper) {
        BoardTests.theTopLayerComesBack(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aHamTakesTwoChopsAndFeedsTheCook(GameTestHelper helper) {
        BoardTests.aHamTakesTwoChopsAndFeedsTheCook(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void noKnifeNoCut(GameTestHelper helper) {
        BoardTests.noKnifeNoCut(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sixteenAreCutAtOnce(GameTestHelper helper) {
        BoardTests.sixteenAreCutAtOnce(helper);
    }

    @GameTest(template = "empty")
    public static void shiftClickLaysTheStackFromTheInventory(GameTestHelper helper) {
        BoardTests.shiftClickLaysTheStackFromTheInventory(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void marksAreCutInOrderAtNewPlaces(GameTestHelper helper) {
        BoardTests.marksAreCutInOrderAtNewPlaces(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void marksFollowWhatTheFoodFallsInto(GameTestHelper helper) {
        BoardTests.marksFollowWhatTheFoodFallsInto(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void layersStayOnTheBoard(GameTestHelper helper) {
        BoardTests.layersStayOnTheBoard(helper);
    }

    @GameTest(template = "empty")
    public static void woodStaysWithFarmersDelight(GameTestHelper helper) {
        BoardTests.woodStaysWithFarmersDelight(helper);
    }

    @GameTest(template = "empty")
    public static void sixteenBurgersAtOnce(GameTestHelper helper) {
        BoardTests.sixteenBurgersAtOnce(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void aBatchFriesInHalvesAndWaitsToBeTurned(GameTestHelper helper) {
        SkilletTests.aBatchFriesInHalvesAndWaitsToBeTurned(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void farmersDelightsRightClickCooksItsWay(GameTestHelper helper) {
        SkilletTests.farmersDelightsRightClickCooksItsWay(helper);
    }

    @GameTest(template = "empty")
    public static void theFoodComesBackAndOnlyFoodGoesIn(GameTestHelper helper) {
        SkilletTests.theFoodComesBackAndOnlyFoodGoesIn(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void moreFoodTakesLongerButKeepsWhatWasDone(GameTestHelper helper) {
        SkilletTests.moreFoodTakesLongerButKeepsWhatWasDone(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 460)
    public static void aPieceGrillsInHalvesAndWaitsToBeTurned(GameTestHelper helper) {
        StoveTests.aPieceGrillsInHalvesAndWaitsToBeTurned(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void farmersDelightsWayStaysItsOwn(GameTestHelper helper) {
        StoveTests.farmersDelightsWayStaysItsOwn(helper);
    }

    @GameTest(template = "empty")
    public static void onlyFoodGoesOnAnOpenGrill(GameTestHelper helper) {
        StoveTests.onlyFoodGoesOnAnOpenGrill(helper);
    }

    @GameTest(template = "empty")
    public static void anEggStaysOffTheGrill(GameTestHelper helper) {
        StoveTests.anEggStaysOffTheGrill(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void anEggFriesWithoutATurn(GameTestHelper helper) {
        SkilletTests.anEggFriesWithoutATurn(helper);
    }

    @GameTest(template = "empty")
    public static void everyStoveOfTheTagIsAStation(GameTestHelper helper) {
        StoveTests.everyStoveOfTheTagIsAStation(helper);
    }

    @GameTest(template = "empty")
    public static void everyRecipeReachesItsStation(GameTestHelper helper) {
        RecipeAudit.everyRecipeReachesItsStation(helper);
    }
}
