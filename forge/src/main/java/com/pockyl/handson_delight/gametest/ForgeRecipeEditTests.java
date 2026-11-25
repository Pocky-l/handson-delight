package com.pockyl.handson_delight.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.pockyl.handson_delight.HandsOnDelight;

/** {@link RecipeEditTests} on Forge. */
@GameTestHolder(HandsOnDelight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForgeRecipeEditTests {
    private ForgeRecipeEditTests() {
    }

    @GameTest(template = "empty")
    public static void everyDishIsTheServersRecipe(GameTestHelper helper) {
        RecipeEditTests.everyDishIsTheServersRecipe(helper);
    }

    @GameTest(template = "empty")
    public static void aScriptsEditsReachTheBoard(GameTestHelper helper) {
        RecipeEditTests.aScriptsEditsReachTheBoard(helper);
    }

    @GameTest(template = "empty")
    public static void aScriptsEditsReachEveryStation(GameTestHelper helper) {
        RecipeEditTests.aScriptsEditsReachEveryStation(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 1200, batch = "reload_scripts")
    public static void aReloadKeepsTheScriptsEdits(GameTestHelper helper) {
        RecipeEditTests.aReloadKeepsTheScriptsEdits(helper);
    }

    @GameTest(template = "empty")
    public static void aDatapacksEditsOfPamsCraftsReachTheBoard(GameTestHelper helper) {
        RecipeEditTests.aDatapacksEditsOfPamsCraftsReachTheBoard(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 1200, batch = "reload")
    public static void aReloadSendsCulinaryCompatsDishes(GameTestHelper helper) {
        RecipeEditTests.aReloadSendsCulinaryCompatsDishes(helper);
    }
}
