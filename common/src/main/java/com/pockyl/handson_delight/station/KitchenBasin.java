package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.mixin.RecipeManagerAccessor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Barbeque's Delight's ingredients basin, as the station sees it: two compartments of one stack each. A skewer is made of a stick,
 * food from one compartment and, for most, a side from the other (Barbeque's own use takes the side from the off hand). Reached by
 * name: the same members in its 1.20.1 and 1.21.1 builds.
 */
public final class KitchenBasin {
    private static final String ENTITY = "com.mao.barbequesdelight.content.block.BasinBlockEntity";
    public static final int PLACES = 2;
    public static final ResourceLocation SKEWERING = ResourceLocation.fromNamespaceAndPath(KitchenGrill.MOD_ID, "skewering");

    /**
     * The recipes per recipe manager (the client's and the server's of a single-player game are two), read again when its map of
     * recipes is a new one: the drawer and the stick on the rim ask for every inventory slot, and each recipe is read by reflection.
     */
    private static final Map<RecipeManager, Read> READ = new WeakHashMap<>();

    private static Class<?> entityClass;
    private static Field itemsField;
    private static Method notifyMethod;
    private static boolean broken;

    private final BlockEntity entity;
    private final Container items;

    private KitchenBasin(BlockEntity entity, Container items) {
        this.entity = entity;
        this.items = items;
    }

    /** The basin of a block entity, or null for anything else. */
    public static KitchenBasin of(BlockEntity entity) {
        if (entity == null || broken || !isBasin(entity) || !resolve(entity.getClass())) {
            return null;
        }
        try {
            return itemsField.get(entity) instanceof Container items && items.getContainerSize() >= PLACES ? new KitchenBasin(entity, items)
                    : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return null;
        }
    }

    /** Whether {@code entity} is Barbeque's Delight's basin, read without touching any of its members. */
    public static boolean isBasin(BlockEntity entity) {
        return entity != null && entity.getClass().getName().equals(ENTITY);
    }

    private static synchronized boolean resolve(Class<?> type) {
        if (type == entityClass) {
            return true;
        }
        try {
            Field items = type.getField("items");
            Method notify = type.getMethod("notifyTile");
            itemsField = items;
            notifyMethod = notify;
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
            HandsOnDelight.LOGGER.warn("Barbeque's Delight's basin is not what the basin station knows; it keeps its own use", e);
        }
    }

    public BlockEntity blockEntity() {
        return entity;
    }

    public ItemStack getItem(int place) {
        return place >= 0 && place < PLACES ? items.getItem(place) : ItemStack.EMPTY;
    }

    /** Puts {@code stack} in compartment {@code place}: a new stack, so that the station sees the change. */
    public void setItem(int place, ItemStack stack) {
        if (place >= 0 && place < PLACES) {
            items.setItem(place, stack.isEmpty() ? ItemStack.EMPTY : stack);
            changed();
        }
    }

    /** Tells the basin what lies in it changed: it saves and sends itself to the clients. */
    public void changed() {
        try {
            notifyMethod.invoke(entity);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
        }
    }

    /**
     * Puts as much of {@code stack} as fits into compartment {@code place}, as the basin takes it (one kind a compartment, up to a full
     * stack); {@code stack} shrinks by what went in. Returns how many.
     */
    public int put(int place, ItemStack stack, int most) {
        ItemStack there = getItem(place);
        if (stack.isEmpty() || !there.isEmpty() && !ItemStack.isSameItemSameComponents(there, stack)) {
            return 0;
        }
        int count = Math.min(Math.min(most, stack.getCount()), stack.getMaxStackSize() - there.getCount());
        if (count <= 0) {
            return 0;
        }
        setItem(place, stack.copyWithCount(there.getCount() + count));
        stack.shrink(count);
        return count;
    }

    /** A skewering recipe: the tool (a stick), the food and how many, the side and how many (none for some), what comes out. */
    public record Skewering(ResourceLocation id, Ingredient tool, Ingredient food, int foodCount, Ingredient side, int sideCount,
            ItemStack result) {
        /** Whether it takes no side. */
        public boolean plain() {
            return sideCount <= 0 || side == null || side.isEmpty();
        }

        /** Whether {@code food} and {@code side} (empty for a plain one) are enough of the right things for one skewer. */
        public boolean takes(ItemStack food, ItemStack side) {
            return this.food.test(food) && food.getCount() >= foodCount && (plain() || this.side.test(side) && side.getCount() >= sideCount);
        }
    }

    private record Read(Map<?, ?> from, List<Skewering> recipes) {
    }

    /** Every skewering recipe of the basin, in the recipe manager's order; empty without Barbeque's Delight. */
    public static List<Skewering> recipes(Level level) {
        RecipeManager manager = level.getRecipeManager();
        Map<?, ?> from = ((RecipeManagerAccessor) manager).handson$byName();
        synchronized (READ) {
            Read read = READ.get(manager);
            if (read == null || read.from() != from) {
                read = new Read(from, List.copyOf(read(level)));
                READ.put(manager, read);
            }
            return read.recipes();
        }
    }

    private static List<Skewering> read(Level level) {
        List<Skewering> recipes = new ArrayList<>();
        Optional<RecipeType<?>> type = BuiltInRegistries.RECIPE_TYPE.getOptional(SKEWERING);
        if (type.isEmpty()) {
            return recipes;
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<RecipeHolder<?>> holders = (List) level.getRecipeManager().getAllRecipesFor((RecipeType) type.get());
        for (RecipeHolder<?> holder : holders) {
            Recipe<?> recipe = holder.value();
            Ingredient tool = field(recipe, "tool") instanceof Ingredient it ? it : null;
            Ingredient food = field(recipe, "ingredient") instanceof Ingredient it ? it : null;
            Ingredient side = field(recipe, "side") instanceof Ingredient it ? it : Ingredient.EMPTY;
            int foodCount = field(recipe, "ingredientCount") instanceof Integer count ? count : 1;
            int sideCount = field(recipe, "sideCount") instanceof Integer count ? count : 0;
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (tool == null || food == null || food.isEmpty() || result.isEmpty()) {
                continue;
            }
            recipes.add(new Skewering(holder.id(), tool, food, Math.max(1, foodCount), side, sideCount, result.copy()));
        }
        return recipes;
    }

    private static Object field(Object recipe, String name) {
        try {
            return recipe.getClass().getField(name).get(recipe);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** What one compartment's food and the other's side make with {@code tool}: which recipe and which compartment holds the food. */
    public record Match(Skewering recipe, int foodPlace) {
        public int sidePlace() {
            return 1 - foodPlace;
        }
    }

    /** The skewer {@code tool} makes of what lies in the compartments now, the food from either one. */
    public Optional<Match> match(Level level, ItemStack tool) {
        for (Skewering recipe : recipes(level)) {
            if (!recipe.tool().test(tool)) {
                continue;
            }
            for (int foodPlace = 0; foodPlace < PLACES; foodPlace++) {
                if (recipe.takes(getItem(foodPlace), getItem(1 - foodPlace))) {
                    return Optional.of(new Match(recipe, foodPlace));
                }
            }
        }
        return Optional.empty();
    }

    /** Whether {@code stack} is a tool of any skewering recipe: a stick. */
    public static boolean isTool(Level level, ItemStack stack) {
        return !stack.isEmpty() && recipes(level).stream().anyMatch(recipe -> recipe.tool().test(stack));
    }

    /** Whether {@code stack} is the food or side of any skewering recipe. */
    public static boolean isFood(Level level, ItemStack stack) {
        return !stack.isEmpty() && recipes(level).stream()
                .anyMatch(recipe -> recipe.food().test(stack) || !recipe.plain() && recipe.side().test(stack));
    }

    /** Makes one skewer of {@code match} with {@code tool}, which loses one stick: the compartments give their food and side. */
    public ItemStack skewer(Match match, ItemStack tool) {
        Skewering recipe = match.recipe();
        tool.shrink(1);
        setItem(match.foodPlace(), getItem(match.foodPlace()).copyWithCount(getItem(match.foodPlace()).getCount() - recipe.foodCount()));
        if (!recipe.plain()) {
            setItem(match.sidePlace(), getItem(match.sidePlace()).copyWithCount(getItem(match.sidePlace()).getCount() - recipe.sideCount()));
        }
        return recipe.result().copy();
    }
}
