package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.KitchenRecipes;
import com.pockyl.handson_delight.RecipeParts;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Cooking for Blockheads' oven, as the oven station sees it. Its block entity keeps one container of 20 (vanilla's {@link Container},
 * Balm's on both loaders): 0-2 what waits to go in, 3 fuel, 4-6 what is baked, 7-15 the 3 x 3 rack it bakes on, one thing a place,
 * 16-19 tools standing on top. It moves one thing at a time from the input onto a free place, bakes each place in 200 ticks while fuel
 * burns, and moves what is done to the output; hoppers fill the input from above and the fuel from the sides and empty the output
 * from below. The station only reads it and puts things on and off the rack, so all of that goes on. Reached by name, the same in its
 * NeoForge and Fabric builds: no class of that mod is needed. On 1.21.1 it bakes by recipe type, its own first: its oven recipes,
 * then campfire, smoking and smelting recipes (smelting only into food).
 */
public final class CfbOven implements KitchenOven {
    public static final String MOD_ID = "cookingforblockheads";
    private static final String ENTITY = "net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity";
    private static final String CONFIG = "net.blay09.mods.cookingforblockheads.CookingForBlockheadsConfig";
    private static final String SMELTED_EVENT = "net.blay09.mods.cookingforblockheads.api.event.OvenItemSmeltedEvent";
    public static final int INPUT = 0;
    public static final int INPUTS = 3;
    public static final int FUEL = 3;
    public static final int OUTPUT = 4;
    public static final int OUTPUTS = 3;
    public static final int RACK = 7;
    public static final int PLACES = 9;
    public static final int TOOL = 16;
    public static final int TOOLS = 4;
    /** Its baking time before its config's multiplier, ticks. */
    public static final int BAKE_TICKS = 200;
    /** The power upgrade a player puts into it from the world: its own use. */
    public static final Identifier HEATING_UNIT = Identifier.fromNamespaceAndPath(MOD_ID, "heating_unit");
    /** What its own use stands on top of it: its kitchen's utensils. */
    public static final TagKey<Item> UTENSILS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "utensils"));
    /** Its own recipe type, the first it bakes by. */
    private static final Identifier OVEN_RECIPES = Identifier.fromNamespaceAndPath(MOD_ID, "oven");

    private static Class<?> entityClass;
    private static Method containerMethod;
    private static Method fuelMethod;
    private static Field cookTimesField;
    private static Field burnTimeField;
    private static boolean broken;
    /** What the recipes were listed from: see {@link KitchenRecipes#source}. */
    private static Object listedFor;
    private static List<Baking> listed = List.of();

    private final BlockEntity entity;
    private final Container container;

    private CfbOven(BlockEntity entity, Container container) {
        this.entity = entity;
        this.container = container;
    }

    /** The oven of a block entity, or null for anything else. */
    public static CfbOven of(BlockEntity entity) {
        if (entity == null || broken || !entity.getClass().getName().equals(ENTITY) || !resolve(entity.getClass())) {
            return null;
        }
        try {
            Container container = (Container) containerMethod.invoke(entity);
            return container == null || container.getContainerSize() < TOOL + TOOLS ? null : new CfbOven(entity, container);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return null;
        }
    }

    /** Whether the block entity is Cooking for Blockheads' oven, without asking more of it. */
    public static boolean isOven(BlockEntity entity) {
        return entity != null && entity.getClass().getName().equals(ENTITY);
    }

    private static synchronized boolean resolve(Class<?> type) {
        if (type == entityClass) {
            return true;
        }
        try {
            Method container = type.getMethod("getInternalContainer");
            // Since 26.x its fuel test asks the level's fuel values.
            Method fuel = type.getMethod("isItemFuel", Level.class, ItemStack.class);
            Field cookTimes = type.getField("slotCookTime");
            Field burnTime = type.getField("furnaceBurnTime");
            containerMethod = container;
            fuelMethod = fuel;
            cookTimesField = cookTimes;
            burnTimeField = burnTime;
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
            HandsOnDelight.LOGGER.warn("Cooking for Blockheads' oven is not what the oven station knows; it keeps its own menu", e);
        }
    }

    @Override
    public BlockEntity blockEntity() {
        return entity;
    }

    private void set(int slot, ItemStack stack) {
        // Its container resets the place's baking time when a place changes, as for its own menu.
        container.setItem(slot, stack);
        entity.setChanged();
    }

    @Override
    public int places() {
        return PLACES;
    }

    @Override
    public ItemStack place(int index) {
        return container.getItem(RACK + index);
    }

    @Override
    public void setPlace(int index, ItemStack stack) {
        set(RACK + index, stack);
    }

    @Override
    public int outputs() {
        return OUTPUTS;
    }

    @Override
    public ItemStack output(int index) {
        return container.getItem(OUTPUT + index);
    }

    @Override
    public void setOutput(int index, ItemStack stack) {
        set(OUTPUT + index, stack);
    }

    /** What waits in its input to go onto the rack, put there by a hopper or its own menu. */
    public ItemStack input(int index) {
        return container.getItem(INPUT + index);
    }

    @Override
    public boolean burnsFuel() {
        return true;
    }

    @Override
    public ItemStack fuel() {
        return container.getItem(FUEL);
    }

    @Override
    public void setFuel(ItemStack stack) {
        set(FUEL, stack);
    }

    @Override
    public boolean isFuel(ItemStack stack) {
        try {
            Level level = entity.getLevel();
            return !stack.isEmpty() && level != null && (boolean) fuelMethod.invoke(entity, level, stack);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return false;
        }
    }

    @Override
    public int tools() {
        return TOOLS;
    }

    @Override
    public ItemStack tool(int index) {
        return container.getItem(TOOL + index);
    }

    @Override
    public void setTool(int index, ItemStack stack) {
        set(TOOL + index, stack);
    }

    /** What its kitchen counts as a tool (bakeware, a pot, a saucepan, a skillet): what its own use stands on top. */
    @Override
    public boolean isTool(ItemStack stack) {
        return isToolItem(stack);
    }

    /**
     * Cookware of Farmer's Delight that stands on a burner of the oven, as on a stove, though the oven's
     * kitchen does not count it as a tool: its skillet and cooking pot.
     */
    public static boolean standsOnBurner(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("farmersdelight") && (id.getPath().equals("skillet") || id.getPath().equals("cooking_pot"));
    }

    /** The tool place (burner) of the top face a hit lands on, as the oven's own use finds it from its facing. */
    public static int burnerAt(BlockState state, BlockPos pos, Vec3 hit) {
        double hx = hit.x - pos.getX();
        double hz = hit.z - pos.getZ();
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        double x = hx;
        double z = hz;
        switch (facing) {
            case NORTH -> {
                x = 1.0 - hx;
                z = 1.0 - hz;
            }
            case WEST -> {
                z = 1.0 - hx;
                x = hz;
            }
            case EAST -> {
                z = hx;
                x = 1.0 - hz;
            }
            default -> {
            }
        }
        return x < 0.5 ? (z < 0.5 ? 1 : 3) : (z < 0.5 ? 0 : 2);
    }

    public static boolean isToolItem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(UTENSILS);
    }

    @Override
    public boolean bakesEachPlace() {
        return true;
    }

    @Override
    public int perPlace() {
        return 1;
    }

    /** What it bakes: its own rule, which its menu and hoppers keep to as well. */
    @Override
    public boolean takes(ItemStack stack) {
        return !stack.isEmpty() && !bakedOf(stack).isEmpty();
    }

    /**
     * What {@code stack} bakes to in it, empty for what it does not bake: found as its {@code getSmeltingResult} finds it, recipe type
     * by type, but through the level's recipes, which a client knows too (its own asks the server's).
     */
    public ItemStack bakedOf(ItemStack stack) {
        Level level = entity.getLevel();
        return level == null || stack.isEmpty() ? ItemStack.EMPTY : bakedOf(level, stack);
    }

    public static ItemStack bakedOf(Level level, ItemStack stack) {
        SingleRecipeInput input = new SingleRecipeInput(stack.copyWithCount(1));
        for (RecipeType<?> type : bakingTypes()) {
            Optional<ItemStack> result = firstFor(level, type, input);
            if (result.isPresent() && (type != RecipeType.SMELTING || result.get().has(DataComponents.FOOD))) {
                return result.get();
            }
        }
        return ItemStack.EMPTY;
    }

    @SuppressWarnings("unchecked")
    private static Optional<ItemStack> firstFor(Level level, RecipeType<?> type, SingleRecipeInput input) {
        return KitchenRecipes.find(level, (RecipeType<Recipe<SingleRecipeInput>>) type, input)
                .map(holder -> holder.value().assemble(input))
                .filter(result -> !result.isEmpty());
    }

    /** Its own recipe type, null without it. */
    public static RecipeType<?> ovenRecipes() {
        return BuiltInRegistries.RECIPE_TYPE.getValue(OVEN_RECIPES);
    }

    /** Its recipe types in the order it tries them: its own, campfire, smoking, smelting. */
    private static List<RecipeType<?>> bakingTypes() {
        List<RecipeType<?>> types = new ArrayList<>();
        BuiltInRegistries.RECIPE_TYPE.getOptional(OVEN_RECIPES).ifPresent(types::add);
        types.add(RecipeType.CAMPFIRE_COOKING);
        types.add(RecipeType.SMOKING);
        types.add(RecipeType.SMELTING);
        return types;
    }

    private int cookTime(int place) {
        try {
            int[] times = (int[]) cookTimesField.get(entity);
            return times != null && place < times.length ? times[place] : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return 0;
        }
    }

    /** Ticks a place takes to bake, as its config has it. */
    public static int bakeTicks() {
        return Math.max(1, (int) Math.round(BAKE_TICKS * cookTimeMultiplier()));
    }

    private static double cookTimeMultiplier() {
        try {
            Object config = Class.forName(CONFIG).getMethod("getActive").invoke(null);
            return config.getClass().getField("ovenCookTimeMultiplier").getDouble(config);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1.0;
        }
    }

    /** -1 for a place it has baked: what lies there is done and waits for room in the output. */
    @Override
    public float progress(int place) {
        if (place(place).isEmpty()) {
            return 0.0F;
        }
        int time = cookTime(place);
        return time < 0 ? 1.0F : Math.min(1.0F, (float) time / bakeTicks());
    }

    @Override
    public int ticksLeft(int place) {
        if (place(place).isEmpty() || !takes(place(place))) {
            return 0;
        }
        int time = cookTime(place);
        return time < 0 ? 0 : Math.max(0, bakeTicks() - time);
    }

    @Override
    public boolean isHot() {
        try {
            return burnTimeField.getInt(entity) > 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return false;
        }
    }

    @Override
    public boolean waitsForHeat() {
        if (isHot()) {
            return false;
        }
        for (int place = 0; place < PLACES; place++) {
            if (ticksLeft(place) > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Baking> recipes(Level level) {
        return recipesOf(level);
    }

    /**
     * What it bakes, one thing into one, as its {@code getSmeltingResult} finds it: its own oven recipes, then campfire, smoking and
     * smelting recipes (smelting only into food), each input with the first type that bakes it. Campfire recipes are the book's
     * campfire tab. Listed once a recipe manager.
     */
    public static synchronized List<Baking> recipesOf(Level level) {
        Object manager = KitchenRecipes.source(level);
        if (manager == listedFor) {
            return listed;
        }
        List<Baking> recipes = new ArrayList<>();
        Set<Item> covered = new HashSet<>();
        int ticks = bakeTicks();
        for (RecipeType<?> type : bakingTypes()) {
            Set<Item> inputs = new HashSet<>();
            for (RecipeHolder<?> holder : KitchenRecipes.recipes(level)) {
                Recipe<?> recipe = holder.value();
                if (recipe.getType() != type || RecipeParts.ingredients(recipe).isEmpty() || RecipeParts.ingredients(recipe).get(0).isEmpty()) {
                    continue;
                }
                ItemStack result = RecipeParts.result(recipe);
                if (result.isEmpty() || type == RecipeType.SMELTING && !result.has(DataComponents.FOOD)) {
                    continue;
                }
                // An input an earlier type bakes already is baked by that one.
                Ingredient ingredient = RecipeParts.ingredients(recipe).get(0);
                boolean fresh = false;
                for (ItemStack item : RecipeParts.items(ingredient)) {
                    fresh |= !covered.contains(item.getItem());
                    inputs.add(item.getItem());
                }
                if (fresh) {
                    String kind = type == RecipeType.CAMPFIRE_COOKING ? OvenStation.KIND_CAMPFIRE : OvenStation.KIND_FOOD;
                    recipes.add(new Baking(holder.id().identifier(), List.of(ingredient), 0, 0, result.copy(), ticks, kind));
                }
            }
            covered.addAll(inputs);
        }
        recipes.sort(java.util.Comparator.comparing(recipe -> recipe.id().toString()));
        listedFor = manager;
        listed = List.copyOf(recipes);
        return listed;
    }

    /** As its own output slot: the item's own hook, then its event for what the player took out of the oven. */
    @Override
    @SuppressWarnings("unchecked")
    public void onTaken(Player player, ItemStack stack) {
        KitchenOven.super.onTaken(player, stack);
        Level level = entity.getLevel();
        if (level == null || stack.isEmpty()) {
            return;
        }
        try {
            // Since 26.x the event carries its own listeners: its EVENT mapper's invoker is a consumer of it.
            Class<?> type = Class.forName(SMELTED_EVENT);
            Object event = type.getConstructor(Player.class, Level.class, BlockPos.class, ItemStack.class)
                    .newInstance(player, level, entity.getBlockPos(), stack);
            Field mapper = type.getField("EVENT");
            // Called through the field's public interface: the mapper itself is one of Balm's own classes.
            Object invoker = mapper.getType().getMethod("invoker").invoke(mapper.get(null));
            ((Consumer<Object>) invoker).accept(event);
        } catch (ReflectiveOperationException | RuntimeException e) {
            HandsOnDelight.LOGGER.debug("Cooking for Blockheads' smelted event not fired", e);
        }
    }
}
