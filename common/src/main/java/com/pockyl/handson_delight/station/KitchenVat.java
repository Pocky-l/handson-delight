package com.pockyl.handson_delight.station;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import com.pockyl.handson_delight.HandsOnDelight;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Cultural Delights' vat, as the station sees it. Its block entity is a container in both of its builds, so its items go through
 * Minecraft's own {@link Container}; only how far it has aged is read by name. Slots as its menu has them: six for what ages, one
 * for the container the result goes into (a bottle, a wafer), then the output. It ages by itself, one of each slot at a time, while
 * the vat is as warm as the recipe wants: hot over a heat source of Farmer's Delight, cold over ice, otherwise neither.
 */
public final class KitchenVat {
    public static final String MOD_ID = "culturaldelights";
    private static final String ENTITY = "com.baisylia.culturaldelights.block.entity.custom.VatBlockEntity";
    public static final int INGREDIENTS = 6;
    public static final int CONTAINER_SLOT = 6;
    public static final int OUTPUT_SLOT = 7;
    public static final ResourceLocation AGING = ResourceLocation.fromNamespaceAndPath(MOD_ID, "aging");
    /** The vat's block state property for how warm it is; the same names as its {@code VatTemperature}. */
    private static final String TEMPERATURE_PROPERTY = "temperature";

    /** How warm the vat is, or a recipe wants it: its own three, cold to hot. */
    public enum Temperature {
        COLD("cold"),
        NORMAL("normal"),
        HOT("hot");

        private final String name;

        Temperature(String name) {
            this.name = name;
        }

        public String serializedName() {
            return name;
        }

        static Temperature of(String name) {
            return Arrays.stream(values()).filter(value -> value.name.equals(name)).findFirst().orElse(NORMAL);
        }
    }

    private static Class<?> entityClass;
    private static Field progressField;
    private static Field totalField;
    private static boolean broken;

    private final BlockEntity entity;
    private final Container container;

    private KitchenVat(BlockEntity entity) {
        this.entity = entity;
        this.container = (Container) entity;
    }

    /** The vat of a block entity, or null for anything else. */
    public static KitchenVat of(BlockEntity entity) {
        if (entity == null || broken || !(entity instanceof Container) || !entity.getClass().getName().equals(ENTITY)
                || !resolve(entity.getClass())) {
            return null;
        }
        return new KitchenVat(entity);
    }

    private static synchronized boolean resolve(Class<?> type) {
        if (type == entityClass) {
            return true;
        }
        try {
            Field progress = type.getDeclaredField("progress");
            Field total = type.getDeclaredField("maxProgress");
            progress.setAccessible(true);
            total.setAccessible(true);
            progressField = progress;
            totalField = total;
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
            HandsOnDelight.LOGGER.warn("Cultural Delights' vat is not what the vat station knows; it keeps its own menu", e);
        }
    }

    public BlockEntity blockEntity() {
        return entity;
    }

    public ItemStack getItem(int slot) {
        return container.getItem(slot);
    }

    /** Puts a stack in a slot; the vat starts over what it ages when an ingredient changes, as it always does. */
    public void setItem(int slot, ItemStack stack) {
        container.setItem(slot, stack);
        entity.setChanged();
    }

    /** Ticks aged of what is in it, 0 while it ages nothing. */
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

    /** Ticks what is in it takes to age. */
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

    /** Whether the vat is the right way up for the station: its lid on top. Lying or upside down it keeps its own menu. */
    public static boolean isUpright(BlockState state) {
        return state.hasProperty(BlockStateProperties.FACING) && state.getValue(BlockStateProperties.FACING) == Direction.UP;
    }

