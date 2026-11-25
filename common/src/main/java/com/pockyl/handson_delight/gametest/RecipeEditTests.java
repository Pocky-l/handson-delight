package com.pockyl.handson_delight.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.assembly.AssemblyRecipes;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.Cutting;
import com.pockyl.handson_delight.station.SkilletCooking;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
        RecipeManager manager = helper.getLevel().getRecipeManager();
        RegistryAccess access = helper.getLevel().registryAccess();
        DynamicOps<JsonElement> ops = access.createSerializationContext(JsonOps.INSTANCE);
        for (AssemblyRecipes.Assembly dish : AssemblyRecipes.all()) {
            Optional<RecipeHolder<?>> live = manager.byKey(dish.id());
            helper.assertTrue(live.isPresent(), dish.id() + " is among the server's recipes");
            Recipe<?> recipe = live.get().value();
            helper.assertTrue(dish.recipe() == recipe, dish.id() + " is made by the server's own recipe");
            helper.assertTrue(ingredients(dish.recipe(), ops).equals(ingredients(recipe, ops)), dish.id() + " takes what the server's recipe takes");
            ItemStack made = dish.recipe().getResultItem(access);
            ItemStack server = recipe.getResultItem(access);
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
            ResourceLocation chicken = FarmersDelightContent.id("chicken_sandwich");
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(chicken).isEmpty(), "the script removed the recipe");
            helper.assertTrue(!AssemblyRecipes.isMadeOnTheBoard(chicken), "and the board does not make it");
        }
        helper.succeed();
    }

    /**
     * The KubeJS test script's edits of the other stations reach them and their books: a pot meal, a cut, a board dish and a campfire
     * recipe removed are made nowhere, a pot meal and a cut added are made. Every station and book reads the level's recipes.
     */
    public static void aScriptsEditsReachEveryStation(GameTestHelper helper) {
        if (Platform.INSTANCE.isLoaded("kubejs")) {
            scriptsEdits(helper);
        }
        helper.succeed();
    }

    /**
     * After a {@code /reload} KubeJS runs its scripts again: the stations still follow them and read the new recipes, not the ones from
     * before the reload. Runs in a batch of its own: the reload swaps every recipe.
     */
    public static void aReloadKeepsTheScriptsEdits(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded("kubejs")) {
            helper.succeed();
            return;
        }
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation sticks = ResourceLocation.parse("kubejs:apple_sticks");
        Recipe<?> oldCut = server.getRecipeManager().byKey(sticks).map(RecipeHolder::value).orElse(null);
        CompletableFuture<Void> reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        helper.succeedWhen(() -> {
            helper.assertTrue(reload.isDone(), "the reload is done");
            scriptsEdits(helper);
            Recipe<?> newCut = server.getRecipeManager().byKey(sticks).map(RecipeHolder::value).orElse(null);
            helper.assertTrue(newCut != null && newCut != oldCut, "the reload made the script's cut anew");
            helper.assertTrue(Cutting.all(helper.getLevel()).contains(newCut) && !Cutting.all(helper.getLevel()).contains(oldCut),
                    "the board cuts by the recipes after the reload");
            everyDishIsTheServersRecipe(helper);
        });
    }

    private static void scriptsEdits(GameTestHelper helper) {
        Level level = helper.getLevel();
        ResourceLocation soup = FarmersDelightContent.id("cooking/beetroot_soup");
        helper.assertTrue(Platform.INSTANCE.cookingRecipes(level).stream().noneMatch(recipe -> recipe.id().equals(soup)),
                "a pot meal a script removed is not in the pot's book");
        ItemStack beetroot = new ItemStack(Items.BEETROOT);
        helper.assertTrue(Platform.INSTANCE.findRecipe(level, List.of(beetroot, beetroot.copy(), beetroot.copy())).isEmpty(),
                "and the pot does not cook it");
        ResourceLocation honey = ResourceLocation.parse("kubejs:apple_honey");
        helper.assertTrue(Platform.INSTANCE.cookingRecipes(level).stream().anyMatch(recipe -> recipe.id().equals(honey)),
                "a pot meal a script added is in the pot's book");
        helper.assertTrue(Platform.INSTANCE.findRecipe(level, List.of(new ItemStack(Items.APPLE), new ItemStack(Items.SUGAR)))
                .map(recipe -> recipe.id().equals(honey)).orElse(false), "and the pot cooks it");

        ResourceLocation allium = FarmersDelightContent.id("cutting/allium");
        helper.assertTrue(Cutting.all(level).stream().noneMatch(recipe -> allium.equals(Cutting.id(level, recipe))),
                "a cut a script removed is not in the board's book");
        helper.assertTrue(Cutting.recipesFor(level, new ItemStack(Items.ALLIUM)).stream().noneMatch(recipe -> allium.equals(Cutting.id(level, recipe))),
                "and the board does not cut it");
        ResourceLocation sticks = ResourceLocation.parse("kubejs:apple_sticks");
        helper.assertTrue(Cutting.all(level).stream().anyMatch(recipe -> sticks.equals(Cutting.id(level, recipe))),
                "a cut a script added is in the board's book");
        helper.assertTrue(Cutting.recipesFor(level, new ItemStack(Items.APPLE)).stream().anyMatch(recipe -> sticks.equals(Cutting.id(level, recipe))),
                "and the board cuts it");

        // Its twin stays: it shows that such a dish is the board's when no script takes it out.
        helper.assertTrue(AssemblyRecipes.isMadeOnTheBoard(FarmersDelightContent.id("mixed_salad")), "a tossed salad is made on the board");
        ResourceLocation salad = FarmersDelightContent.id("fruit_salad");
        helper.assertTrue(AssemblyRecipes.all().stream().noneMatch(dish -> dish.id().equals(salad)) && !AssemblyRecipes.isMadeOnTheBoard(salad),
                "a board dish a script removed is not made on the board");

        helper.assertTrue(SkilletCooking.recipe(level, new ItemStack(fd("cod_slice"))).isEmpty(),
                "a campfire recipe a script removed is not fried in the skillet");
    }

    private static AssemblyRecipes.Assembly dish(GameTestHelper helper, String path) {
        ResourceLocation id = FarmersDelightContent.id(path);
        Optional<AssemblyRecipes.Assembly> dish = AssemblyRecipes.all().stream().filter(it -> it.id().equals(id)).findFirst();
        helper.assertTrue(dish.isPresent(), id + " is made on the board");
        return dish.get();
    }

    private static Item fd(String path) {
        return BuiltInRegistries.ITEM.get(FarmersDelightContent.id(path));
    }

    private static boolean same(ItemStack one, ItemStack other) {
        return ItemStack.isSameItemSameComponents(one, other) && one.getCount() == other.getCount();
    }

    private static boolean takes(AssemblyRecipes.Assembly dish, Item item) {
        return dish.ingredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(item)));
    }

    private static List<String> ingredients(Recipe<?> recipe, DynamicOps<JsonElement> ops) {
        return recipe.getIngredients().stream()
                .map(ingredient -> Ingredient.CODEC.encodeStart(ops, ingredient).getOrThrow(JsonParseException::new).toString()).toList();
    }
}
