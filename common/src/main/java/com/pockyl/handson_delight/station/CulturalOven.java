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

import com.pockyl.diegesis.api.station.StationContext;
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
 * Cultural Delights' oven, as the oven station sees it. Its block entity keeps an item handler of the loader's own class (NeoForge's,
 * or Refabricated's on Fabric), so it is reached by name: the same field and method names in both of its builds, no class of that mod
 * needed. Slots as its menu has them: nine for what bakes, a 3 x 3 grid, then the output. It bakes by itself, the whole tray as one set
 * into a dish, while the block under it is a heat source of Farmer's Delight; the station only reads it and puts things in and out.
 * With the door open the heat goes: its count of how far it has baked stays where it was ({@link #whileDoor}).
 */
public final class CulturalOven implements KitchenOven {
    public static final String MOD_ID = "culturaldelights";
    private static final String ENTITY = "com.baisylia.culturaldelights.block.entity.custom.OvenBlockEntity";
    public static final int PLACES = 9;
    public static final int OUTPUT_SLOT = 9;
    /** As its slots hold: a whole stack a place. */
    public static final int PER_PLACE = 64;
    /** Its two recipe types: a set of ingredients in any slots, and one laid out in the grid as at a crafting table. */
    public static final Identifier BAKING = Identifier.fromNamespaceAndPath(MOD_ID, "baking");
    public static final Identifier BAKING_SHAPED = Identifier.fromNamespaceAndPath(MOD_ID, "baking_shaped");

    private static Class<?> entityClass;
    private static Field progressField;
    private static Field totalField;
    private static Method handlerMethod;
    private static boolean broken;
    /** What the recipes were listed from: see {@link KitchenRecipes#source}. */
    private static Object listedFor;
    private static List<Baking> listed = List.of();

    private final BlockEntity entity;
    private final Object handler;
    /** While the door is open: how far it had baked, which it stays at until the door shuts; -1 with the door shut. */
    private int heldAt = -1;

    private CulturalOven(BlockEntity entity, Object handler) {
        this.entity = entity;
        this.handler = handler;
    }

    /** The oven of a block entity, or null for anything else. */
    public static CulturalOven of(BlockEntity entity) {
        if (entity == null || broken || !entity.getClass().getName().equals(ENTITY) || !resolve(entity.getClass())) {
            return null;
        }
        try {
            return new CulturalOven(entity, handlerMethod.invoke(entity));
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

    @Override
    public BlockEntity blockEntity() {
        return entity;
    }

    /** One of its ten slots: the nine places, then the output. */
    public ItemStack item(int slot) {
        return Platform.INSTANCE.handlerItem(handler, slot);
    }

    private void setItem(int slot, ItemStack stack) {
        Platform.INSTANCE.setHandlerItem(handler, slot, stack);
        entity.setChanged();
    }

    @Override
    public int places() {
        return PLACES;
    }

    @Override
    public ItemStack place(int index) {
        return item(index);
    }

    @Override
    public void setPlace(int index, ItemStack stack) {
        setItem(index, stack);
    }

    @Override
    public int outputs() {
        return 1;
    }

    @Override
    public ItemStack output(int index) {
        return item(OUTPUT_SLOT);
    }

    @Override
    public void setOutput(int index, ItemStack stack) {
        setItem(OUTPUT_SLOT, stack);
    }

    @Override
    public boolean bakesEachPlace() {
        return false;
    }

    @Override
    public int perPlace() {
        return PER_PLACE;
    }

    /** Anything goes on its tray: what it makes depends on the whole set. */
    @Override
    public boolean takes(ItemStack stack) {
        return !stack.isEmpty();
    }

    /** Ticks baked of what is in it, 0 while it bakes nothing. */
    public int ticks() {
        return read(progressField);
    }

    private void setTicks(int ticks) {
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

    /** The whole tray bakes as one: each place that holds something is as far on as the set. */
    @Override
    public float progress(int place) {
        int time = ticks();
        int total = total();
        return place(place).isEmpty() || time <= 0 || total <= 0 ? 0.0F : Math.min(1.0F, (float) time / total);
    }

    @Override
    public int ticksLeft(int place) {
        int time = ticks();
        int total = total();
        return place(place).isEmpty() || time <= 0 || total <= 0 ? 0 : Math.max(0, total - time);
    }

    @Override
    public boolean isHot() {
        return isHeated(entity.getBlockState());
    }

    /** Whether the block under it heats it: the oven lights itself by that every tick. */
    public static boolean isHeated(BlockState state) {
        return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
    }

    /** A set that makes a dish lies on the tray, and no heat under it. */
    @Override
    public boolean waitsForHeat() {
        Level level = entity.getLevel();
        if (isHot() || level == null) {
            return false;
        }
        ItemStack[] grid = new ItemStack[PLACES];
        for (int place = 0; place < PLACES; place++) {
            grid[place] = place(place);
        }
        return KitchenOven.matching(recipes(level), grid).isPresent();
    }

    /** With the door open the heat goes: its count of how far it has baked stays where it was. */
    @Override
    public void whileDoor(StationContext station, boolean open) {
        if (!open) {
            heldAt = -1;
            return;
        }
        int time = ticks();
        if (heldAt < 0 || time < heldAt) {
            heldAt = time;
        }
        if (time != heldAt) {
            setTicks(heldAt);
        }
    }

    @Override
    public List<Baking> recipes(Level level) {
        return recipesOf(level);
    }

    /**
     * Its recipes as this level knows them (a client knows them too): the laid-out ones first, as the oven tries them. Listed once a
     * recipe manager.
     */
    public static synchronized List<Baking> recipesOf(Level level) {
        Object manager = KitchenRecipes.source(level);
        if (manager == listedFor) {
            return listed;
        }
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
                shaped.add(new Baking(holder.id().identifier(), gridOf(recipe), call(recipe, "getWidth", 0),
                        call(recipe, "getHeight", 0), result.copy(), ticks, OvenStation.KIND_FOOD));
            } else {
                loose.add(new Baking(holder.id().identifier(), List.copyOf(RecipeParts.ingredients(recipe)), 0, 0, result.copy(), ticks,
                        OvenStation.KIND_FOOD));
            }
        }
        shaped.removeIf(recipe -> recipe.width() <= 0 || recipe.height() <= 0 || recipe.width() * recipe.height() != recipe.ingredients().size());
        shaped.addAll(loose);
        listedFor = manager;
        listed = List.copyOf(shaped);
        return listed;
    }

    /**
     * A laid-out recipe's grid, row by row with its blank places as gaps ({@link RecipeParts#NOTHING}): its {@code getIngredients()}
     * gives the places as optional ingredients, where the game's own list of what it takes leaves the blanks out.
     */
    private static List<Ingredient> gridOf(Recipe<?> recipe) {
        try {
            if (recipe.getClass().getMethod("getIngredients").invoke(recipe) instanceof List<?> places) {
                List<Ingredient> grid = new ArrayList<>(places.size());
                for (Object place : places) {
                    Object inside = place instanceof Optional<?> optional ? optional.orElse(null) : place;
                    grid.add(inside instanceof Ingredient ingredient ? ingredient : RecipeParts.NOTHING);
                }
                return List.copyOf(grid);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Read as the game lists what it takes, below.
        }
        return List.copyOf(RecipeParts.ingredients(recipe));
    }

    private static int call(Recipe<?> recipe, String method, int otherwise) {
        try {
            return (int) recipe.getClass().getMethod(method).invoke(recipe);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return otherwise;
        }
    }
}
