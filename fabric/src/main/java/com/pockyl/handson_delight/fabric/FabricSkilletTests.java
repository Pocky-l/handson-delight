package com.pockyl.handson_delight.fabric;

import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.gametest.SkilletTests;

/** {@link SkilletTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}. */
public final class FabricSkilletTests {
    @GameTest(maxTicks = 260)
    public void aBatchFriesInHalvesAndWaitsToBeTurned(GameTestHelper helper) {
        SkilletTests.aBatchFriesInHalvesAndWaitsToBeTurned(helper);
    }

    @GameTest(maxTicks = 160)
    public void farmersDelightsRightClickCooksItsWay(GameTestHelper helper) {
        SkilletTests.farmersDelightsRightClickCooksItsWay(helper);
    }

    @GameTest(maxTicks = 200)
    public void anEggFriesWithoutATurn(GameTestHelper helper) {
        SkilletTests.anEggFriesWithoutATurn(helper);
    }

    @GameTest(maxTicks = 100)
    public void everyCampfireFoodGoesInThePan(GameTestHelper helper) {
        SkilletTests.everyCampfireFoodGoesInThePan(helper);
    }

    @GameTest(maxTicks = 600)
    public void friedFoodFriesNoFurther(GameTestHelper helper) {
        SkilletTests.friedFoodFriesNoFurther(helper);
    }

    @GameTest(maxTicks = 160, environment = "handson_delight:skillet_off")
    public void aStationTurnedOffHandsThePanToFarmersDelight(GameTestHelper helper) {
        SkilletTests.aStationTurnedOffHandsThePanToFarmersDelight(helper);
    }

    @GameTest(maxTicks = 100)
    public void theFoodComesBackAndOnlyFoodGoesIn(GameTestHelper helper) {
        SkilletTests.theFoodComesBackAndOnlyFoodGoesIn(helper);
    }

    @GameTest(maxTicks = 100)
    public void moreFoodTakesLongerButKeepsWhatWasDone(GameTestHelper helper) {
        SkilletTests.moreFoodTakesLongerButKeepsWhatWasDone(helper);
    }

    @GameTest(maxTicks = 100)
    public void theFryingTimeFollowsTheConfig(GameTestHelper helper) {
        SkilletTests.theFryingTimeFollowsTheConfig(helper);
    }
}
