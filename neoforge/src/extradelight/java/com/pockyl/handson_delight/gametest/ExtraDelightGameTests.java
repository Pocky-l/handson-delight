package com.pockyl.handson_delight.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Extra Delight's stations, run with {@code -Pextradelight} only: with Extra Delight the other tests that make Farmer's Delight's
 * stews, hot cocoa, apple pie or dough fail by design, so these have a namespace of their own.
 */
@GameTestHolder("handson_extradelight")
@PrefixGameTestTemplate(false)
public final class ExtraDelightGameTests {
    private ExtraDelightGameTests() {
    }

    @GameTest(template = "empty")
    public static void everyMixingFromItsFoodDroppedIn(GameTestHelper helper) {
        BowlTests.everyMixingFromItsFoodDroppedIn(helper);
    }

    @GameTest(template = "empty")
    public static void theBowlReadsItsRecipes(GameTestHelper helper) {
        BowlTests.theBowlReadsItsRecipes(helper);
    }

    @GameTest(template = "empty")
    public static void aLiquidIsPouredAndDrawnBack(GameTestHelper helper) {
        BowlTests.aLiquidIsPouredAndDrawnBack(helper);
    }

    @GameTest(template = "empty")
    public static void anEggIsCrackedForItsWhite(GameTestHelper helper) {
        BowlTests.anEggIsCrackedForItsWhite(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void eggMixIsStirredByCircles(GameTestHelper helper) {
        BowlTests.eggMixIsStirredByCircles(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void aBowlfulIsStirredAtOnceAndWaitsForItsBowls(GameTestHelper helper) {
        BowlTests.aBowlfulIsStirredAtOnceAndWaitsForItsBowls(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theWrongUtensilStirsNothing(GameTestHelper helper) {
        BowlTests.theWrongUtensilStirsNothing(helper);
    }

    @GameTest(template = "empty")
    public static void theBookFillsFoodAndLiquids(GameTestHelper helper) {
        BowlTests.theBookFillsFoodAndLiquids(helper);
    }

    @GameTest(template = "empty")
    public static void everyEdAgingFromItsFoodDroppedIn(GameTestHelper helper) {
        EdVatTests.everyEdAgingFromItsFoodDroppedIn(helper);
    }

    @GameTest(template = "empty")
    public static void theEdVatReadsItsRecipes(GameTestHelper helper) {
        EdVatTests.theEdVatReadsItsRecipes(helper);
    }

    @GameTest(template = "empty")
    public static void liquidIsPouredAndDrawnBack(GameTestHelper helper) {
        EdVatTests.liquidIsPouredAndDrawnBack(helper);
    }

    @GameTest(template = "empty")
    public static void sauerkrautAgesInItsStagesAndIsDrawnIntoBowls(GameTestHelper helper) {
        EdVatTests.sauerkrautAgesInItsStagesAndIsDrawnIntoBowls(helper);
    }

    @GameTest(template = "empty")
    public static void theEdLidIsABlockOnTheVat(GameTestHelper helper) {
        EdVatTests.theEdLidIsABlockOnTheVat(helper);
    }

    @GameTest(template = "empty")
    public static void edContainersInTheirSlotWorkAsBefore(GameTestHelper helper) {
        EdVatTests.edContainersInTheirSlotWorkAsBefore(helper);
    }

    @GameTest(template = "empty")
    public static void everyEdBakingFromItsFoodLaidIn(GameTestHelper helper) {
        EdOvenTests.everyEdBakingFromItsFoodLaidIn(helper);
    }

    @GameTest(template = "empty")
    public static void theEdOvenReadsItsRecipes(GameTestHelper helper) {
        EdOvenTests.theEdOvenReadsItsRecipes(helper);
    }

    @GameTest(template = "empty")
    public static void bakewareStandsInTheOvenAndComesBack(GameTestHelper helper) {
        EdOvenTests.bakewareStandsInTheOvenAndComesBack(helper);
    }

    @GameTest(template = "empty")
    public static void doughWithoutItsSheetWaitsForIt(GameTestHelper helper) {
        EdOvenTests.doughWithoutItsSheetWaitsForIt(helper);
    }

    @GameTest(template = "empty")
    public static void aRightClickLaysOneAndTheDoughBringsItsSheet(GameTestHelper helper) {
        EdOvenTests.aRightClickLaysOneAndTheDoughBringsItsSheet(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 700)
    public static void cookiesBakeWithTheSheetPushedIn(GameTestHelper helper) {
        EdOvenTests.cookiesBakeWithTheSheetPushedIn(helper);
    }

    @GameTest(template = "empty")
    public static void theMeltingPotReadsItsRecipes(GameTestHelper helper) {
        MeltingPotTests.theMeltingPotReadsItsRecipes(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void chocolateMeltsAndIsDrawnIntoABottle(GameTestHelper helper) {
        MeltingPotTests.chocolateMeltsAndIsDrawnIntoABottle(helper);
    }
}
