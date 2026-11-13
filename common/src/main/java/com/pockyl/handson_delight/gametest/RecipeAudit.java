package com.pockyl.handson_delight.gametest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.KitchenTags;
import com.pockyl.handson_delight.assembly.AssemblyRecipes;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.Cutting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Every recipe of Farmer's Delight and its addons against the stations: whether each one reaches its station, and how the station reads
 * it. Writes {@code recipe-audit.txt} into the game directory for review, with addons or without, and fails when a dish of the crafting
 * table is not put together on the board.
 */
public final class RecipeAudit {
    /** Names that suggest a kitchen tag the item lacks: worth a look, never a failure. */
    private static final Pattern POURED = Pattern.compile("milk|water|(^|_)honey$|(^|_)oil$|juice|sauce|syrup|broth|(?<!ice|magma)_cream$|vinegar|"
            + "wine|(^|_)tea$|coffee$|latte|cider|jam|_bucket$|(?<!glass)_bottle$|_jar$");
    private static final Pattern CRACKED = Pattern.compile("(^|_)eggs?($|_)");
    private static final Pattern COOKED = Pattern.compile("fried|boiled|cooked|scrambled|smoked|baked|roasted");
    private static final Pattern DISSOLVES = Pattern.compile("sugar|salt|powder|dust|spice|cinnamon|cocoa|flour|seasoning|ground");
    private static final Pattern GRAINS = Pattern.compile("rice|grain|oat|barley|kernel|seeds|(^|_)corn$");
    private static final Pattern WEDGES = Pattern.compile("pie|cake|tart|cheese_wheel|wheel_of|melon$|pumpkin$|pizza|quiche");
    private static final Pattern STRAIGHT = Pattern.compile("roll$|loaf|sausage|baguette|log$");

    private RecipeAudit() {
    }

    public static void everyRecipeReachesItsStation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> report = new ArrayList<>();
        List<String> missed = new ArrayList<>();
        List<String> ownCrafts = new ArrayList<>();
        Map<String, Integer> others = new TreeMap<>();

