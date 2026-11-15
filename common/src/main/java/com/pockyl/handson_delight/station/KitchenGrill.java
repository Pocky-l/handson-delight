package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import com.pockyl.handson_delight.HandsOnDelight;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Barbeque's Delight's grill, as the station sees it: two skewers side by side, each grilling by itself in the grill's own tick. A
 * skewer cooks to half its time and waits to be turned (once); turned, it is done at its full time and becomes what its recipe makes;
 * left on, anything burns at twice its time. The station reads it and lays, turns and takes skewers through the grill's own methods,
 * so its rules hold however a skewer got there. Reached by name: the same members in its 1.20.1 and 1.21.1 builds.
 */
public final class KitchenGrill {
    public static final String MOD_ID = "barbequesdelight";
    private static final String ENTITY = "com.mao.barbequesdelight.content.block.GrillBlockEntity";
    private static final String SEASONING = "com.mao.barbequesdelight.content.item.SeasoningItem";
    public static final int PLACES = 2;
    public static final ResourceLocation GRILLING = ResourceLocation.fromNamespaceAndPath(MOD_ID, "grilling");

    private static Class<?> entityClass;
    private static Field entriesField;
    private static Field timeField;
    private static Field durationField;
    private static Field flippedField;
    private static Field burntField;
    private static Field stackField;
    private static Method addMethod;
    private static Method canFlipMethod;
    private static Method flipMethod;
    private static Method heatedMethod;
    private static Method changedMethod;
    private static boolean broken;

    private final BlockEntity entity;
    private final Object[] entries;

    private KitchenGrill(BlockEntity entity, Object[] entries) {
        this.entity = entity;
        this.entries = entries;
    }

    /** The grill of a block entity, or null for anything else. */
    public static KitchenGrill of(BlockEntity entity) {
        if (entity == null || broken || !entity.getClass().getName().equals(ENTITY) || !resolve(entity.getClass())) {
            return null;
        }
        try {
            Object[] entries = (Object[]) entriesField.get(entity);
            return entries.length == PLACES ? new KitchenGrill(entity, entries) : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return null;
        }
    }

    /** Whether {@code entity} is Barbeque's Delight's grill, read without touching any of its members. */
    public static boolean isGrill(BlockEntity entity) {
        return entity != null && entity.getClass().getName().equals(ENTITY);
    }

    private static synchronized boolean resolve(Class<?> type) {
        if (type == entityClass) {
            return true;
        }
        try {
            Field entries = type.getField("entries");
            Class<?> entry = entries.getType().getComponentType();
            timeField = entry.getField("time");
            durationField = entry.getField("duration");
            flippedField = entry.getField("flipped");
            burntField = entry.getField("burnt");
            stackField = entry.getField("stack");
            addMethod = type.getMethod("addItem", int.class, ItemStack.class);
            canFlipMethod = type.getMethod("canFlip", int.class);
            flipMethod = type.getMethod("flip", int.class);
            heatedMethod = type.getMethod("isHeated");
            changedMethod = type.getMethod("inventoryChanged");
            entriesField = entries;
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
            HandsOnDelight.LOGGER.warn("Barbeque's Delight's grill is not what the grill station knows; it keeps its own use", e);
        }
    }

    public BlockEntity blockEntity() {
        return entity;
    }

    public ItemStack getItem(int place) {
        Object stack = get(stackField, place);
        return stack instanceof ItemStack item ? item : ItemStack.EMPTY;
    }

    /** Ticks the skewer on {@code place} has grilled; its lower side is done at half its {@link #duration}. */
    public int time(int place) {
        return get(timeField, place) instanceof Integer ticks ? ticks : 0;
    }

    /** Ticks the skewer on {@code place} takes on both sides, from its recipe. */
    public int duration(int place) {
        return get(durationField, place) instanceof Integer ticks ? ticks : 0;
    }

    public boolean turned(int place) {
        return Boolean.TRUE.equals(get(flippedField, place));
    }

    public boolean burnt(int place) {
        return Boolean.TRUE.equals(get(burntField, place));
    }

    /** Whether the skewer on {@code place} may be turned: its lower side is done and it was not turned yet. */
    public boolean canTurn(int place) {
        return Boolean.TRUE.equals(call(canFlipMethod, place));
    }

    /** Turns the skewer on {@code place} over, as the grill does: its sound, its time back to the half. */
    public boolean turn(int place) {
        return Boolean.TRUE.equals(call(flipMethod, place));
    }

    /** Lays one of {@code stack} on the free {@code place} the grill's way (it reads its recipe); the stack shrinks by one. */
    public boolean lay(int place, ItemStack stack) {
        if (place < 0 || place >= PLACES || !getItem(place).isEmpty()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(addMethod.invoke(entity, place, stack));
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return false;
        }
    }

    /** Takes the skewer off {@code place}, as an empty hand does at the grill. */
    public ItemStack take(int place) {
        ItemStack stack = getItem(place);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = stack.copy();
        set(stackField, place, ItemStack.EMPTY);
        changed();
        return taken;
    }

    /** Puts {@code stack} on {@code place} as it is, with no new grilling time: the station's slot window, for the library only. */
    public void setItem(int place, ItemStack stack) {
        set(stackField, place, stack);
        changed();
    }

