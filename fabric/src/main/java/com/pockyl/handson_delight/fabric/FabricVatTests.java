package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.gametest.VatTests;

/**
 * {@link VatTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}, and pass at once without Cultural
 * Delights ({@code -Paddons} puts it in).
 */
public final class FabricVatTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyAgingFromItsFoodDroppedIn(GameTestHelper helper) {
        VatTests.everyAgingFromItsFoodDroppedIn(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theVatReadsItsRecipes(GameTestHelper helper) {
        VatTests.theVatReadsItsRecipes(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theLidKeepsTheVatShut(GameTestHelper helper) {
        VatTests.theLidKeepsTheVatShut(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aNewVatStandsUpright(GameTestHelper helper) {
        VatTests.aNewVatStandsUpright(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void beerAgesWithTheLidShutAndIsDrawnIntoABottle(GameTestHelper helper) {
        VatTests.beerAgesWithTheLidShutAndIsDrawnIntoABottle(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void aBottleInItsSlotWorksAsBefore(GameTestHelper helper) {
        VatTests.aBottleInItsSlotWorksAsBefore(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aStackIsSharedOutAndBottlesStayOut(GameTestHelper helper) {
        VatTests.aStackIsSharedOutAndBottlesStayOut(helper);
    }
}
