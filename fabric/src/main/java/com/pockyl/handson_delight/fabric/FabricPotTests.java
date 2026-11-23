package com.pockyl.handson_delight.fabric;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import vectorwing.farmersdelight.common.crafting.CookingPotBookCategory;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.refabricated.inventory.RecipeWrapper;

import com.pockyl.handson_delight.gametest.PotTests;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** {@link PotTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}. */
public final class FabricPotTests {
    @GameTest(maxTicks = 100)
    public void whatWentInWithoutFloatingComesBackOut(GameTestHelper helper) {
        PotTests.whatWentInWithoutFloatingComesBackOut(helper);
    }

    @GameTest(maxTicks = 100)
    public void slotsAreThePotsOwn(GameTestHelper helper) {
        PotTests.slotsAreThePotsOwn(helper);
    }

    @GameTest(maxTicks = 100)
    public void theMealStaysInThePot(GameTestHelper helper) {
        PotTests.theMealStaysInThePot(helper);
    }

    @GameTest(maxTicks = 100)
    public void aBowlClickedOnTheSoupFills(GameTestHelper helper) {
        PotTests.aBowlClickedOnTheSoupFills(helper);
    }

    @GameTest(maxTicks = 100)
    public void anEmptyHandScoopsWithBowlsFromTheInventory(GameTestHelper helper) {
        PotTests.anEmptyHandScoopsWithBowlsFromTheInventory(helper);
    }

    @GameTest(maxTicks = 100)
    public void milkAskedForAsFoodIsMilk(GameTestHelper helper) {
        PotTests.milkAskedForAsFoodIsMilk(helper);
    }

    @GameTest(maxTicks = 100)
    public void twoAlikeThrownInMakeTheirDish(GameTestHelper helper) {
        PotTests.twoAlikeThrownInMakeTheirDish(helper);
    }

    @GameTest(maxTicks = 100)
    public void aFullPotTakesWhatItsDishStillReads(GameTestHelper helper) {
        PotTests.aFullPotTakesWhatItsDishStillReads(helper);
    }

    @GameTest(maxTicks = 100)
    public void theFullerDishOnceItsLastIngredientIsIn(GameTestHelper helper) {
        PotTests.theFullerDishOnceItsLastIngredientIsIn(helper);
    }

    @GameTest(maxTicks = 100)
    public void aTagTakesWhatTheOtherIngredientsLeave(GameTestHelper helper) {
        PotTests.aTagTakesWhatTheOtherIngredientsLeave(helper);
    }

    @GameTest(maxTicks = 100)
    public void theBookKeepsThePickedDish(GameTestHelper helper) {
        PotTests.theBookKeepsThePickedDish(helper);
    }

    @GameTest(maxTicks = 100)
    public void aDishNobodyCanMakeIsNotOffered(GameTestHelper helper) {
        PotTests.aDishNobodyCanMakeIsNotOffered(helper);
    }

    @GameTest(maxTicks = 100)
    public void everyDishFromItsFoodThrownIn(GameTestHelper helper) {
        PotTests.everyDishFromItsFoodThrownIn(helper);
    }

    @GameTest(maxTicks = 100)
    public void aStackIsSharedOutForARecipeOfTwoAlike(GameTestHelper helper) {
        PotTests.aStackIsSharedOutForARecipeOfTwoAlike(helper);
    }

    @GameTest(maxTicks = 100)
    public void dishesStayOutOfTheWater(GameTestHelper helper) {
        PotTests.dishesStayOutOfTheWater(helper);
    }

    @GameTest(maxTicks = 100)
    public void shiftFillsTheWholeStack(GameTestHelper helper) {
        PotTests.shiftFillsTheWholeStack(helper);
    }

    @GameTest(maxTicks = 340)
    public void theWholeBatchCooksAtOnce(GameTestHelper helper) {
        PotTests.theWholeBatchCooksAtOnce(helper);
    }

    @GameTest(maxTicks = 100)
    public void theCooksClockJudgesThePace(GameTestHelper helper) {
        PotTests.theCooksClockJudgesThePace(helper);
    }

    @GameTest(maxTicks = 100)
    public void aFloodOfStrokesFillsNoFaster(GameTestHelper helper) {
        PotTests.aFloodOfStrokesFillsNoFaster(helper);
    }

    @GameTest(maxTicks = 100)
    public void theBookFillsThePot(GameTestHelper helper) {
        PotTests.theBookFillsThePot(helper);
    }

    @GameTest(maxTicks = 100)
    public void aBonusIsCarriedOn(GameTestHelper helper) {
        PotTests.aBonusIsCarriedOn(helper);
    }