    /** Whether it grills: a campfire in it, or Farmer's Delight's heat under it. */
    public boolean heated() {
        try {
            return Boolean.TRUE.equals(heatedMethod.invoke(entity));
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return false;
        }
    }

    /** Tells the grill what lies on it changed: it saves and sends itself to the clients. */
    public void changed() {
        try {
            changedMethod.invoke(entity);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
        }
    }

    private Object get(Field field, int place) {
        if (place < 0 || place >= PLACES) {
            return null;
        }
        try {
            return field.get(entries[place]);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return null;
        }
    }

    private void set(Field field, int place, Object value) {
        if (place < 0 || place >= PLACES) {
            return;
        }
        try {
            field.set(entries[place], value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
        }
    }

    private Object call(Method method, int place) {
        try {
            return method.invoke(entity, place);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return null;
        }
    }

    /** A grilling recipe: what goes on, what comes off, and in how many ticks on both sides. */
    public record Grilling(ResourceLocation id, Ingredient ingredient, ItemStack result, int ticks) {
    }

    /** Every grilling recipe of the grill, in the recipe manager's order; empty without Barbeque's Delight. */
    public static List<Grilling> recipes(Level level) {
        List<Grilling> recipes = new ArrayList<>();
        Optional<RecipeType<?>> type = BuiltInRegistries.RECIPE_TYPE.getOptional(GRILLING);
        if (type.isEmpty()) {
            return recipes;
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<RecipeHolder<?>> holders = (List) level.getRecipeManager().getAllRecipesFor((RecipeType) type.get());
        for (RecipeHolder<?> holder : holders) {
            Recipe<?> recipe = holder.value();
            Ingredient ingredient = field(recipe, "ingredient") instanceof Ingredient in ? in : null;
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (ingredient == null || ingredient.isEmpty() || result.isEmpty()) {
                continue;
            }
            int ticks = field(recipe, "barbecuingTime") instanceof Integer time ? time : 0;
            recipes.add(new Grilling(holder.id(), ingredient, result.copy(), ticks));
        }
        return recipes;
    }

    /** The recipe that grills {@code stack}, if any. */
    public static Optional<Grilling> recipe(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return recipes(level).stream().filter(recipe -> recipe.ingredient().test(stack)).findFirst();
    }

    /** Whether {@code stack} is grilled on the grill. */
    public static boolean grills(Level level, ItemStack stack) {
        return recipe(level, stack).isPresent();
    }

    private static Object field(Object recipe, String name) {
        try {
            return recipe.getClass().getField(name).get(recipe);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Whether {@code seasoning} is one of Barbeque's Delight's seasonings (powders and sauces). */
    public static boolean isSeasoning(ItemStack seasoning) {
        return !seasoning.isEmpty() && isSeasoningClass(seasoning.getItem().getClass());
    }

    private static boolean isSeasoningClass(Class<?> type) {
        for (Class<?> at = type; at != null; at = at.getSuperclass()) {
            if (at.getName().equals(SEASONING)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code seasoning} can go on {@code skewer}: a skewer that has none yet, as the seasoning itself decides. */
    public static boolean canSeason(ItemStack seasoning, ItemStack skewer) {
        if (!isSeasoning(seasoning) || skewer.isEmpty()) {
            return false;
        }
        try {
            Method can = seasoning.getItem().getClass().getMethod("canSprinkle", ItemStack.class);
            return Boolean.TRUE.equals(can.invoke(seasoning.getItem(), skewer));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Seasons the skewer on {@code place} with {@code seasoning} through the seasoning's own code (its mark on the skewer, its dust,
     * a use of it worn off): server side.
     */
    public boolean season(int place, ItemStack seasoning, Player player) {
        // A new stack, not the one lying there marked in place: the station sees a change only in a different stack.
        ItemStack skewer = getItem(place).copy();
        if (!canSeason(seasoning, skewer)) {
            return false;
        }
        try {
            Method sprinkle = seasoning.getItem().getClass().getMethod("sprinkle", ItemStack.class, Vec3.class, ItemStack.class,
                    Player.class, InteractionHand.class);
            sprinkle.invoke(seasoning.getItem(), seasoning, Vec3.atCenterOf(entity.getBlockPos()).add(0.0, 0.5, 0.0), skewer, player,
                    InteractionHand.MAIN_HAND);
        } catch (ReflectiveOperationException | RuntimeException e) {
            HandsOnDelight.LOGGER.warn("Could not season a skewer with {}", seasoning, e);
            return false;
        }
        setItem(place, skewer);
        return true;
    }

    /** Whether {@code stack} is seasoned: it carries a seasoning that the skewer's own name shows. */
    public static boolean isSeasoned(ItemStack stack) {
        return seasoning(stack).isPresent();
    }

    /** The seasoning item {@code stack} is seasoned with, read from its {@code barbequesdelight:seasoning} data. */
    public static Optional<ItemStack> seasoning(ItemStack stack) {
        return Optional.ofNullable(BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.fromNamespaceAndPath(MOD_ID, "seasoning")))
                .map(stack::get)
                .filter(value -> value instanceof net.minecraft.world.level.ItemLike)
                .map(value -> new ItemStack((net.minecraft.world.level.ItemLike) value));
    }

    /** The burnt skewer, what anything left too long on the grill becomes. */
    public static boolean isBurnt(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ResourceLocation.fromNamespaceAndPath(MOD_ID, "burnt_skewer"));
    }
}
