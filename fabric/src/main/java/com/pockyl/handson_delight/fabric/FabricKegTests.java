package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.gametest.KegTests;

/**
 * {@link KegTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}, and pass at once without Brewin' and
 * Chewin' ({@code -Paddons} puts it in).
 */
public final class FabricKegTests {
    @GameTest(maxTicks = 100)
    public void waterPoursInAndTheBucketComesBack(GameTestHelper helper) {
        KegTests.waterPoursInAndTheBucketComesBack(helper);
    }

    @GameTest(maxTicks = 100)
    public void beerFermentsAndIsDrawnAtTheTap(GameTestHelper helper) {
        KegTests.beerFermentsAndIsDrawnAtTheTap(helper);
    }

    @GameTest(maxTicks = 100)
    public void aStandingKegIsAStationOfItsOwn(GameTestHelper helper) {
        KegTests.aStandingKegIsAStationOfItsOwn(helper);
    }

    @GameTest(maxTicks = 100)
    public void vodkaFermentsFromWaterAndPotatoes(GameTestHelper helper) {
        KegTests.vodkaFermentsFromWaterAndPotatoes(helper);
    }

    @GameTest(maxTicks = 100)
    public void meadFermentsFromHoneyBottles(GameTestHelper helper) {
        KegTests.meadFermentsFromHoneyBottles(helper);
    }

    @GameTest(maxTicks = 100)
    public void saltyFollyWantsACoolKeg(GameTestHelper helper) {
        KegTests.saltyFollyWantsACoolKeg(helper);
    }

    @GameTest(maxTicks = 100)
    public void jerkyFromThreeBeefThrownIn(GameTestHelper helper) {
        KegTests.jerkyFromThreeBeefThrownIn(helper);
    }

    @GameTest(maxTicks = 100)
    public void pickledPicklesFromFoodThrownInBeforeTheHoney(GameTestHelper helper) {
        KegTests.pickledPicklesFromFoodThrownInBeforeTheHoney(helper);
    }

    @GameTest(maxTicks = 100)
    public void everyKegRecipeFromItsFoodThrownIn(GameTestHelper helper) {
        KegTests.everyKegRecipeFromItsFoodThrownIn(helper);
    }

    @GameTest(maxTicks = 100)
    public void jerkyDriesInAWarmKeg(GameTestHelper helper) {
        KegTests.jerkyDriesInAWarmKeg(helper);
    }

    @GameTest(maxTicks = 120)
    public void theLidKeepsThingsOutAndTheBrewWaitsUnderIt(GameTestHelper helper) {
        KegTests.theLidKeepsThingsOutAndTheBrewWaitsUnderIt(helper);
    }
}
