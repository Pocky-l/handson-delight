package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.gametest.KegTests;

/**
 * {@link KegTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}, and pass at once without Brewin' and
 * Chewin' ({@code -Paddons} puts it in).
 */
public final class FabricKegTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void waterPoursInAndTheBucketComesBack(GameTestHelper helper) {
        KegTests.waterPoursInAndTheBucketComesBack(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void beerFermentsAndIsDrawnAtTheTap(GameTestHelper helper) {
        KegTests.beerFermentsAndIsDrawnAtTheTap(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aStandingKegIsAStationOfItsOwn(GameTestHelper helper) {
        KegTests.aStandingKegIsAStationOfItsOwn(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void vodkaFermentsFromWaterAndPotatoes(GameTestHelper helper) {
        KegTests.vodkaFermentsFromWaterAndPotatoes(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void meadFermentsFromHoneyBottles(GameTestHelper helper) {
        KegTests.meadFermentsFromHoneyBottles(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void saltyFollyWantsACoolKeg(GameTestHelper helper) {
        KegTests.saltyFollyWantsACoolKeg(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void jerkyDriesInAWarmKeg(GameTestHelper helper) {
        KegTests.jerkyDriesInAWarmKeg(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
    public void theLidKeepsThingsOutAndTheBrewWaitsUnderIt(GameTestHelper helper) {
        KegTests.theLidKeepsThingsOutAndTheBrewWaitsUnderIt(helper);
    }
}
