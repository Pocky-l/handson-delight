package com.pockyl.handson_delight.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.KitchenRecipes;
import com.pockyl.handson_delight.RecipeParts;
import com.pockyl.handson_delight.assembly.AssemblyRecipes;
import com.pockyl.handson_delight.platform.Platform;

import java.util.List;
import java.util.Optional;

/**
 * A modpack edits Farmer's Delight's dishes with a script: the board makes each dish exactly as the server's recipes have it, or not at
 * all. With KubeJS ({@code -Pkubejs}) the test script replaces an ingredient and a result and adds a removed recipe anew under its id;
 * with CraftTweaker ({@code -Pcrafttweaker}) it removes a recipe after the recipe manager has loaded.
 */
public final class RecipeEditTests {
    private RecipeEditTests() {
    }

    /** Every dish of the board is a recipe the server has under its id, with the same ingredients and result. */
    public static void everyDishIsTheServersRecipe(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        DynamicOps<JsonElement> ops = access.createSerializationContext(JsonOps.INSTANCE);
        for (AssemblyRecipes.Assembly dish : AssemblyRecipes.all()) {
            Optional<RecipeHolder<?>> live = KitchenRecipes.byId(helper.getLevel(), dish.id());
            helper.assertTrue(live.isPresent(), dish.id() + " is among the server's recipes");
            Recipe<?> recipe = live.get().value();
            helper.assertTrue(dish.recipe() == recipe, dish.id() + " is made by the server's own recipe");
            helper.assertTrue(ingredients(dish.recipe(), ops).equals(ingredients(recipe, ops)), dish.id() + " takes what the server's recipe takes");
            ItemStack made = RecipeParts.result(dish.recipe());
            ItemStack server = RecipeParts.result(recipe);
            // Farmer's Delight's dough from water names no result of its own: the board knows what it makes.
            helper.assertTrue(same(made, server) && (server.isEmpty() || same(dish.result(), server)),
                    dish.id() + " makes what the server's recipe makes");
        }
        helper.succeed();
    }

    /** The test scripts' edits reach the board. */
    public static void aScriptsEditsReachTheBoard(GameTestHelper helper) {
        if (Platform.INSTANCE.isLoaded("kubejs")) {
            AssemblyRecipes.Assembly bacon = dish(helper, "bacon_sandwich");
            helper.assertTrue(takes(bacon, Items.APPLE), "an ingredient replaced by a script is replaced on the board");
            AssemblyRecipes.Assembly egg = dish(helper, "egg_sandwich");
            helper.assertTrue(egg.result().is(fd("bacon_sandwich")), "a result replaced by a script is replaced on the board");
            AssemblyRecipes.Assembly chicken = dish(helper, "chicken_sandwich");
            helper.assertTrue(chicken.ingredients().size() == 3 && takes(chicken, Items.APPLE) && takes(chicken, Items.COOKED_CHICKEN),
                    "a recipe removed and added anew under its id is made as added");
        }
        if (Platform.INSTANCE.isLoaded("crafttweaker")) {
            Identifier chicken = FarmersDelightContent.id("chicken_sandwich");
            helper.assertTrue(KitchenRecipes.byId(helper.getLevel(), chicken).isEmpty(), "the script removed the recipe");
            helper.assertTrue(!AssemblyRecipes.isMadeOnTheBoard(chicken), "and the board does not make it");
        }
        helper.succeed();
    }

    private static AssemblyRecipes.Assembly dish(GameTestHelper helper, String path) {
        Identifier id = FarmersDelightContent.id(path);
        Optional<AssemblyRecipes.Assembly> dish = AssemblyRecipes.all().stream().filter(it -> it.id().equals(id)).findFirst();
        helper.assertTrue(dish.isPresent(), id + " is made on the board");
        return dish.get();
    }

    private static Item fd(String path) {
        return BuiltInRegistries.ITEM.getValue(FarmersDelightContent.id(path));
    }

    private static boolean same(ItemStack one, ItemStack other) {
        return ItemStack.isSameItemSameComponents(one, other) && one.getCount() == other.getCount();
    }

    private static boolean takes(AssemblyRecipes.Assembly dish, Item item) {
        return dish.ingredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(item)));
    }

    private static List<String> ingredients(Recipe<?> recipe, DynamicOps<JsonElement> ops) {
        return RecipeParts.ingredients(recipe).stream()
                .map(ingredient -> Ingredient.CODEC.encodeStart(ops, ingredient).getOrThrow(JsonParseException::new).toString()).toList();
    }
}
