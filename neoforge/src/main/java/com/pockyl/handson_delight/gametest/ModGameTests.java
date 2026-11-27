package com.pockyl.handson_delight.gametest;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import vectorwing.farmersdelight.client.recipebook.CookingPotRecipeBookTab;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.handson_delight.HandsOnDelight;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link PotTests}, {@link CopperPotTests}, {@link MonsterPotTests}, {@link FieryPotTests}, {@link AlchemistPotTests}, {@link KegTests}, {@link HearthKegTests}, {@link HearthCaskTests}, {@link CulturalOvenTests}, {@link OvenTests}, {@link VatTests}, {@link KettleTests}, {@link YoukaiKettleTests}, {@link YoukaiBoardTests}, {@link YoukaiPotTests}, {@link YoukaiSteamerTests}, {@link YoukaiTankTests}, {@link StumpTests}, {@link BoardTests}, {@link SkilletTests}, {@link StoveTests}
 * and {@link RecipeAudit} on NeoForge.
 */
@GameTestHolder(HandsOnDelight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModGameTests {
    static {
        TestPlayers.useFakePlayers(FakePlayerFactory::get);
    }

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
    public static void milkAskedForAsFoodIsMilk(GameTestHelper helper) {
        PotTests.milkAskedForAsFoodIsMilk(helper);
    }

    @GameTest(template = "empty")
    public static void twoAlikeThrownInMakeTheirDish(GameTestHelper helper) {
        PotTests.twoAlikeThrownInMakeTheirDish(helper);
    }

    @GameTest(template = "empty")
    public static void aFullPotTakesWhatItsDishStillReads(GameTestHelper helper) {
        PotTests.aFullPotTakesWhatItsDishStillReads(helper);
    }

    @GameTest(template = "empty")
    public static void theFullerDishOnceItsLastIngredientIsIn(GameTestHelper helper) {
        PotTests.theFullerDishOnceItsLastIngredientIsIn(helper);
    }

    @GameTest(template = "empty")
    public static void aTagTakesWhatTheOtherIngredientsLeave(GameTestHelper helper) {
        PotTests.aTagTakesWhatTheOtherIngredientsLeave(helper);
    }

    @GameTest(template = "empty")
    public static void theBookKeepsThePickedDish(GameTestHelper helper) {
        PotTests.theBookKeepsThePickedDish(helper);
    }

    @GameTest(template = "empty")
    public static void aDishNobodyCanMakeIsNotOffered(GameTestHelper helper) {
        PotTests.aDishNobodyCanMakeIsNotOffered(helper);
    }

    @GameTest(template = "empty")
    public static void everyDishFromItsFoodThrownIn(GameTestHelper helper) {
        PotTests.everyDishFromItsFoodThrownIn(helper);
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
    public static void aFloodOfStrokesFillsNoFaster(GameTestHelper helper) {
        PotTests.aFloodOfStrokesFillsNoFaster(helper);
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

    @GameTest(template = "empty", timeoutTicks = 450)
    public static void theSetsSavedStayWhenTheCookLeft(GameTestHelper helper) {
        PotTests.theSetsSavedStayWhenTheCookLeft(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void theEmptiedContainerGoesToTheCook(GameTestHelper helper) {
        PotTests.theEmptiedContainerGoesToTheCook(helper);
    }

    @GameTest(template = "empty")
    public static void whatWentInWithoutFloatingComesBackOut(GameTestHelper helper) {
        PotTests.whatWentInWithoutFloatingComesBackOut(helper);
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

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void aHopperFeedsThePotByItselfBesideACook(GameTestHelper helper) {
        PotTests.aHopperFeedsThePotByItselfBesideACook(helper);
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
    public static void aToolStuckInTheBoardOpensTheStation(GameTestHelper helper) {
        BoardTests.aToolStuckInTheBoardOpensTheStation(helper);
    }

    @GameTest(template = "empty")
    public static void aToolIsStuckIntoTheBoardFromTheStation(GameTestHelper helper) {
        BoardTests.aToolIsStuckIntoTheBoardFromTheStation(helper);
    }

    @GameTest(template = "empty")
    public static void foodMovesAFinishedDishAside(GameTestHelper helper) {
        BoardTests.foodMovesAFinishedDishAside(helper);
    }

    @GameTest(template = "empty")
    public static void whatTheHandHeldStaysWhenTheBoardCannotTakeIt(GameTestHelper helper) {
        BoardTests.whatTheHandHeldStaysWhenTheBoardCannotTakeIt(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aKnifeInTheOffHandCuts(GameTestHelper helper) {
        BoardTests.aKnifeInTheOffHandCuts(helper);
    }

    @GameTest(template = "empty")
    public static void takingACarvedKnifeLeavesTheBoardBare(GameTestHelper helper) {
        BoardTests.takingACarvedKnifeLeavesTheBoardBare(helper);
    }

    @GameTest(template = "empty")
    public static void aBookPickLeavesTheFoodUntilTheRestIsThere(GameTestHelper helper) {
        BoardTests.aBookPickLeavesTheFoodUntilTheRestIsThere(helper);
    }

    @GameTest(template = "empty")
    public static void aStackItsRecipeRefusesComesBackOff(GameTestHelper helper) {
        BoardTests.aStackItsRecipeRefusesComesBackOff(helper);
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

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void riceAddedWhileCookingKeepsTheProgress(GameTestHelper helper) {
        PotTests.riceAddedWhileCookingKeepsTheProgress(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 320)
    public static void moreRiceThanAStackStillCooks(GameTestHelper helper) {
        PotTests.moreRiceThanAStackStillCooks(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void aMealOfWhatLiesThereCooksAsABatch(GameTestHelper helper) {
        PotTests.aMealOfWhatLiesThereCooksAsABatch(helper, MealOfWhatLiesThere::new);
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
    public static void aTaggedCraftOfAnyModComesToTheBoard(GameTestHelper helper) {
        BoardTests.aTaggedCraftOfAnyModComesToTheBoard(helper);
    }

    @GameTest(template = "empty")
    public static void aPouredWaterBottleLeavesItsBottle(GameTestHelper helper) {
        BoardTests.aPouredWaterBottleLeavesItsBottle(helper);
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
    public static void aDishsIngredientsMakeAnotherRecipeInTheGrid(GameTestHelper helper) {
        BoardTests.aDishsIngredientsMakeAnotherRecipeInTheGrid(helper);
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
    public static void everyCuttableFoodLiesDownToBeCut(GameTestHelper helper) {
        BoardTests.everyCuttableFoodLiesDownToBeCut(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cutFoodBecomesItsDishWhenTheNextLayerComes(GameTestHelper helper) {
        BoardTests.cutFoodBecomesItsDishWhenTheNextLayerComes(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aKnifeIsNoLayer(GameTestHelper helper) {
        BoardTests.aKnifeIsNoLayer(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void everyDishIsLaidByHand(GameTestHelper helper) {
        BoardTests.everyDishIsLaidByHand(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anOrderTheBookDoesNotUseMakesTheDish(GameTestHelper helper) {
        BoardTests.anOrderTheBookDoesNotUseMakesTheDish(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aToolThatIsALayerCutsOnlyWhatItCuts(GameTestHelper helper) {
        BoardTests.aToolThatIsALayerCutsOnlyWhatItCuts(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aPickedDishWinsOverAnotherOfTheSameLayers(GameTestHelper helper) {
        BoardTests.aPickedDishWinsOverAnotherOfTheSameLayers(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aHamTakesTwoChopsAndFeedsTheCook(GameTestHelper helper) {
        BoardTests.aHamTakesTwoChopsAndFeedsTheCook(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rawChickenFallsIntoItsCuts(GameTestHelper helper) {
        BoardTests.rawChickenFallsIntoItsCuts(helper);
    }

    @GameTest(template = "empty")
    public static void aClientPlansTheMarksItsServerPlans(GameTestHelper helper) {
        BoardTests.aClientPlansTheMarksItsServerPlans(helper);
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

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void everyCutIsTheStations(GameTestHelper helper) {
        BoardTests.everyCutIsTheStations(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void breadAnAddonCutsIsSliced(GameTestHelper helper) {
        BoardTests.breadAnAddonCutsIsSliced(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void whatThePotServesOnIsCutAtTheStation(GameTestHelper helper) {
        BoardTests.whatThePotServesOnIsCutAtTheStation(helper);
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

    @GameTest(template = "empty", timeoutTicks = 160, batch = "skilletOff")
    public static void aStationTurnedOffHandsThePanToFarmersDelight(GameTestHelper helper) {
        SkilletTests.aStationTurnedOffHandsThePanToFarmersDelight(helper);
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

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void piecesLaidByHandGoFarmersDelightsWay(GameTestHelper helper) {
        StoveTests.piecesLaidByHandGoFarmersDelightsWay(helper);
    }

    @GameTest(template = "empty")
    public static void onlyFoodGoesOnAnOpenGrill(GameTestHelper helper) {
        StoveTests.onlyFoodGoesOnAnOpenGrill(helper);
    }

    @GameTest(template = "empty")
    public static void everyCampfireFoodGoesOnTheGrill(GameTestHelper helper) {
        StoveTests.everyCampfireFoodGoesOnTheGrill(helper);
    }

    @GameTest(template = "empty")
    public static void everyCampfireFoodGoesInThePan(GameTestHelper helper) {
        SkilletTests.everyCampfireFoodGoesInThePan(helper);
    }

    @GameTest(template = "empty")
    public static void anEggStaysOffTheGrill(GameTestHelper helper) {
        StoveTests.anEggStaysOffTheGrill(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void anEggFriesWithoutATurn(GameTestHelper helper) {
        SkilletTests.anEggFriesWithoutATurn(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void friedFoodFriesNoFurther(GameTestHelper helper) {
        SkilletTests.friedFoodFriesNoFurther(helper);
    }

    @GameTest(template = "empty")
    public static void everyStoveOfTheTagIsAStation(GameTestHelper helper) {
        StoveTests.everyStoveOfTheTagIsAStation(helper);
    }

    @GameTest(template = "empty")
    public static void theMazeStoveGrillsTwilightFoodTwiceAsFast(GameTestHelper helper) {
        StoveTests.theMazeStoveGrillsTwilightFoodTwiceAsFast(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void choppersDelightsBoardsAreStations(GameTestHelper helper) {
        BoardTests.choppersDelightsBoardsAreStations(helper);
    }

    @GameTest(template = "empty")
    public static void kebabsKeepTheSeasoningOfTheirSkewers(GameTestHelper helper) {
        BoardTests.kebabsKeepTheSeasoningOfTheirSkewers(helper);
    }

    @GameTest(template = "empty")
    public static void theCookingTimeFollowsTheConfig(GameTestHelper helper) {
        PotTests.theCookingTimeFollowsTheConfig(helper);
    }

    @GameTest(template = "empty")
    public static void theFryingTimeFollowsTheConfig(GameTestHelper helper) {
        SkilletTests.theFryingTimeFollowsTheConfig(helper);
    }

    @GameTest(template = "empty")
    public static void theGrillingTimeFollowsTheConfig(GameTestHelper helper) {
        StoveTests.theGrillingTimeFollowsTheConfig(helper);
    }

    @GameTest(template = "empty")
    public static void kitchenThingsOpenTheStove(GameTestHelper helper) {
        StoveTests.kitchenThingsOpenTheStove(helper);
    }

    @GameTest(template = "empty")
    public static void everyRecipeReachesItsStation(GameTestHelper helper) {
        RecipeAudit.everyRecipeReachesItsStation(helper);
    }

    @GameTest(template = "empty")
    public static void everyPotSceneHasEveryPart(GameTestHelper helper) {
        PotSceneTests.everyPotSceneHasEveryPart(helper);
    }

    @GameTest(template = "empty")
    public static void theCopperPotHoldsFourIngredients(GameTestHelper helper) {
        CopperPotTests.theCopperPotHoldsFourIngredients(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aStirredCopperPotCooksTheBatchInCups(GameTestHelper helper) {
        CopperPotTests.aStirredCopperPotCooksTheBatchInCups(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aHopperFedCopperPotCooksByItself(GameTestHelper helper) {
        CopperPotTests.aHopperFedCopperPotCooksByItself(helper);
    }

    @GameTest(template = "empty")
    public static void aStandingKegIsAStationOfItsOwn(GameTestHelper helper) {
        KegTests.aStandingKegIsAStationOfItsOwn(helper);
    }

    @GameTest(template = "empty")
    public static void theMonsterPotIsAPotStation(GameTestHelper helper) {
        MonsterPotTests.theMonsterPotIsAPotStation(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aStirredMonsterPotCooksTheBatch(GameTestHelper helper) {
        MonsterPotTests.aStirredMonsterPotCooksTheBatch(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aHopperFedMonsterPotCooksByItself(GameTestHelper helper) {
        MonsterPotTests.aHopperFedMonsterPotCooksByItself(helper);
    }

    @GameTest(template = "empty")
    public static void theFieryPotIsAPotStation(GameTestHelper helper) {
        FieryPotTests.theFieryPotIsAPotStation(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void aStirredFieryPotWaitsAndCooksTheBatch(GameTestHelper helper) {
        FieryPotTests.aStirredFieryPotWaitsAndCooksTheBatch(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aLongTwilightDishCooksInTenSeconds(GameTestHelper helper) {
        FieryPotTests.aLongTwilightDishCooksInTenSeconds(helper);
    }

    private static boolean spellSchools;

    /**
     * Iron's Spells reads every spell's school from its config, which a server builds when it syncs data packs to joining players:
     * a game test server has none, so every spell would be of its default school. Its sync is fired once, to no player.
     */
    private static void spellSchools(GameTestHelper helper) {
        if (!spellSchools && net.neoforged.fml.ModList.get().isLoaded("irons_spellbooks")) {
            spellSchools = true;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new net.neoforged.neoforge.event.OnDatapackSyncEvent(helper.getLevel().getServer().getPlayerList(), null));
        }
    }

    @GameTest(template = "empty")
    public static void theAlchemistPotIsAPotStation(GameTestHelper helper) {
        spellSchools(helper);
        AlchemistPotTests.theAlchemistPotIsAPotStation(helper);
    }

    @GameTest(template = "empty")
    public static void aScrollHangsOverThePot(GameTestHelper helper) {
        spellSchools(helper);
        AlchemistPotTests.aScrollHangsOverThePot(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aSchoolRecipeWaitsForItsScroll(GameTestHelper helper) {
        spellSchools(helper);
        AlchemistPotTests.aSchoolRecipeWaitsForItsScroll(helper);
    }

    @GameTest(template = "empty")
    public static void aFireScrollHeatsThePot(GameTestHelper helper) {
        spellSchools(helper);
        AlchemistPotTests.aFireScrollHeatsThePot(helper);
    }

    @GameTest(template = "empty")
    public static void theBookHangsTheScrollADishNeeds(GameTestHelper helper) {
        spellSchools(helper);
        AlchemistPotTests.theBookHangsTheScrollADishNeeds(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aBottleDishIsScoopedInABottle(GameTestHelper helper) {
        spellSchools(helper);
        AlchemistPotTests.aBottleDishIsScoopedInABottle(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aHopperFedAlchemistPotCooksByItself(GameTestHelper helper) {
        spellSchools(helper);
        AlchemistPotTests.aHopperFedAlchemistPotCooksByItself(helper);
    }

    @GameTest(template = "empty")
    public static void aDishOfMoreThanFourIsNotFilledIn(GameTestHelper helper) {
        CopperPotTests.aDishOfMoreThanFourIsNotFilledIn(helper);
    }

    @GameTest(template = "empty")
    public static void waterPoursInAndTheBucketComesBack(GameTestHelper helper) {
        KegTests.waterPoursInAndTheBucketComesBack(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void beerFermentsAndIsDrawnAtTheTap(GameTestHelper helper) {
        KegTests.beerFermentsAndIsDrawnAtTheTap(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void vodkaFermentsFromWaterAndPotatoes(GameTestHelper helper) {
        KegTests.vodkaFermentsFromWaterAndPotatoes(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void meadFermentsFromHoneyBottles(GameTestHelper helper) {
        KegTests.meadFermentsFromHoneyBottles(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void saltyFollyWantsACoolKeg(GameTestHelper helper) {
        KegTests.saltyFollyWantsACoolKeg(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pickledPicklesFromFoodThrownInBeforeTheHoney(GameTestHelper helper) {
        KegTests.pickledPicklesFromFoodThrownInBeforeTheHoney(helper);
    }

    @GameTest(template = "empty")
    public static void everyKegRecipeFromItsFoodThrownIn(GameTestHelper helper) {
        KegTests.everyKegRecipeFromItsFoodThrownIn(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void jerkyFromThreeBeefThrownIn(GameTestHelper helper) {
        KegTests.jerkyFromThreeBeefThrownIn(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void jerkyDriesInAWarmKeg(GameTestHelper helper) {
        KegTests.jerkyDriesInAWarmKeg(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void theLidKeepsThingsOutAndTheBrewWaitsUnderIt(GameTestHelper helper) {
        KegTests.theLidKeepsThingsOutAndTheBrewWaitsUnderIt(helper);
    }

    @GameTest(template = "empty")
    public static void everyLooseBakingFromItsFoodLaidOn(GameTestHelper helper) {
        CulturalOvenTests.everyLooseBakingFromItsFoodLaidOn(helper);
    }

    @GameTest(template = "empty")
    public static void theCfbOvenReadsItsRecipes(GameTestHelper helper) {
        OvenTests.theOvenReadsItsRecipes(helper);
    }

    @GameTest(template = "empty")
    public static void theCfbOvenDoorKeepsTheRackShut(GameTestHelper helper) {
        OvenTests.theDoorKeepsTheRackShut(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void foodBakesOnTheCfbOvenRack(GameTestHelper helper) {
        OvenTests.foodBakesOnTheRack(helper);
    }

    @GameTest(template = "empty")
    public static void cfbOvenFuelComesFromTheInventory(GameTestHelper helper) {
        OvenTests.fuelComesFromTheInventory(helper);
    }

    @GameTest(template = "empty")
    public static void aPickInTheBookFillsTheCfbOvenRack(GameTestHelper helper) {
        OvenTests.aPickInTheBookFillsTheRack(helper);
    }

    @GameTest(template = "empty")
    public static void theCfbOvenDoorShutsBehindTheLastCook(GameTestHelper helper) {
        OvenTests.theDoorShutsBehindTheLastCook(helper);
    }

    @GameTest(template = "empty")
    public static void aSkilletStandsOnACfbOvenBurner(GameTestHelper helper) {
        OvenTests.aSkilletStandsOnABurner(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 700)
    public static void hoppersFeedAndEmptyTheCfbOven(GameTestHelper helper) {
        OvenTests.hoppersFeedAndEmptyTheOven(helper);
    }

    @GameTest(template = "empty")
    public static void everyAgingFromItsFoodDroppedIn(GameTestHelper helper) {
        VatTests.everyAgingFromItsFoodDroppedIn(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sausagesAndMashLaidByHandBake(GameTestHelper helper) {
        CulturalOvenTests.sausagesAndMashLaidByHandBake(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void twoSetsLaidByHandBakeBoth(GameTestHelper helper) {
        CulturalOvenTests.twoSetsLaidByHandBakeBoth(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void twoSetsFromTheBookBakeBoth(GameTestHelper helper) {
        CulturalOvenTests.twoSetsFromTheBookBakeBoth(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aWaitingDishMakesWayForTheNext(GameTestHelper helper) {
        CulturalOvenTests.aWaitingDishMakesWayForTheNext(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aWaitingDishGoesWithTheCookWhoLeaves(GameTestHelper helper) {
        CulturalOvenTests.aWaitingDishGoesWithTheCookWhoLeaves(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void everyLooseBakingLaidByHand(GameTestHelper helper) {
        CulturalOvenTests.everyLooseBakingLaidByHand(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void berriesAreSharedOutForTheCheesecake(GameTestHelper helper) {
        CulturalOvenTests.berriesAreSharedOutForTheCheesecake(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sausagesAndMashInAnyOrder(GameTestHelper helper) {
        CulturalOvenTests.sausagesAndMashInAnyOrder(helper);
    }

    @GameTest(template = "empty")
    public static void theOvenReadsItsRecipes(GameTestHelper helper) {
        CulturalOvenTests.theOvenReadsItsRecipes(helper);
    }

    @GameTest(template = "empty")
    public static void theDoorKeepsTheTrayShut(GameTestHelper helper) {
        CulturalOvenTests.theDoorKeepsTheTrayShut(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void breadBakesWithTheDoorShut(GameTestHelper helper) {
        CulturalOvenTests.breadBakesWithTheDoorShut(helper);
    }

    @GameTest(template = "empty")
    public static void aPieIsLaidOutFromTheBook(GameTestHelper helper) {
        CulturalOvenTests.aPieIsLaidOutFromTheBook(helper);
    }

    @GameTest(template = "empty")
    public static void aStackIsSharedOutForItsRecipe(GameTestHelper helper) {
        CulturalOvenTests.aStackIsSharedOutForItsRecipe(helper);
    }

    @GameTest(template = "empty")
    public static void theVatReadsItsRecipes(GameTestHelper helper) {
        VatTests.theVatReadsItsRecipes(helper);
    }

    @GameTest(template = "empty")
    public static void theLidKeepsTheVatShut(GameTestHelper helper) {
        VatTests.theLidKeepsTheVatShut(helper);
    }

    @GameTest(template = "empty")
    public static void aNewVatStandsUpright(GameTestHelper helper) {
        VatTests.aNewVatStandsUpright(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void beerAgesWithTheLidShutAndIsDrawnIntoABottle(GameTestHelper helper) {
        VatTests.beerAgesWithTheLidShutAndIsDrawnIntoABottle(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aBottleInItsSlotWorksAsBefore(GameTestHelper helper) {
        VatTests.aBottleInItsSlotWorksAsBefore(helper);
    }

    @GameTest(template = "empty")
    public static void aStackIsSharedOutAndBottlesStayOut(GameTestHelper helper) {
        VatTests.aStackIsSharedOutAndBottlesStayOut(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiKettleLidIsTheStations(GameTestHelper helper) {
        YoukaiKettleTests.youkaiKettleLidIsTheStations(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiKettleWaterStaysFromAnEmptyHand(GameTestHelper helper) {
        YoukaiKettleTests.youkaiKettleWaterStaysFromAnEmptyHand(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void youkaiBlackTeaBoilsBrewsAndIsPoured(GameTestHelper helper) {
        YoukaiKettleTests.youkaiBlackTeaBoilsBrewsAndIsPoured(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiKettleReadsItsRecipes(GameTestHelper helper) {
        YoukaiKettleTests.youkaiKettleReadsItsRecipes(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiBoardReadsItsRecipes(GameTestHelper helper) {
        YoukaiBoardTests.youkaiBoardReadsItsRecipes(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiNigiriIsShapedAndTopped(GameTestHelper helper) {
        YoukaiBoardTests.youkaiNigiriIsShapedAndTopped(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiMakiFromTheBookIsRolledAndCut(GameTestHelper helper) {
        YoukaiBoardTests.youkaiMakiFromTheBookIsRolledAndCut(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiFlaskOfSoySauceIsAStep(GameTestHelper helper) {
        YoukaiBoardTests.youkaiFlaskOfSoySauceIsAStep(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiBoardTakesTheLastStepBack(GameTestHelper helper) {
        YoukaiBoardTests.youkaiBoardTakesTheLastStepBack(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiBoardTakesBackOnlyTheLastStep(GameTestHelper helper) {
        YoukaiBoardTests.youkaiBoardTakesBackOnlyTheLastStep(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiBookMakesSeveralDishes(GameTestHelper helper) {
        YoukaiBoardTests.youkaiBookMakesSeveralDishes(helper);
    }

    @GameTest(template = "empty")
    public static void stumpCutsFoodLikeTheBoard(GameTestHelper helper) {
        StumpTests.stumpCutsFoodLikeTheBoard(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stumpTakesNoFoodAfterThirtyTwoCuts(GameTestHelper helper) {
        StumpTests.stumpTakesNoFoodAfterThirtyTwoCuts(helper);
    }

    @GameTest(template = "empty")
    public static void stumpSplitsALogWithTheAxe(GameTestHelper helper) {
        StumpTests.stumpSplitsALogWithTheAxe(helper);
    }

    @GameTest(template = "empty")
    public static void stumpStationIsQuietWithoutSableTrees(GameTestHelper helper) {
        StumpTests.stumpStationIsQuietWithoutSableTrees(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiPotsReadTheirRecipes(GameTestHelper helper) {
        YoukaiPotTests.youkaiPotsReadTheirRecipes(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void youkaiShortPotCooksAFeast(GameTestHelper helper) {
        YoukaiPotTests.youkaiShortPotCooksAFeast(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void youkaiSmallPotCooksFromTheBook(GameTestHelper helper) {
        YoukaiPotTests.youkaiSmallPotCooksFromTheBook(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 1300)
    public static void youkaiStockpotFinishesWithNobodyAtIt(GameTestHelper helper) {
        YoukaiPotTests.youkaiStockpotFinishesWithNobodyAtIt(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void youkaiSteamerSteamsAPotato(GameTestHelper helper) {
        YoukaiSteamerTests.youkaiSteamerSteamsAPotato(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiSteamerLaysTheTower(GameTestHelper helper) {
        YoukaiSteamerTests.youkaiSteamerLaysTheTower(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiSteamerIsOneTower(GameTestHelper helper) {
        YoukaiSteamerTests.youkaiSteamerIsOneTower(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiSteamerTakesARackOnShift(GameTestHelper helper) {
        YoukaiSteamerTests.youkaiSteamerTakesARackOnShift(helper);
    }

    @GameTest(template = "empty")
    public static void anEmptyYoukaiPotTakesItsWaterAtTheStation(GameTestHelper helper) {
        YoukaiPotTests.anEmptyYoukaiPotTakesItsWaterAtTheStation(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiTankReadsItsRecipes(GameTestHelper helper) {
        YoukaiTankTests.youkaiTankReadsItsRecipes(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiTankLidIsItsBlocks(GameTestHelper helper) {
        YoukaiTankTests.youkaiTankLidIsItsBlocks(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void youkaiMioFermentsAndIsDipped(GameTestHelper helper) {
        YoukaiTankTests.youkaiMioFermentsAndIsDipped(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void youkaiTeaLeavesTurnBlack(GameTestHelper helper) {
        YoukaiTankTests.youkaiTeaLeavesTurnBlack(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void youkaiFlaskIsFilledAtTheTank(GameTestHelper helper) {
        YoukaiTankTests.youkaiFlaskIsFilledAtTheTank(helper);
    }

    @GameTest(template = "empty")
    public static void hearthKegReadsItsRecipes(GameTestHelper helper) {
        HearthKegTests.hearthKegReadsItsRecipes(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void hearthWineFermentsAndIsTapped(GameTestHelper helper) {
        HearthKegTests.hearthWineFermentsAndIsTapped(helper);
    }

    @GameTest(template = "empty")
    public static void hearthBookFillsCountsAndWaterGoesBack(GameTestHelper helper) {
        HearthKegTests.hearthBookFillsCountsAndWaterGoesBack(helper);
    }

    @GameTest(template = "empty")
    public static void hearthBigKegIsOneStation(GameTestHelper helper) {
        HearthKegTests.hearthBigKegIsOneStation(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void hearthBigKegBrewsBeer(GameTestHelper helper) {
        HearthKegTests.hearthBigKegBrewsBeer(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void hearthPicklesWaitToBeTaken(GameTestHelper helper) {
        HearthKegTests.hearthPicklesWaitToBeTaken(helper);
    }

    @GameTest(template = "empty")
    public static void hearthCaskReadsItsRecipes(GameTestHelper helper) {
        HearthCaskTests.hearthCaskReadsItsRecipes(helper);
    }

    @GameTest(template = "empty")
    public static void hearthCaskLidIsItsSeal(GameTestHelper helper) {
        HearthCaskTests.hearthCaskLidIsItsSeal(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void hearthWineAgesSealed(GameTestHelper helper) {
        HearthCaskTests.hearthWineAgesSealed(helper);
    }

    @GameTest(template = "empty")
    public static void hearthCaskBookLaysJerky(GameTestHelper helper) {
        HearthCaskTests.hearthCaskBookLaysJerky(helper);
    }

    @GameTest(template = "empty")
    public static void theGrillReadsItsRecipes(GameTestHelper helper) {
        GrillTests.theGrillReadsItsRecipes(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aSkewerTurnsOnceFromItsHalfAndIsDoneAfter(GameTestHelper helper) {
        GrillTests.aSkewerTurnsOnceFromItsHalfAndIsDoneAfter(helper);
    }

    @GameTest(template = "empty")
    public static void aPowderSeasonsASkewerOnTheGrill(GameTestHelper helper) {
        GrillTests.aPowderSeasonsASkewerOnTheGrill(helper);
    }

    @GameTest(template = "empty")
    public static void theGrillOpensForWhatItTakes(GameTestHelper helper) {
        GrillTests.theGrillOpensForWhatItTakes(helper);
    }

    @GameTest(template = "empty")
    public static void theBasinReadsItsRecipes(GameTestHelper helper) {
        BasinTests.theBasinReadsItsRecipes(helper);
    }

    @GameTest(template = "empty")
    public static void aStickSkewersTheFoodOfOneCompartmentWithTheSideOfTheOther(GameTestHelper helper) {
        BasinTests.aStickSkewersTheFoodOfOneCompartmentWithTheSideOfTheOther(helper);
    }

    @GameTest(template = "empty")
    public static void aRecipeFromTheBookFillsAndSkewers(GameTestHelper helper) {
        BasinTests.aRecipeFromTheBookFillsAndSkewers(helper);
    }

    @GameTest(template = "empty")
    public static void everyKettleRecipeFromItsLeavesThrownIn(GameTestHelper helper) {
        KettleTests.everyKettleRecipeFromItsLeavesThrownIn(helper);
    }

    @GameTest(template = "empty")
    public static void theKettlesLidIsTheBlocksOwn(GameTestHelper helper) {
        KettleTests.theLidIsTheBlocksOwn(helper);
    }

    @GameTest(template = "empty")
    public static void aBucketFromTheInventoryPoursIntoTheKettle(GameTestHelper helper) {
        KettleTests.aBucketFromTheInventoryPoursIn(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void greenTeaBrewsAndIsPouredFromTheSpout(GameTestHelper helper) {
        KettleTests.greenTeaBrewsAndIsPouredFromTheSpout(helper);
    }

    @GameTest(template = "empty")
    public static void leavesDroppedInLieAsTheRecipeWants(GameTestHelper helper) {
        KettleTests.leavesDroppedInLieAsTheRecipeWants(helper);
    }

    @GameTest(template = "empty")
    public static void theKettleClientsSeeEmptiesWithTheServers(GameTestHelper helper) {
        KettleTests.theKettleClientsSeeEmptiesWithTheServers(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void kettleTimeScalesTheBrew(GameTestHelper helper) {
        KettleTests.kettleTimeScalesTheBrew(helper);
    }

    @GameTest(template = "empty")
    public static void milkPoursForCocoaAndSteepsInTea(GameTestHelper helper) {
        KettleTests.milkPoursForCocoaAndSteepsInTea(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void youkaiTeaMochaTakesAMilkBottle(GameTestHelper helper) {
        YoukaiKettleTests.youkaiTeaMochaTakesAMilkBottle(helper);
    }

    @GameTest(template = "empty")
    public static void cutPiecesThatDoNotFitDropOnce(GameTestHelper helper) {
        FullInventoryTests.cutPiecesThatDoNotFitDropOnce(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aStoveWithFewerPlacesGrillsOnThoseItHas(GameTestHelper helper) {
        SmallStoveTests.aStoveWithFewerPlacesGrillsOnThoseItHas(helper);
    }

    @GameTest(template = "empty")
    public static void everyDishIsTheServersRecipe(GameTestHelper helper) {
        RecipeEditTests.everyDishIsTheServersRecipe(helper);
    }

    @GameTest(template = "empty")
    public static void aScriptsEditsReachTheBoard(GameTestHelper helper) {
        RecipeEditTests.aScriptsEditsReachTheBoard(helper);
    }

    @GameTest(template = "empty")
    public static void aScriptsEditsReachEveryStation(GameTestHelper helper) {
        RecipeEditTests.aScriptsEditsReachEveryStation(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 1200, batch = "reload_scripts")
    public static void aReloadKeepsTheScriptsEdits(GameTestHelper helper) {
        RecipeEditTests.aReloadKeepsTheScriptsEdits(helper);
    }

    @GameTest(template = "empty")
    public static void aKnifeThatBreaksOnTheLastCutLeavesThePieces(GameTestHelper helper) {
        FullInventoryTests.aKnifeThatBreaksOnTheLastCutLeavesThePieces(helper);
    }

    @GameTest(template = "empty")
    public static void aYoukaiPotGivesTheContainerBackIntoTheEmptiedSlot(GameTestHelper helper) {
        FullInventoryTests.aYoukaiPotGivesTheContainerBackIntoTheEmptiedSlot(helper);
    }

    @GameTest(template = "empty")
    public static void theCuisineBoardGivesTheContainerBackIntoTheEmptiedSlot(GameTestHelper helper) {
        FullInventoryTests.theCuisineBoardGivesTheContainerBackIntoTheEmptiedSlot(helper);
    }

    @GameTest(template = "empty")
    public static void youkaiSteamerTakesBackEachCooksOwn(GameTestHelper helper) {
        YoukaiSteamerTests.youkaiSteamerTakesBackEachCooksOwn(helper);
    }

    /** {@link PotTests.MealMaker}'s meal of what lies in the pot on NeoForge. */
    private static final class MealOfWhatLiesThere extends CookingPotRecipe {
        private final NonNullList<Ingredient> inputs;
        private final ItemStack plain;

        MealOfWhatLiesThere(NonNullList<Ingredient> inputs, ItemStack made, ItemStack plain) {
            super("", CookingPotRecipeBookTab.MEALS, inputs, made, new ItemStack(Items.BOWL), 0.1F, 40);
            this.inputs = inputs;
            this.plain = plain;
        }

        /** Exactly its inputs, decided here as Modest Meals decides: the listed ingredients are none. */
        @Override
        public boolean matches(RecipeWrapper input, Level level) {
            List<ItemStack> there = new ArrayList<>();
            for (int slot = 0; slot < CookingPotRecipe.INPUT_SLOTS; slot++) {
                if (!input.getItem(slot).isEmpty()) {
                    there.add(input.getItem(slot));
                }
            }
            return there.size() == inputs.size() && inputs.stream().allMatch(ingredient -> there.stream().anyMatch(ingredient));
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return NonNullList.create();
        }

        @Override
        public ItemStack getResultItem(HolderLookup.Provider registries) {
            return plain;
        }
    }
}
