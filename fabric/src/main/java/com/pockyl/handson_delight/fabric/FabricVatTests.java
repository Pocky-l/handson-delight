package com.pockyl.handson_delight.fabric;

import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.gametest.VatTests;

/**
 * {@link VatTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}, and pass at once without Cultural
 * Delights ({@code -Paddons} puts it in).
 */
public final class FabricVatTests {
    @GameTest(maxTicks = 100)
    public void everyAgingFromItsFoodDroppedIn(GameTestHelper helper) {
        VatTests.everyAgingFromItsFoodDroppedIn(helper);
    }

    @GameTest(maxTicks = 100)
    public void theVatReadsItsRecipes(GameTestHelper helper) {
        VatTests.theVatReadsItsRecipes(helper);
    }

    @GameTest(maxTicks = 100)
    public void theLidKeepsTheVatShut(GameTestHelper helper) {
        VatTests.theLidKeepsTheVatShut(helper);
    }

    @GameTest(maxTicks = 100)
    public void aNewVatStandsUpright(GameTestHelper helper) {
        VatTests.aNewVatStandsUpright(helper);
    }

    @GameTest(maxTicks = 400)
    public void beerAgesWithTheLidShutAndIsDrawnIntoABottle(GameTestHelper helper) {
        VatTests.beerAgesWithTheLidShutAndIsDrawnIntoABottle(helper);
    }

    @GameTest(maxTicks = 400)
    public void aBottleInItsSlotWorksAsBefore(GameTestHelper helper) {
        VatTests.aBottleInItsSlotWorksAsBefore(helper);
    }

    @GameTest(maxTicks = 100)
    public void aStackIsSharedOutAndBottlesStayOut(GameTestHelper helper) {
        VatTests.aStackIsSharedOutAndBottlesStayOut(helper);
    }
}
