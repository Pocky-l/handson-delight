package com.pockyl.handson_delight.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Modest Meals' meals at the board, run with {@code -Pmeals} only: with Modest Meals the other tests that make Farmer's Delight's
 * burgers and soups fail by design, so these have a namespace of their own.
 */
@GameTestHolder("handson_meals")
@PrefixGameTestTemplate(false)
public final class MealGameTests {
    private MealGameTests() {
    }

    @GameTest(template = "empty")
    public static void modestMealsComeToTheBoard(GameTestHelper helper) {
        BoardTests.modestMealsComeToTheBoard(helper);
    }

    @GameTest(template = "empty")
    public static void aModestHamburgerIsPutTogether(GameTestHelper helper) {
        BoardTests.aModestHamburgerIsPutTogether(helper);
    }

    @GameTest(template = "empty")
    public static void aSandwichOfFreeFillings(GameTestHelper helper) {
        BoardTests.aSandwichOfFreeFillings(helper);
    }

    @GameTest(template = "empty")
    public static void aPieOfFreeFillingsGoesIntoItsCrust(GameTestHelper helper) {
        BoardTests.aPieOfFreeFillingsGoesIntoItsCrust(helper);
    }

    @GameTest(template = "empty")
    public static void aSaladOfTwoWaitsForAThird(GameTestHelper helper) {
        BoardTests.aSaladOfTwoWaitsForAThird(helper);
    }
}
