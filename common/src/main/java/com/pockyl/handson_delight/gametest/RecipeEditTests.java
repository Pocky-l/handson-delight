package com.pockyl.handson_delight.gametest;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.assembly.AssemblyRecipes;
import com.pockyl.handson_delight.assembly.BoardDishes;
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
        for (AssemblyRecipes.Assembly dish : AssemblyRecipes.all()) {
            Optional<? extends Recipe<?>> live = manager.byKey(dish.id());
            helper.assertTrue(live.isPresent(), dish.id() + " is among the server's recipes");
            if (dish.tool() != null) {
                continue;
            }
            Recipe<?> recipe = live.get();
            helper.assertTrue(dish.recipe() == recipe, dish.id() + " is made by the server's own recipe");
            helper.assertTrue(ingredients(dish.recipe()).equals(ingredients(recipe)), dish.id() + " takes what the server's recipe takes");
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
            if (culinary()) {
                Optional<AssemblyRecipes.Assembly> caramel = pam("caramelyogurtitem");
                helper.assertTrue(caramel.isPresent() && takes(caramel.get(), Items.HONEY_BOTTLE),
                        "a Pam craft a script changed is cut together as changed: " + caramel.map(AssemblyRecipes.Assembly::json).orElse(null));
            }
        }
        if (Platform.INSTANCE.isLoaded("crafttweaker")) {
            ResourceLocation chicken = FarmersDelightContent.id("chicken_sandwich");
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(chicken).isEmpty(), "the script removed the recipe");
            helper.assertTrue(!AssemblyRecipes.isMadeOnTheBoard(chicken), "and the board does not make it");
            if (culinary()) {
                ResourceLocation chocolate = new ResourceLocation(PAM_CUT + "chocolateyogurtitem");
                boolean server = helper.getLevel().getRecipeManager().byKey(chocolate).isPresent();
                // Culinary Compat reads Pam's crafts after CraftTweaker's scripts ran (seen 2026-11-17): its cut is gone as well.
                helper.assertTrue(!server && pam("chocolateyogurtitem").isEmpty(), "a Pam craft a script removed is not cut on the board, "
                        + "nor is Culinary Compat's cut of it on the server: " + server);
            }
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
        RecipeManager before = server.getRecipeManager();
        Recipe<?> oldCut = before.byKey(new ResourceLocation("kubejs:apple_sticks")).orElse(null);
        CompletableFuture<Void> reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        helper.succeedWhen(() -> {
            helper.assertTrue(reload.isDone(), "the reload is done");
            scriptsEdits(helper);
            Recipe<?> newCut = server.getRecipeManager().byKey(new ResourceLocation("kubejs:apple_sticks")).orElse(null);
            helper.assertTrue(newCut != null && newCut != oldCut, "the reload made the script's cut anew");
            helper.assertTrue(Cutting.all(helper.getLevel()).contains(newCut) && !Cutting.all(helper.getLevel()).contains(oldCut),
                    "the board cuts by the recipes after the reload");
            everyDishIsTheServersRecipe(helper);
        });
    }

    private static void scriptsEdits(GameTestHelper helper) {
        Level level = helper.getLevel();
        ResourceLocation soup = FarmersDelightContent.id("cooking/beetroot_soup");
        helper.assertTrue(Platform.INSTANCE.cookingRecipes(level).stream().noneMatch(recipe -> recipe.getId().equals(soup)),
                "a pot meal a script removed is not in the pot's book");
        ItemStack beetroot = new ItemStack(Items.BEETROOT);
        helper.assertTrue(Platform.INSTANCE.findRecipe(level, List.of(beetroot, beetroot.copy(), beetroot.copy())).isEmpty(),
                "and the pot does not cook it");
        ResourceLocation honey = new ResourceLocation("kubejs:apple_honey");
        helper.assertTrue(Platform.INSTANCE.cookingRecipes(level).stream().anyMatch(recipe -> recipe.getId().equals(honey)),
                "a pot meal a script added is in the pot's book");
        helper.assertTrue(Platform.INSTANCE.findRecipe(level, List.of(new ItemStack(Items.APPLE), new ItemStack(Items.SUGAR)))
                .map(recipe -> recipe.getId().equals(honey)).orElse(false), "and the pot cooks it");

        ResourceLocation allium = FarmersDelightContent.id("cutting/allium");
        helper.assertTrue(Cutting.all(level).stream().noneMatch(recipe -> recipe.getId().equals(allium)),
                "a cut a script removed is not in the board's book");
        helper.assertTrue(Cutting.recipesFor(level, new ItemStack(Items.ALLIUM)).stream().noneMatch(recipe -> recipe.getId().equals(allium)),
                "and the board does not cut it");
        ResourceLocation sticks = new ResourceLocation("kubejs:apple_sticks");
        helper.assertTrue(Cutting.all(level).stream().anyMatch(recipe -> recipe.getId().equals(sticks)),
                "a cut a script added is in the board's book");
        helper.assertTrue(Cutting.recipesFor(level, new ItemStack(Items.APPLE)).stream().anyMatch(recipe -> recipe.getId().equals(sticks)),
                "and the board cuts it");

        // Its twin stays: it shows that such a dish is the board's when no script takes it out.
        helper.assertTrue(AssemblyRecipes.isMadeOnTheBoard(FarmersDelightContent.id("mixed_salad")), "a tossed salad is made on the board");
        ResourceLocation pie = FarmersDelightContent.id("fruit_salad");
        helper.assertTrue(AssemblyRecipes.all().stream().noneMatch(dish -> dish.id().equals(pie)) && !AssemblyRecipes.isMadeOnTheBoard(pie),
                "a board dish a script removed is not made on the board");
        helper.assertTrue(BoardDishes.outgoing().stream().noneMatch(tag -> ((CompoundTag) tag).getString("id").equals(pie.toString())),
                "nor sent to the viewers' books");

        helper.assertTrue(SkilletCooking.recipe(level, new ItemStack(fd("cod_slice"))).isEmpty(),
                "a campfire recipe a script removed is not fried in the skillet");
    }

    /**
     * A datapack's edits of Pam's crafts reach the board through Culinary Compat ({@code -Pculinary} writes the pack): its apple sauce
     * takes a honey bottle for the sugar, its apple yogurt is taken out by a false condition.
     */
    public static void aDatapacksEditsOfPamsCraftsReachTheBoard(GameTestHelper helper) {
        if (!culinary()) {
            helper.succeed();
            return;
        }
        helper.assertTrue(helper.getLevel().getServer().getPackRepository().getSelectedIds().contains("file/handson_culinary_test"),
                "the test datapack is on: " + helper.getLevel().getServer().getPackRepository().getSelectedIds());
        Optional<AssemblyRecipes.Assembly> sauce = pam("applesauceitem");
        helper.assertTrue(sauce.isPresent() && takes(sauce.get(), Items.HONEY_BOTTLE) && !takes(sauce.get(), Items.SUGAR),
                "the apple sauce is cut together as the datapack has it: " + sauce.map(AssemblyRecipes.Assembly::json).orElse(null));
        helper.assertTrue(pam("appleyogurtitem").isEmpty(), "the apple yogurt the datapack took out is not made on the board");
        helper.succeed();
    }

    /**
     * A {@code /reload} sends the viewers the dishes Culinary Compat adds anew at the datapack sync, not the list from before it, so
     * JEI shows them without a rejoin. Runs in a batch of its own: the reload swaps every recipe.
     */
    public static void aReloadSendsCulinaryCompatsDishes(GameTestHelper helper) {
        if (!culinary()) {
            helper.succeed();
            return;
        }
        MinecraftServer server = helper.getLevel().getServer();
        CompletableFuture<Void> reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        helper.succeedWhen(() -> {
            helper.assertTrue(reload.isDone(), "the reload is done");
            long cut = BoardDishes.outgoing().stream().filter(tag -> ((CompoundTag) tag).getString("id").startsWith("culinarycompat:"))
                    .count();
            helper.assertTrue(cut > 0, "the dishes sent after the reload have Culinary Compat's: " + BoardDishes.outgoing().size() + " sent");
            helper.assertTrue(cut == AssemblyRecipes.all().stream().filter(dish -> dish.id().getNamespace().equals("culinarycompat")).count(),
                    "all of them");
        });
    }

    private static final String PAM_CUT = "culinarycompat:pam_multi_cutting/pamhc2foodcore_";

    private static boolean culinary() {
        return Platform.INSTANCE.isLoaded("culinarycompat") && Platform.INSTANCE.isLoaded("pamhc2foodcore");
    }

    private static Optional<AssemblyRecipes.Assembly> pam(String item) {
        ResourceLocation id = new ResourceLocation(PAM_CUT + item);
        return AssemblyRecipes.all().stream().filter(dish -> dish.id().equals(id)).findFirst();
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
        return ItemStack.isSameItemSameTags(one, other) && one.getCount() == other.getCount();
    }

    private static boolean takes(AssemblyRecipes.Assembly dish, Item item) {
        return dish.ingredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(item)));
    }

    private static List<String> ingredients(Recipe<?> recipe) {
        return recipe.getIngredients().stream().map(Ingredient::toJson).map(Object::toString).toList();
    }
}
