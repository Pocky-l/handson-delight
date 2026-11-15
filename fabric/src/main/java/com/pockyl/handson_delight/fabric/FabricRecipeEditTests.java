package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.gametest.RecipeEditTests;

/** {@link RecipeEditTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}. */
public final class FabricRecipeEditTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyDishIsTheServersRecipe(GameTestHelper helper) {
        RecipeEditTests.everyDishIsTheServersRecipe(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aScriptsEditsReachTheBoard(GameTestHelper helper) {
        RecipeEditTests.aScriptsEditsReachTheBoard(helper);
    }
}