    @GameTest(maxTicks = 400)
    public void theSetsSavedGoBack(GameTestHelper helper) {
        PotTests.theSetsSavedGoBack(helper);
    }

    @GameTest(maxTicks = 450)
    public void theSetsSavedStayWhenTheCookLeft(GameTestHelper helper) {
        PotTests.theSetsSavedStayWhenTheCookLeft(helper);
    }

    @GameTest(maxTicks = 500)
    public void theEmptiedContainerGoesToTheCook(GameTestHelper helper) {
        PotTests.theEmptiedContainerGoesToTheCook(helper);
    }

    @GameTest(maxTicks = 360)
    public void aPotLeftByItsCookCooksOn(GameTestHelper helper) {
        PotTests.aPotLeftByItsCookCooksOn(helper);
    }

    @GameTest(maxTicks = 260)
    public void aCookWhoKnowsTheDishGetsMore(GameTestHelper helper) {
        PotTests.aCookWhoKnowsTheDishGetsMore(helper);
    }

    @GameTest(maxTicks = 260)
    public void theFirstTimeHasOneStar(GameTestHelper helper) {
        PotTests.theFirstTimeHasOneStar(helper);
    }

    @GameTest(maxTicks = 100)
    public void onlyGoodStirringEarnsStars(GameTestHelper helper) {
        PotTests.onlyGoodStirringEarnsStars(helper);
    }

    @GameTest(maxTicks = 200)
    public void nothingCooksWithoutStirring(GameTestHelper helper) {
        PotTests.nothingCooksWithoutStirring(helper);
    }

    @GameTest(maxTicks = 100)
    public void theStationReadsHeat(GameTestHelper helper) {
        PotTests.theStationReadsHeat(helper);
    }

    @GameTest(maxTicks = 220)
    public void theSmallestStackSetsTheBatch(GameTestHelper helper) {
        PotTests.theSmallestStackSetsTheBatch(helper);
    }

    @GameTest(maxTicks = 300)
    public void aHopperFedPotCooksByItself(GameTestHelper helper) {
        PotTests.aHopperFedPotCooksByItself(helper);
    }

    @GameTest(maxTicks = 300)
    public void aHopperFeedsThePotByItselfBesideACook(GameTestHelper helper) {
        PotTests.aHopperFeedsThePotByItselfBesideACook(helper);
    }

    @GameTest(maxTicks = 60)
    public void oneSpoonOneHand(GameTestHelper helper) {
        PotTests.oneSpoonOneHand(helper);
    }

    @GameTest(maxTicks = 100)
    public void aServingTakenByHandCounts(GameTestHelper helper) {
        PotTests.aServingTakenByHandCounts(helper);
    }

    @GameTest(maxTicks = 160)
    public void aModdedRecipeCooksAsABatch(GameTestHelper helper) {
        PotTests.aModdedRecipeCooksAsABatch(helper);
    }

    @GameTest(maxTicks = 160)
    public void aMealOfWhatLiesThereCooksAsABatch(GameTestHelper helper) {
        PotTests.aMealOfWhatLiesThereCooksAsABatch(helper, MealOfWhatLiesThere::new);
    }

    @GameTest(maxTicks = 220)
    public void aDishWithoutAContainerComesOutWhole(GameTestHelper helper) {
        PotTests.aDishWithoutAContainerComesOutWhole(helper);
    }

    @GameTest(maxTicks = 320)
    public void moreRiceThanAStackStillCooks(GameTestHelper helper) {
        PotTests.moreRiceThanAStackStillCooks(helper);
    }

    @GameTest(maxTicks = 200)
    public void riceAddedWhileCookingKeepsTheProgress(GameTestHelper helper) {
        PotTests.riceAddedWhileCookingKeepsTheProgress(helper);
    }

    @GameTest(maxTicks = 100)
    public void theCookingTimeFollowsTheConfig(GameTestHelper helper) {
        PotTests.theCookingTimeFollowsTheConfig(helper);
    }

    /** {@link PotTests.MealMaker}'s meal of what lies in the pot on Fabric. */
    private static final class MealOfWhatLiesThere extends CookingPotRecipe {
        private final List<Ingredient> inputs;
        private final ItemStack plain;

        MealOfWhatLiesThere(List<Ingredient> inputs, ItemStack made, ItemStack plain) {
            super("", CookingPotBookCategory.MEALS, inputs, made,
                    Optional.of(new ItemStack(Items.BOWL)), 0.1F, 40);
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
        public List<Ingredient> input() {
            return List.of();
        }

        @Override
        public ItemStack result() {
            return plain;
        }
    }
}
