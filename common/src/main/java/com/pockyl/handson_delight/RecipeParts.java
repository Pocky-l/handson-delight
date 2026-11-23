package com.pockyl.handson_delight;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import com.mojang.datafixers.util.Either;
import com.pockyl.handson_delight.mixin.ShapedRecipeAccessor;
import com.pockyl.handson_delight.mixin.ShapelessRecipeAccessor;
import com.pockyl.handson_delight.mixin.SingleItemRecipeAccessor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * What a recipe, an ingredient or an item said of itself before Minecraft 1.21.2, which the stations still ask: a recipe's
 * ingredients and result, the stacks an ingredient takes, an item's crafting remainder. Since then each kind of recipe keeps its own
 * parts, and these ask each the way it knows.
 */
public final class RecipeParts {
    /**
     * An ingredient that takes nothing, where {@code Ingredient.EMPTY} stood: a gap. Since Minecraft 1.21.2 an ingredient of no listed
     * items cannot be made, while one of an empty tag can; so this is one of a tag of no items, told from a real empty tag by being
     * this very object.
     */
    public static final Ingredient NOTHING = emptyTag(TagKey.create(Registries.ITEM, HandsOnDelight.id("nothing")));

    /** The names another mod's recipe still gives its result by, tried in this order. */
    private static final List<String> RESULT_NAMES = List.of("getResultItem", "getOutput");
    /** Per recipe class: its own method that gives its result, or null. */
    private static final ClassValue<Method> RESULT_METHODS = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            for (String name : RESULT_NAMES) {
                try {
                    Method method = type.getMethod(name);
                    Class<?> returns = method.getReturnType();
                    if (returns == ItemStack.class) {
                        return method;
                    }
                } catch (NoSuchMethodException | RuntimeException e) {
                    // Not this name: try the next.
                }
            }
            return null;
        }
    };

    private RecipeParts() {
    }

    /** Whether {@code ingredient} is a gap ({@link #NOTHING}), not a part of the recipe: unlike an empty tag, which nobody can fill. */
    public static boolean isGap(Ingredient ingredient) {
        return ingredient == NOTHING;
    }

    /** An ingredient of {@code tag} as a tag that names no items reads: it takes nothing. */
    public static Ingredient emptyTag(TagKey<Item> tag) {
        return Ingredient.of(new HolderSet.ListBacked<Item>() {
            @Override
            protected List<Holder<Item>> contents() {
                return List.of();
            }

            @Override
            public boolean isBound() {
                return true;
            }

            @Override
            public Either<TagKey<Item>, List<Holder<Item>>> unwrap() {
                return Either.left(tag);
            }

            @Override
            public Optional<TagKey<Item>> unwrapKey() {
                return Optional.of(tag);
            }

            @Override
            public boolean contains(Holder<Item> value) {
                return false;
            }
        });
    }

    /** A stack of each item {@code ingredient} takes, as {@code Ingredient.getItems()} gave them. */
    public static ItemStack[] items(Ingredient ingredient) {
        return ingredient.items().map(ItemStack::new).toArray(ItemStack[]::new);
    }

    /**
     * The recipe's ingredients, as {@code Recipe.getIngredients()} listed them, without a shaped craft's blank places (an ingredient
     * is never empty now): a shaped craft's row by row, a single-input recipe's one.
     */
    public static List<Ingredient> ingredients(Recipe<?> recipe) {
        return switch (recipe) {
            case CookingPotRecipe cooking -> cooking.input();
            case CuttingBoardRecipe cutting -> List.of(cutting.getInput());
            case SingleItemRecipe single -> List.of(single.input());
            case ShapedRecipe shaped -> shaped.getIngredients().stream().flatMap(Optional::stream).toList();
            case ShapelessRecipe shapeless -> ((ShapelessRecipeAccessor) shapeless).handson$ingredients();
            default -> recipe.placementInfo().ingredients();
        };
    }

    /**
     * What the recipe makes without knowing what it is made of, as {@code getResultItem} said: empty for a recipe that makes nothing
     * set (a special craft), and for a cutting recipe its first result. Another mod's recipe is asked as it still answers
     * ({@link #ownResult}).
     */
    public static ItemStack result(Recipe<?> recipe) {
        ItemStack result = switch (recipe) {
            case CookingPotRecipe cooking -> cooking.result();
            case CuttingBoardRecipe cutting -> cutting.getResults().isEmpty() ? null : cutting.getResults().getFirst();
            case SingleItemRecipe single -> ((SingleItemRecipeAccessor) single).handson$result();
            case ShapedRecipe shaped -> ((ShapedRecipeAccessor) shaped).handson$result();
            case ShapelessRecipe shapeless -> ((ShapelessRecipeAccessor) shapeless).handson$result();
            default -> null;
        };
        return result != null ? result.copy() : ownResult(recipe);
    }

    /**
     * The result of a recipe of another mod. Minecraft's recipes no longer say what they make, but the ports of the addons kept a
     * method that does (Cultural Delights' oven and vat: {@code getResultItem()} with no arguments, {@code getOutput()}); without one,
     * empty.
     */
    private static ItemStack ownResult(Recipe<?> recipe) {
        Method method = RESULT_METHODS.get(recipe.getClass());
        if (method == null) {
            return ItemStack.EMPTY;
        }
        try {
            return switch (method.invoke(recipe)) {
                case ItemStack stack -> stack;
                case null, default -> ItemStack.EMPTY;
            };
        } catch (ReflectiveOperationException | RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    /** What a cutting recipe gives, each result as a stack, before its chances are rolled (as {@code getResults} gave them). */
    public static List<ItemStack> results(CuttingBoardRecipe recipe) {
        return recipe.getResults().stream().map(ItemStack::copy).toList();
    }

    /**
     * What a pot's dish is served in (a bowl), as {@code getOutputContainer} said: the recipe's own, or what its dish leaves when eaten;
     * empty for none.
     */
    public static ItemStack container(CookingPotRecipe recipe) {
        ItemStack container = recipe.container();
        return container == null ? ItemStack.EMPTY : container.copy();
    }

    /** What stays of {@code item} in the grid when it is crafted with (a bucket of milk's bucket); null when nothing does. */
    public static @Nullable Item remainder(Item item) {
        ItemStack remainder = item.getCraftingRemainder();
        return remainder.isEmpty() ? null : remainder.getItem();
    }

    /** Whether something stays of {@code item} in the grid when it is crafted with. */
    public static boolean hasRemainder(Item item) {
        return !item.getCraftingRemainder().isEmpty();
    }
}
