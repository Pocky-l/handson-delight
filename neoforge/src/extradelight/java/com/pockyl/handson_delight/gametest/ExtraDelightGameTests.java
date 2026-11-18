package com.pockyl.handson_delight.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Extra Delight's stations, run with {@code -Pextradelight} only: Extra Delight overrides Farmer's Delight's stews, cocoa, pies and
 * dough, so the other tests that make those fail by design with it; these have a namespace of their own.
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
}
