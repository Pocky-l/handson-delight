package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.gametest.SmallStoveTests;
import com.pockyl.handson_delight.gametest.StoveTests;

/** {@link StoveTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}. */
public final class FabricStoveTests {
    @GameTest(maxTicks = 460)
    public void aPieceGrillsInHalvesAndWaitsToBeTurned(GameTestHelper helper) {
        StoveTests.aPieceGrillsInHalvesAndWaitsToBeTurned(helper);
    }

    @GameTest(maxTicks = 100)
    public void farmersDelightsWayStaysItsOwn(GameTestHelper helper) {
        StoveTests.farmersDelightsWayStaysItsOwn(helper);
    }

    @GameTest(maxTicks = 500)
    public void piecesLaidByHandGoFarmersDelightsWay(GameTestHelper helper) {
        StoveTests.piecesLaidByHandGoFarmersDelightsWay(helper);
    }

    @GameTest(maxTicks = 100)
    public void onlyFoodGoesOnAnOpenGrill(GameTestHelper helper) {
        StoveTests.onlyFoodGoesOnAnOpenGrill(helper);
    }

    @GameTest(maxTicks = 100)
    public void anEggStaysOffTheGrill(GameTestHelper helper) {
        StoveTests.anEggStaysOffTheGrill(helper);
    }

    @GameTest(maxTicks = 100)
    public void everyCampfireFoodGoesOnTheGrill(GameTestHelper helper) {
        StoveTests.everyCampfireFoodGoesOnTheGrill(helper);
    }

    @GameTest(maxTicks = 100)
    public void everyStoveOfTheTagIsAStation(GameTestHelper helper) {
        StoveTests.everyStoveOfTheTagIsAStation(helper);
    }

    @GameTest(maxTicks = 100)
    public void kitchenThingsOpenTheStove(GameTestHelper helper) {
        StoveTests.kitchenThingsOpenTheStove(helper);
    }

    @GameTest(maxTicks = 100)
    public void theGrillingTimeFollowsTheConfig(GameTestHelper helper) {
        StoveTests.theGrillingTimeFollowsTheConfig(helper);
    }

    @GameTest(maxTicks = 100)
    public void aStoveWithFewerPlacesGrillsOnThoseItHas(GameTestHelper helper) {
        SmallStoveTests.aStoveWithFewerPlacesGrillsOnThoseItHas(helper);
    }
}
