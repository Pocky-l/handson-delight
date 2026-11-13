package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import vectorwing.farmersdelight.client.recipebook.CookingPotRecipeBookTab;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.refabricated.inventory.RecipeWrapper;

import com.pockyl.handson_delight.gametest.PotTests;

import java.util.ArrayList;
import java.util.List;

/** {@link PotTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}. */
public final class FabricPotTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void whatWentInWithoutFloatingComesBackOut(GameTestHelper helper) {
        PotTests.whatWentInWithoutFloatingComesBackOut(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void slotsAreThePotsOwn(GameTestHelper helper) {
        PotTests.slotsAreThePotsOwn(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theMealStaysInThePot(GameTestHelper helper) {
        PotTests.theMealStaysInThePot(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aBowlClickedOnTheSoupFills(GameTestHelper helper) {
        PotTests.aBowlClickedOnTheSoupFills(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anEmptyHandScoopsWithBowlsFromTheInventory(GameTestHelper helper) {
        PotTests.anEmptyHandScoopsWithBowlsFromTheInventory(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aStackIsSharedOutForARecipeOfTwoAlike(GameTestHelper helper) {
        PotTests.aStackIsSharedOutForARecipeOfTwoAlike(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void dishesStayOutOfTheWater(GameTestHelper helper) {
        PotTests.dishesStayOutOfTheWater(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void shiftFillsTheWholeStack(GameTestHelper helper) {
        PotTests.shiftFillsTheWholeStack(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 340)
    public void theWholeBatchCooksAtOnce(GameTestHelper helper) {
        PotTests.theWholeBatchCooksAtOnce(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theCooksClockJudgesThePace(GameTestHelper helper) {
        PotTests.theCooksClockJudgesThePace(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theBookFillsThePot(GameTestHelper helper) {
        PotTests.theBookFillsThePot(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void aBonusIsCarriedOn(GameTestHelper helper) {
        PotTests.aBonusIsCarriedOn(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void theSetsSavedGoBack(GameTestHelper helper) {
        PotTests.theSetsSavedGoBack(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 500)
    public void theEmptiedContainerGoesToTheCook(GameTestHelper helper) {
        PotTests.theEmptiedContainerGoesToTheCook(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 360)
    public void aPotLeftByItsCookCooksOn(GameTestHelper helper) {
        PotTests.aPotLeftByItsCookCooksOn(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 260)
    public void aCookWhoKnowsTheDishGetsMore(GameTestHelper helper) {
        PotTests.aCookWhoKnowsTheDishGetsMore(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 260)
    public void theFirstTimeHasOneStar(GameTestHelper helper) {
        PotTests.theFirstTimeHasOneStar(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void onlyGoodStirringEarnsStars(GameTestHelper helper) {
        PotTests.onlyGoodStirringEarnsStars(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void nothingCooksWithoutStirring(GameTestHelper helper) {
        PotTests.nothingCooksWithoutStirring(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theStationReadsHeat(GameTestHelper helper) {
        PotTests.theStationReadsHeat(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 220)
    public void theSmallestStackSetsTheBatch(GameTestHelper helper) {
        PotTests.theSmallestStackSetsTheBatch(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300)
    public void aHopperFedPotCooksByItself(GameTestHelper helper) {
        PotTests.aHopperFedPotCooksByItself(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300)
    public void aHopperFeedsThePotByItselfBesideACook(GameTestHelper helper) {
        PotTests.aHopperFeedsThePotByItselfBesideACook(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
    public void oneSpoonOneHand(GameTestHelper helper) {
        PotTests.oneSpoonOneHand(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aServingTakenByHandCounts(GameTestHelper helper) {
        PotTests.aServingTakenByHandCounts(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 160)
    public void aModdedRecipeCooksAsABatch(GameTestHelper helper) {
        PotTests.aModdedRecipeCooksAsABatch(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 160)
    public void aMealOfWhatLiesThereCooksAsABatch(GameTestHelper helper) {
        PotTests.aMealOfWhatLiesThereCooksAsABatch(helper, MealOfWhatLiesThere::new);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 220)
    public void aDishWithoutAContainerComesOutWhole(GameTestHelper helper) {
        PotTests.aDishWithoutAContainerComesOutWhole(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 320)
    public void moreRiceThanAStackStillCooks(GameTestHelper helper) {
        PotTests.moreRiceThanAStackStillCooks(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void riceAddedWhileCookingKeepsTheProgress(GameTestHelper helper) {
        PotTests.riceAddedWhileCookingKeepsTheProgress(helper);
    }

    /** {@link PotTests.MealMaker}'s meal of what lies in the pot on Fabric. */
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