    /** How warm the vat is, as it sets its own block state every tick from what is under it. */
    public static Temperature temperature(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(TEMPERATURE_PROPERTY)) {
                Object value = state.getValue(property);
                return Temperature.of(value instanceof StringRepresentable named ? named.getSerializedName() : value.toString());
            }
        }
        return Temperature.NORMAL;
    }

    /**
     * An aging recipe: what goes in (one of each, in any of the six places), the container the result is put in (empty for none),
     * how warm the vat must be, and what comes out after {@code ticks}.
     */
    public record Aging(ResourceLocation id, List<Ingredient> ingredients, Ingredient container, Temperature temperature, ItemStack result,
            int ticks) {
        public boolean hasContainer() {
            return !container.isEmpty();
        }

        /** Everything the cook brings: the ingredients, then the container. */
        public List<Ingredient> needed() {
            List<Ingredient> all = new ArrayList<>(ingredients);
            if (hasContainer()) {
                all.add(container);
            }
            return all;
        }
    }

    /** The drink a vat aged without its container, waiting in it to be drawn: kept by this mod in the vat's block entity. */
    public interface Servings {
        ItemStack handson$servings();

        void handson$setServings(ItemStack stack);
    }

    /** The drink waiting in the vat to be drawn into its containers, empty for none. */
    public ItemStack servings() {
        return entity instanceof Servings servings ? servings.handson$servings() : ItemStack.EMPTY;
    }

    public void setServings(ItemStack stack) {
        if (entity instanceof Servings servings) {
            servings.handson$setServings(stack);
        }
    }

    /** The recipes read last, and the recipe manager they were read from: read again when it is another (a reload, another world). */
    private static java.lang.ref.WeakReference<Object> readFrom = new java.lang.ref.WeakReference<>(null);
    private static List<Aging> read = List.of();

    /** The vat's recipes as this level knows them (a client knows them too), in the order the vat tries them. */
    public static synchronized List<Aging> recipes(Level level) {
        Object manager = level.getRecipeManager();
        if (readFrom.get() != manager) {
            read = List.copyOf(readRecipes(level));
            readFrom = new java.lang.ref.WeakReference<>(manager);
        }
        return read;
    }

    private static List<Aging> readRecipes(Level level) {
        List<Aging> recipes = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!AGING.equals(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()))) {
                continue;
            }
            ItemStack result = recipe.getResultItem(level.registryAccess());
            List<Ingredient> ingredients = List.copyOf(recipe.getIngredients());
            // A recipe with an ingredient that names nothing can never be made.
            if (result.isEmpty() || ingredients.isEmpty() || ingredients.size() > INGREDIENTS || ingredients.stream().anyMatch(Ingredient::isEmpty)) {
                continue;
            }
            Ingredient container = call(recipe, "getContainer", Ingredient.class, Ingredient.EMPTY);
            Object temperature = call(recipe, "getTemperature", Object.class, null);
            Temperature wanted = temperature instanceof StringRepresentable named ? Temperature.of(named.getSerializedName()) : Temperature.NORMAL;
            int ticks = call(recipe, "getCookTime", Integer.class, 200);
            recipes.add(new Aging(holder.id(), ingredients, container, wanted, result.copy(), ticks));
        }
        return recipes;
    }

    private static <T> T call(Recipe<?> recipe, String method, Class<T> type, T otherwise) {
        try {
            Object value = recipe.getClass().getMethod(method).invoke(recipe);
            return type.isInstance(value) ? type.cast(value) : otherwise;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return otherwise;
        }
    }

    /**
     * The recipe what lies in the vat ages into, whatever the warmth, as the vat matches it: one stack for each ingredient in the six
     * places and nothing more, the container place holding the recipe's container, or nothing when it has none.
     */
    public static Optional<Aging> matching(List<Aging> recipes, ItemStack[] places, ItemStack container) {
        for (Aging recipe : recipes) {
            boolean containerFits = recipe.hasContainer() ? recipe.container().test(container) : container.isEmpty();
            if (containerFits && matchesLoose(recipe.ingredients(), places)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    /** The recipe of the six places alone, the container left out: what the vat would make once its container is there. */
    public static Optional<Aging> matchingPlaces(List<Aging> recipes, ItemStack[] places) {
        return recipes.stream().filter(recipe -> matchesLoose(recipe.ingredients(), places)).findFirst();
    }

    /** The recipe that ages into {@code result} in a container, if any: a drink drawn from the vat into what that recipe names. */
    public static Optional<Aging> bottling(List<Aging> recipes, ItemStack result) {
        return result.isEmpty() ? Optional.empty()
                : recipes.stream().filter(recipe -> recipe.hasContainer() && ItemStack.isSameItem(recipe.result(), result)).findFirst();
    }

    /**
     * Whether {@code result}, waiting in the vat, is a drink: it is drawn into bottles. Anything else drawn into a container (ice cream
     * into a wafer) has set in the vat and is scooped out.
     */
    public static boolean isDrink(List<Aging> recipes, ItemStack result) {
        return bottling(recipes, result).map(recipe -> recipe.container().test(new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE)))
                .orElse(false);
    }

    /** How high what waits in the vat stands, hundredths of its inside: where the liquid it was made from stood, a little more for every serving. */
    public static int servingsLevel(ItemStack servings) {
        return servings.isEmpty() ? 0 : Math.min(SERVINGS_MOST, SERVINGS_FIRST + SERVINGS_MORE * (servings.getCount() - 1));
    }

    private static final int SERVINGS_FIRST = 45;
    private static final int SERVINGS_MORE = 3;
    private static final int SERVINGS_MOST = 60;

    /** Whether {@code stack} is no recipe's ingredient but some recipe's container: it goes into the container place. */
    public static boolean isContainer(List<Aging> recipes, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean container = false;
        for (Aging recipe : recipes) {
            if (recipe.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack))) {
                return false;
            }
            container |= recipe.hasContainer() && recipe.container().test(stack);
        }
        return container;
    }

    /** One stack for each ingredient, in any places, and nothing more. */
    static boolean matchesLoose(List<Ingredient> wanted, ItemStack[] places) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : places) {
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        if (items.size() != wanted.size()) {
            return false;
        }
        int[] holder = new int[items.size()];
        Arrays.fill(holder, -1);
        for (int ingredient = 0; ingredient < wanted.size(); ingredient++) {
            if (!assign(ingredient, wanted, items, holder, new boolean[items.size()])) {
                return false;
            }
        }
        return true;
    }

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
