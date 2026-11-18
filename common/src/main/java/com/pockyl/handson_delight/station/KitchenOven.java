package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.KitchenRecipes;
import com.pockyl.handson_delight.RecipeParts;
import com.pockyl.handson_delight.platform.Platform;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cultural Delights' oven, as the station sees it. Its block entity keeps an item handler of the loader's own class (NeoForge's, or
 * Refabricated's on Fabric), so it is reached by name: the same field and method names in both of its builds, no class of that mod
 * needed. Slots as its menu has them: nine for what bakes, a 3 x 3 grid, then the output. It bakes by itself, a set of one of each
 * slot at a time, while the block under it is a heat source of Farmer's Delight; the station only reads it and puts things in and out.
 */
public final class KitchenOven {
    public static final String MOD_ID = "culturaldelights";
    private static final String ENTITY = "com.baisylia.culturaldelights.block.entity.custom.OvenBlockEntity";
    public static final int INGREDIENTS = 9;
    public static final int GRID = 3;
    public static final int OUTPUT_SLOT = 9;
    /** Its two recipe types: a set of ingredients in any slots, and one laid out in the grid as at a crafting table. */
    public static final Identifier BAKING = Identifier.fromNamespaceAndPath(MOD_ID, "baking");
    public static final Identifier BAKING_SHAPED = Identifier.fromNamespaceAndPath(MOD_ID, "baking_shaped");

    private static Class<?> entityClass;
    private static Field progressField;
    private static Field totalField;
    private static Method handlerMethod;
    private static boolean broken;

    private final BlockEntity entity;
    private final Object handler;

    private KitchenOven(BlockEntity entity, Object handler) {
        this.entity = entity;
        this.handler = handler;
    }

    /** The oven of a block entity, or null for anything else. */
    public static KitchenOven of(BlockEntity entity) {
        if (entity == null || broken || !entity.getClass().getName().equals(ENTITY) || !resolve(entity.getClass())) {
            return null;
        }
        try {
            return new KitchenOven(entity, handlerMethod.invoke(entity));
        } catch (ReflectiveOperationException e) {
            fail(e);
            return null;
        }
    }

    private static synchronized boolean resolve(Class<?> type) {
        if (type == entityClass) {
            return true;
        }
        try {
            Field progress = type.getDeclaredField("progress");
            Field total = type.getDeclaredField("maxProgress");
            Method handler = type.getMethod("getItemHandler");
            progress.setAccessible(true);
            total.setAccessible(true);
            progressField = progress;
            totalField = total;
            handlerMethod = handler;
            entityClass = type;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return false;
        }
    }

    private static void fail(Exception e) {
        if (!broken) {
            broken = true;
            HandsOnDelight.LOGGER.warn("Cultural Delights' oven is not what the oven station knows; it keeps its own menu", e);
        }
    }

    public BlockEntity blockEntity() {
        return entity;
    }

    public ItemStack getItem(int slot) {
        return Platform.INSTANCE.handlerItem(handler, slot);
    }

    public void setItem(int slot, ItemStack stack) {
        Platform.INSTANCE.setHandlerItem(handler, slot, stack);
        entity.setChanged();
    }

    /** Ticks baked of what is in it, 0 while it bakes nothing. */
    public int progress() {
        return read(progressField);
    }

    public void setProgress(int ticks) {
        try {
            progressField.setInt(entity, ticks);
        } catch (IllegalAccessException e) {
            fail(e);
        }
    }

    /** Ticks what is in it takes to bake. */
    public int total() {
        return read(totalField);
    }

    private int read(Field field) {
        try {
            return field.getInt(entity);
        } catch (IllegalAccessException e) {
            fail(e);
            return 0;
        }
    }

    /** Whether the block under it heats it: the oven lights itself by that every tick. */
    public static boolean isHeated(BlockState state) {
        return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
    }

    /**
     * A baking recipe: what goes in and what comes out after {@code ticks}. {@code width} 0: any slots; otherwise laid out
     * {@code width} x {@code height} as at a crafting table, {@code ingredients} row by row with empty places.
     */
    public record Baking(Identifier id, List<Ingredient> ingredients, int width, int height, ItemStack result, int ticks) {
        public boolean isShaped() {
            return width > 0;
        }

        /** What goes in, without the empty places of a laid-out recipe. */
        public List<Ingredient> needed() {
            return ingredients.stream().filter(ingredient -> !RecipeParts.isGap(ingredient)).toList();
        }
    }

    /** The oven's recipes as this level knows them (a client knows them too): the laid-out ones first, as the oven tries them. */
    public static List<Baking> recipes(Level level) {
        List<Baking> shaped = new ArrayList<>();
        List<Baking> loose = new ArrayList<>();
        for (RecipeHolder<?> holder : KitchenRecipes.recipes(level)) {
            Recipe<?> recipe = holder.value();
            Identifier type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            boolean isShaped = BAKING_SHAPED.equals(type);
            if (!isShaped && !BAKING.equals(type)) {
                continue;
            }
            ItemStack result = RecipeParts.result(recipe);
            if (result.isEmpty()) {
                continue;
            }
            int ticks = call(recipe, "getCookTime", 200);
            if (isShaped) {
                shaped.add(new Baking(holder.id().identifier(), List.copyOf(RecipeParts.ingredients(recipe)), call(recipe, "getWidth", 0),
                        call(recipe, "getHeight", 0), result.copy(), ticks));
            } else {
                loose.add(new Baking(holder.id().identifier(), List.copyOf(RecipeParts.ingredients(recipe)), 0, 0, result.copy(), ticks));
            }
        }
        shaped.removeIf(recipe -> recipe.width() <= 0 || recipe.height() <= 0 || recipe.width() * recipe.height() != recipe.ingredients().size());
        shaped.addAll(loose);
        return shaped;
    }

    private static int call(Recipe<?> recipe, String method, int otherwise) {
        try {
            return (int) recipe.getClass().getMethod(method).invoke(recipe);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return otherwise;
        }
    }

    /** The recipe the grid (nine stacks, row by row) bakes, as the oven finds it: a laid-out recipe first. */
    public static Optional<Baking> matching(List<Baking> recipes, ItemStack[] grid) {
        for (Baking recipe : recipes) {
            if (recipe.isShaped() ? matchesShaped(recipe, grid) : matchesLoose(recipe, grid)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    /** Laid out anywhere in the grid, unturned, unmirrored, nothing else in it: the oven's own rule. */
    private static boolean matchesShaped(Baking recipe, ItemStack[] grid) {
        for (int left = 0; left <= GRID - recipe.width(); left++) {
            for (int top = 0; top <= GRID - recipe.height(); top++) {
                if (fitsAt(recipe, grid, left, top)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean fitsAt(Baking recipe, ItemStack[] grid, int left, int top) {
        for (int x = 0; x < GRID; x++) {
            for (int y = 0; y < GRID; y++) {
                ItemStack there = grid[x + y * GRID];
                boolean inside = x >= left && x < left + recipe.width() && y >= top && y < top + recipe.height();
                Ingredient wanted = inside ? recipe.ingredients().get(x - left + (y - top) * recipe.width()) : RecipeParts.NOTHING;
                if (inside ? !wanted.test(there) : !there.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** One stack for each ingredient, in any slots, and nothing more. */
    private static boolean matchesLoose(Baking recipe, ItemStack[] grid) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : grid) {
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        List<Ingredient> wanted = recipe.ingredients();
        if (items.size() != wanted.size()) {
            return false;
        }
        int[] holder = new int[items.size()];
        java.util.Arrays.fill(holder, -1);
        for (int ingredient = 0; ingredient < wanted.size(); ingredient++) {
            if (!assign(ingredient, wanted, items, holder, new boolean[items.size()])) {
                return false;
            }
        }
        return true;
    }

    /** Finds a stack for {@code ingredient}, moving earlier ones to other stacks where they fit (a matching, as the oven's own). */
    private static boolean assign(int ingredient, List<Ingredient> wanted, List<ItemStack> items, int[] holder, boolean[] seen) {
        for (int item = 0; item < items.size(); item++) {
            if (seen[item] || !wanted.get(ingredient).test(items.get(item))) {
                continue;
            }
            seen[item] = true;
            if (holder[item] < 0 || assign(holder[item], wanted, items, holder, seen)) {
                holder[item] = ingredient;
                return true;
            }
        }
        return false;
    }
}
