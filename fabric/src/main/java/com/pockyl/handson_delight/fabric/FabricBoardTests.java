package com.pockyl.handson_delight.fabric;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import com.pockyl.handson_delight.gametest.BoardTests;
import com.pockyl.handson_delight.gametest.RecipeAudit;

import java.util.Arrays;

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

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void everyCutIsTheStations(GameTestHelper helper) {
        BoardTests.everyCutIsTheStations(helper);
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
    public void dishesCutTogetherNeedAKnife(GameTestHelper helper) {
        BoardTests.dishesCutTogetherNeedAKnife(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anIgnoredChopIsAnswered(GameTestHelper helper) {
        BoardTests.anIgnoredChopIsAnswered(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anotherCookKeepsTheMarks(GameTestHelper helper) {
        BoardTests.anotherCookKeepsTheMarks(helper);
    }

    /**
     * An addon's axe cut written as {@code fabric:any} round Refabricated's tool action, or deeper inside Fabric's other
     * ingredients, tests and lists its axes: in a released game the tool action wrapped by Fabric throws on every test.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anAxeCutInsideFabricIngredientsKnowsItsAxes(GameTestHelper helper) {
        String axe = "{\"fabric:type\": \"farmersdelight:tool_action\", \"action\": \"axe_dig\"}";
        Ingredient any = FabricIngredients.unwrap(Ingredient.fromJson(JsonParser.parseString(
                "{\"fabric:type\": \"fabric:any\", \"ingredients\": [" + axe + ", {\"item\": \"minecraft:shears\"}]}")));
        helper.assertTrue(any.test(new ItemStack(Items.IRON_AXE)) && any.test(new ItemStack(Items.SHEARS)), "an axe and shears cut");
        helper.assertFalse(any.test(new ItemStack(Items.CARROT)), "a carrot does not");
        helper.assertTrue(lists(any, Items.IRON_AXE) && lists(any, Items.SHEARS), "the book shows the axes and the shears");

        Ingredient difference = FabricIngredients.unwrap(Ingredient.fromJson(JsonParser.parseString(
                "{\"fabric:type\": \"fabric:difference\", \"base\": {\"fabric:type\": \"fabric:any\", \"ingredients\": [" + axe
                        + "]}, \"subtracted\": {\"item\": \"minecraft:wooden_axe\"}}")));
        helper.assertTrue(difference.test(new ItemStack(Items.IRON_AXE)), "an iron axe cuts deeper inside");
        helper.assertFalse(difference.test(new ItemStack(Items.WOODEN_AXE)), "the subtracted axe does not");
        helper.assertTrue(lists(difference, Items.IRON_AXE) && !lists(difference, Items.WOODEN_AXE), "and the book shows it so");
        helper.succeed();
    }

    private static boolean lists(Ingredient ingredient, Item item) {
        return Arrays.stream(ingredient.getItems()).anyMatch(stack -> stack.is(item));
    }
}