        List<AssemblyRecipes.Assembly> assembled = new ArrayList<>(AssemblyRecipes.all());
        Set<ResourceLocation> onTheBoard = assembled.stream().map(AssemblyRecipes.Assembly::id).collect(Collectors.toSet());
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            String namespace = holder.id().getNamespace();
            boolean ours = namespace.equals(FarmersDelightContent.MOD_ID) || Platform.INSTANCE.isFarmersDelightAddon(namespace);
            ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            if (recipe instanceof CraftingRecipe crafting && ours && !onTheBoard.contains(holder.id())
                    && isDish(crafting.getResultItem(level.registryAccess())) && kinds(crafting.getIngredients()) > 1
                    && !crafting.isSpecial() && !AssemblyRecipes.usesWorkshop(crafting.getIngredients())) {
                ResourceLocation id = holder.id();
                ResourceLocation serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
                if (serializer != null && !serializer.getNamespace().equals("minecraft")
                        && !serializer.getNamespace().equals(FarmersDelightContent.MOD_ID)
                        && !(crafting instanceof ShapedRecipe) && !(crafting instanceof ShapelessRecipe)) {
                    // A crafting type of the addon's own that matches in a way of its own: it stays in the crafting table. One built
                    // on the shaped or shapeless craft is made on the board (Barbeque's Delight's kebabs, seasoning and all).
                    ownCrafts.add(id + " -> " + name(crafting.getResultItem(level.registryAccess())) + " (" + serializer + ")");
                    continue;
                }
                String why = AssemblyRecipes.candidates().stream().anyMatch(candidate -> candidate.id().equals(id))
                        ? "a candidate, not chosen" : "not a candidate";
                missed.add(id + " -> " + name(crafting.getResultItem(level.registryAccess())) + " (" + why + ", "
                        + recipe.getClass().getSimpleName() + ")");
            }
            if (type != null && !type.getNamespace().equals("minecraft") && !type.getNamespace().equals(FarmersDelightContent.MOD_ID)) {
                others.merge(type.toString(), 1, Integer::sum);
            }
        }

        section(report, "ASSEMBLY ON THE BOARD (" + assembled.size() + ")", "id | result | base: kind | finish | ingredients | look at");
        assembled.sort(Comparator.comparing(assembly -> assembly.id().toString()));
        for (AssemblyRecipes.Assembly assembly : assembled) {
            List<String> look = new ArrayList<>();
            if (assembly.kind() == AssemblyRecipes.Base.FIRST) {
                look.add("no known base");
            }
            if (assembly.finish() == AssemblyRecipes.Finish.PRESS && !assembly.isBlock()) {
                look.add("pressed");
            }
            List<String> ingredients = new ArrayList<>();
            for (int i = 0; i < assembly.ingredients().size(); i++) {
                Ingredient ingredient = assembly.ingredients().get(i);
                ingredients.add(name(ingredient));
                if (ingredient.getItems().length == 0) {
                    look.add("empty ingredient " + i);
                }
                hints(ingredient, look);
            }
            report.add(assembly.id() + " | " + name(assembly.result()) + " | " + name(assembly.ingredients().get(assembly.base())) + ": "
                    + assembly.kind() + " | " + assembly.finish() + " | " + String.join(", ", ingredients) + flags(look));
        }

        List<RecipeHolder<CookingPotRecipe>> pot = new ArrayList<>(Platform.INSTANCE.cookingRecipes(level));
        pot.sort(Comparator.comparing(holder -> holder.id().toString()));
        section(report, "COOKING POT (" + pot.size() + ")", "id | result | container | ingredients | look at");
        for (RecipeHolder<CookingPotRecipe> holder : pot) {
            CookingPotRecipe recipe = holder.value();
            List<String> look = new ArrayList<>();
            recipe.getIngredients().forEach(ingredient -> hints(ingredient, look));
            if (recipe.getIngredients().size() > 6) {
                look.add("more than six ingredients");
            }
            report.add(holder.id() + " | " + name(recipe.getResultItem(level.registryAccess())) + " | " + name(recipe.getOutputContainer())
                    + " | " + recipe.getIngredients().stream().map(RecipeAudit::name).collect(Collectors.joining(", ")) + flags(look));
        }

        List<CuttingBoardRecipe> cooking = Cutting.all(level);
        List<CuttingBoardRecipe> cutting = new ArrayList<>(Cutting.everything(level));
        cutting.sort(Comparator.comparing(recipe -> String.valueOf(Cutting.id(level, recipe))));
        section(report, "CUTTING BOARD (" + cutting.size() + ", " + cooking.size() + " at the station)",
                "id | input | tool | results | station: marks style | look at");
        for (CuttingBoardRecipe recipe : cutting) {
            List<String> look = new ArrayList<>();
            Ingredient input = recipe.getIngredients().get(0);
            String station = "FD's own board";
            if (cooking.contains(recipe)) {
                ItemStack item = input.getItems().length > 0 ? input.getItems()[0] : ItemStack.EMPTY;
                Cutting.Plan plan = Cutting.plan(level, item);
                station = plan.marks() + " " + plan.style();
                String path = path(item);
                if (WEDGES.matcher(path).find() && plan.style() != Cutting.Style.WEDGES) {
                    look.add("wedges?");
                }
                if (STRAIGHT.matcher(path).find() && plan.style() == Cutting.Style.ACROSS) {
                    look.add("sliced straight?");
                }
                if (Cutting.recipesFor(level, item).size() > 1) {
                    look.add(Cutting.recipesFor(level, item).size() + " recipes for the input");
                }
            }
            report.add(Cutting.id(level, recipe) + " | " + name(input) + " | " + name(Cutting.tool(recipe)) + " | "
                    + recipe.getResults().stream().map(RecipeAudit::name).collect(Collectors.joining(", ")) + " | " + station + flags(look));
        }

        List<RecipeHolder<CampfireCookingRecipe>> campfire = new ArrayList<>(level.getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING));
        campfire.sort(Comparator.comparing(holder -> holder.id().toString()));
        section(report, "SKILLET AND STOVE (" + campfire.size() + ")", "id | input | result | time | look at");
        for (RecipeHolder<CampfireCookingRecipe> holder : campfire) {
            CampfireCookingRecipe recipe = holder.value();
            List<String> look = new ArrayList<>();
            Ingredient input = recipe.getIngredients().get(0);
            for (ItemStack item : input.getItems()) {
                if (CRACKED.matcher(path(item)).find() && !COOKED.matcher(path(item)).find() && !item.is(KitchenTags.ONE_SIDED)) {
                    look.add(name(item) + " not one_sided");
                }
            }
            report.add(holder.id() + " | " + name(input) + " | " + name(recipe.getResultItem(level.registryAccess())) + " | "
                    + recipe.getCookingTime() + flags(look));
        }

        section(report, "CRAFTING DISHES NOT ON THE BOARD (" + missed.size() + ")", "id -> result");
        missed.stream().sorted().forEach(report::add);
        section(report, "FOOD CRAFTS OF THEIR OWN TYPE, KEPT IN THE CRAFTING TABLE (" + ownCrafts.size() + ")", "id -> result (type)");
        ownCrafts.stream().sorted().forEach(report::add);
        section(report, "RECIPE TYPES OF OTHER MODS (stations of their own)", "type: recipes");
        others.forEach((type, count) -> report.add(type + ": " + count));

        try {
            Files.write(Path.of("recipe-audit.txt"), report, StandardCharsets.UTF_8);
        } catch (IOException failed) {
            helper.fail("Could not write recipe-audit.txt: " + failed);
            return;
        }
        if (!missed.isEmpty()) {
            helper.fail(missed.size() + " crafting dishes are not put together on the board, first " + missed.get(0));
            return;
        }
        helper.succeed();
    }

    /** The same rule as the board's: food, or a block of food put down whole. */
    private static boolean isDish(ItemStack result) {
        return !result.isEmpty() && !result.is(AssemblyRecipes.NOT_ASSEMBLED) && (AssemblyRecipes.isDishResult(result) || AssemblyRecipes.isCutIntoFood(result));
    }

    private static long kinds(Collection<Ingredient> ingredients) {
        return ingredients.stream().filter(ingredient -> !ingredient.isEmpty())
                .map(ingredient -> Arrays.stream(ingredient.getItems()).map(RecipeAudit::name).sorted().toList()).distinct().count();
    }

    /** The kitchen tags an ingredient's name suggests and it lacks. */
    private static void hints(Ingredient ingredient, List<String> look) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return;
        }
        ItemStack item = items[0];
        String path = path(item);
        String name = name(item);
        if (KitchenTags.is(item, KitchenTags.POURED) || KitchenTags.is(item, KitchenTags.CRACKED)
                || KitchenTags.is(item, KitchenTags.DISSOLVES) || KitchenTags.is(item, KitchenTags.STRAINED)) {
            return;
        }
        if (item.is(KitchenTags.GRAINS)) {
            return;
        }
        if (item.getItem().hasCraftingRemainingItem() && !item.has(DataComponents.FOOD) || POURED.matcher(path).find()) {
            look.add(name + " poured?");
        } else if (CRACKED.matcher(path).find() && !COOKED.matcher(path).find()) {
            look.add(name + " cracked?");
        } else if (DISSOLVES.matcher(path).find()) {
            look.add(name + " dissolves?");
        } else if (GRAINS.matcher(path).find() && !item.is(KitchenTags.GRAINS)) {
            look.add(name + " grains?");
        }
    }

    private static void section(List<String> report, String title, String columns) {
        if (!report.isEmpty()) {
            report.add("");
        }
        report.add("== " + title);
        report.add("   " + columns);
    }

    private static String flags(List<String> look) {
        return look.isEmpty() ? "" : " | " + look.stream().distinct().collect(Collectors.joining("; "));
    }

    private static String path(ItemStack item) {
        return BuiltInRegistries.ITEM.getKey(item.getItem()).getPath();
    }

    private static String name(ItemStack item) {
        if (item.isEmpty()) {
            return "-";
        }
        String id = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        return item.getCount() > 1 ? item.getCount() + " " + id : id;
    }

    private static String name(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return "<nothing>";
        }
        return items.length == 1 ? name(items[0]) : name(items[0]) + " (+" + (items.length - 1) + ")";
    }
}
