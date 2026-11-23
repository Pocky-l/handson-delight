package com.pockyl.handson_delight.fabric;

import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.gametest.RecipeEditTests;

/** {@link RecipeEditTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}. */
public final class FabricRecipeEditTests {
    @GameTest(maxTicks = 100)
    public void everyDishIsTheServersRecipe(GameTestHelper helper) {
        RecipeEditTests.everyDishIsTheServersRecipe(helper);
    }

    @GameTest(maxTicks = 100)
    public void aScriptsEditsReachTheBoard(GameTestHelper helper) {
        RecipeEditTests.aScriptsEditsReachTheBoard(helper);
    }
}
