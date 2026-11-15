package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.mixin.RecipeManagerAccessor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Youkai's Feasts' pot recipes, read from a level's recipes, so that an empty pot (a block without a block entity) has its book too:
 * what goes in, the dish, the time, and the pot the dish is served in, which is the pot it needs. Its recipe classes build on the L2
 * libraries nested in its jar, so they are read by name.
 */
public final class YoukaiPotDishes {
    private static final ResourceLocation COOKING = ResourceLocation.tryBuild(KitchenYoukaiPot.MOD_ID, "pot_cooking");
    /**
     * Per recipe manager: the client's and the server's of a single-player game are two. Read again when the manager's map of recipes
     * is a new one (a reload, the server's recipes reaching a client).
     */
    private static final Map<RecipeManager, Cached> READ = new WeakHashMap<>();
    private static boolean warned;

    private YoukaiPotDishes() {
    }

    public static List<KitchenYoukaiPot.Dish> of(Level level) {
        if (level == null) {
            return List.of();
        }
        RecipeManager manager = level.getRecipeManager();
        Map<?, ?> recipes = ((RecipeManagerAccessor) manager).handson$byName();
        synchronized (READ) {
            Cached cached = READ.get(manager);
            if (cached == null || cached.from() != recipes) {
                cached = new Cached(recipes, read(manager));
                READ.put(manager, cached);
            }
            return cached.dishes();
        }
    }

    private record Cached(Map<?, ?> from, List<KitchenYoukaiPot.Dish> dishes) {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<KitchenYoukaiPot.Dish> read(RecipeManager manager) {
        List<KitchenYoukaiPot.Dish> dishes = new ArrayList<>();
        Optional<RecipeType<?>> type = BuiltInRegistries.RECIPE_TYPE.getOptional(COOKING);
        if (type.isEmpty()) {
            return dishes;
        }
        for (RecipeHolder<?> holder : (List<RecipeHolder<?>>) (List) manager.getAllRecipesFor((RecipeType) type.get())) {
            Object recipe = holder.value();
            try {
                List<Ingredient> input = List.copyOf((List<Ingredient>) call(recipe, "getInput"));
                ItemStack result = ((ItemStack) call(recipe, "getResult")).copy();
                int ticks = (Integer) call(recipe, "getProcessTime");
                // Its dishes name their pot as what crafting leaves of them (the item's own remainder, set by its properties).
                Item pot = result.getItem().getCraftingRemainingItem();
                if (!result.isEmpty() && pot != null && pot != Items.AIR && !input.isEmpty()) {
                    dishes.add(new KitchenYoukaiPot.Dish(holder.id(), result, input, ticks, pot));
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                if (!warned) {
                    warned = true;
                    HandsOnDelight.LOGGER.warn("A pot recipe of Youkai's Feasts is not what the station knows: {}", holder.id(), e);
                }
            }
        }
        return List.copyOf(dishes);
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }
}
