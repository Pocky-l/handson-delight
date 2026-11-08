package com.pockyl.handson_delight.station;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.mixin.RecipeManagerAccessor;
import com.pockyl.handson_delight.platform.Platform;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Farmer's Delight's cutting recipes, read for the board's station: what an item is cut with and into how many strokes. */
public final class Cutting {
    /** Every cut is two chops (owner: "two cuts in different places"), at marks the server places anew for every item, one after the other. */
    public static final int CHOPS = 2;
    /**
     * Per recipe manager: the client's and the server's of a single-player game are two. Read again when the manager's map of
     * recipes is a new one (a reload, a server's recipes): looked up every frame at the board, so never built anew in between.
     */
    private static final Map<RecipeManager, Cached> CACHE = new WeakHashMap<>();

    private Cutting() {
    }

    /** Every cutting recipe, Farmer's Delight's and its addons', wood and flowers too. */
    public static List<CuttingBoardRecipe> everything(Level level) {
        return cached(level).everything();
    }

    /**
     * The cutting recipes that are cooking, the board's station: food goes in or comes out, or what comes out goes into the pot
     * (rice, mushrooms, pasta). Wood, flowers, baskets and the rest stay with Farmer's Delight's own board.
     */
    public static List<CuttingBoardRecipe> all(Level level) {
        return cached(level).cooking();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Cached cached(Level level) {
        RecipeManager manager = level.getRecipeManager();
        Map<?, ?> recipes = ((RecipeManagerAccessor) manager).handson$byName();
        synchronized (CACHE) {
            Cached cached = CACHE.get(manager);
            if (cached == null || cached.from() != recipes) {
                List<CuttingBoardRecipe> everything = new ArrayList<>();
                Map<CuttingBoardRecipe, ResourceLocation> ids = new IdentityHashMap<>();
                List<RecipeHolder<?>> found = manager.getAllRecipesFor((RecipeType) FarmersDelightContent.CUTTING.get());
                for (RecipeHolder<?> holder : found) {
                    if (holder.value() instanceof CuttingBoardRecipe cutting && !cutting.getIngredients().isEmpty()) {
                        everything.add(cutting);
                        ids.put(cutting, holder.id());
                    }
                }
                List<CookingPotRecipe> pot = Platform.INSTANCE.cookingRecipes(level).stream().map(RecipeHolder::value).toList();
                cached = new Cached(recipes, List.copyOf(everything), everything.stream().filter(recipe -> isCooking(recipe, pot)).toList(), ids);
                CACHE.put(manager, cached);
            }
            return cached;
        }
    }

    private static boolean isCooking(CuttingBoardRecipe recipe, List<CookingPotRecipe> pot) {
        for (ItemStack input : recipe.getIngredients().get(0).getItems()) {
            if (input.has(DataComponents.FOOD)) {
                return true;
            }
        }
        for (ItemStack result : recipe.getResults()) {
            if (result.has(DataComponents.FOOD) || pot.stream().anyMatch(cooking -> cooking.getIngredients().stream().anyMatch(in -> in.test(result)))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the board's own use should take {@code item}: Farmer's Delight cuts it, and it is no cooking. */
    public static boolean isOthers(Level level, ItemStack item) {
        return !item.isEmpty() && recipesFor(level, item).isEmpty()
                && everything(level).stream().anyMatch(recipe -> recipe.getIngredients().get(0).test(item));
    }

    /** The cutting recipes read from a recipe manager's map of recipes, as long as the map is the same one. */
    private record Cached(Map<?, ?> from, List<CuttingBoardRecipe> everything, List<CuttingBoardRecipe> cooking,
                          Map<CuttingBoardRecipe, ResourceLocation> ids) {
    }

    /** The id {@code recipe} is known by, now that a recipe holds none of its own. */
    public static ResourceLocation id(Level level, CuttingBoardRecipe recipe) {
        return cached(level).ids().get(recipe);
    }

    public static List<CuttingBoardRecipe> recipesFor(Level level, ItemStack item) {
        List<CuttingBoardRecipe> found = new ArrayList<>();
        if (!item.isEmpty()) {
            all(level).stream().filter(recipe -> recipe.getIngredients().get(0).test(item)).forEach(found::add);
        }
        return found;
    }

    /** The recipe for {@code item} that {@code tool} cuts it by. */
    public static Optional<CuttingBoardRecipe> findWith(Level level, ItemStack item, ItemStack tool) {
        return tool.isEmpty() ? Optional.empty() : recipesFor(level, item).stream().filter(recipe -> recipe.getTool().test(tool)).findFirst();
    }
}
