package com.pockyl.handson_delight.assembly;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Food that Farmer's Delight and its addons make in the crafting table and that is put together on the cutting board instead. The
 * recipes are found when the server loads them and kept here as they were, with the same ingredients and result, so that an addon's
 * sandwich needs nothing from us. They stay among the server's recipes for automation (Create's crafters, AE2, Refined Storage): only
 * a player's own crafting grid does not make them, and no player learns them for the recipe book.
 *
 * <p>So far the board builds what lies on a slice of bread: sandwiches, burgers, wraps. Other dishes stay in the crafting table until
 * the board can make them.
 */
public final class AssemblyRecipes {
    private static final String[] CONDITION_KEYS = {"conditions", "neoforge:conditions", "fabric:load_conditions"};

    private static volatile List<Assembly> recipes = List.of();
    private static volatile Set<ResourceLocation> ids = Set.of();
    /** The recipes as the clients get them for the board's book: a dedicated server sends them nowhere else. */
    private static volatile ListTag book = new ListTag();

    private AssemblyRecipes() {
    }

    /** A dish put together on the board: everything lies on {@code base}, the ingredient that must come first. */
    public record Assembly(ResourceLocation id, CraftingRecipe recipe, int base, JsonObject json) {
        public List<Ingredient> ingredients() {
            return recipe.getIngredients();
        }
    }

    public static List<Assembly> all() {
        return recipes;
    }

    /** Every recipe as its id and JSON, for {@link #read}. */
    public static ListTag book() {
        return book;
    }

    /**
     * The recipes {@link #book} lists, read where they are needed: on a client its tags are bound and its items known. Their
     * ingredients and results name items and tags only, which the built-in registries know.
     */
    public static List<Assembly> read(ListTag list) {
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        List<Assembly> read = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            try {
                JsonObject json = JsonParser.parseString(entry.getString("json")).getAsJsonObject();
                if (id != null && parse(json, registries) instanceof CraftingRecipe crafting) {
                    read.add(new Assembly(id, crafting, baseIndex(json), json));
                }
            } catch (RuntimeException broken) {
                HandsOnDelight.LOGGER.warn("Could not read the board's recipe {}", id, broken);
            }
        }
        return read;
    }

    /**
     * Finds the recipes of {@code json} that the board makes; all of them go on to the recipe manager. Their ingredients are only read
     * as JSON here: item tags are not bound yet while recipes load.
     */
    public static void collect(Map<ResourceLocation, JsonElement> json, Map<ResourceLocation, JsonObject> found) {
        json.forEach((id, element) -> {
            if (element.isJsonObject() && isAssembled(id, element.getAsJsonObject())) {
                found.put(id, element.getAsJsonObject());
            }
        });
    }

    /** Whether the recipe {@code id} is made on the board: a player's crafting grid does not make it. */
    public static boolean isMadeOnTheBoard(ResourceLocation id) {
        return ids.contains(id);
    }

    /** Reads the recipes {@link #split} took, after the recipe manager has read its own. */
    public static void load(Map<ResourceLocation, JsonObject> taken, HolderLookup.Provider registries) {
        List<Assembly> loaded = new ArrayList<>();
        taken.forEach((id, json) -> {
            try {
                Recipe<?> recipe = parse(json, registries);
                if (recipe instanceof CraftingRecipe crafting) {
                    loaded.add(new Assembly(id, crafting, baseIndex(json), json));
                }
            } catch (RuntimeException broken) {
                HandsOnDelight.LOGGER.error("Could not read the recipe {} for the cutting board", id, broken);
            }
        });
        recipes = List.copyOf(loaded);
        ids = Set.copyOf(taken.keySet());
        ListTag list = new ListTag();
        for (Assembly assembly : loaded) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", assembly.id().toString());
            entry.putString("json", assembly.json().toString());
            list.add(entry);
        }
        book = list;
        HandsOnDelight.LOGGER.info("{} crafting recipes of Farmer's Delight are put together on the cutting board, by players", loaded.size());
    }

    /** What {@code RecipeManager.fromJson} does, which is not public. */
    private static Recipe<?> parse(JsonObject json, HolderLookup.Provider registries) {
        return Recipe.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow(JsonParseException::new);
    }

    private static boolean isAssembled(ResourceLocation id, JsonObject json) {
        if (!"minecraft:crafting_shapeless".equals(string(json, "type")) || !json.has("ingredients") || !json.has("result")) {
            return false;
        }
        for (String key : CONDITION_KEYS) {
            if (json.has(key)) {
                return false;
            }
        }
        if (!id.getNamespace().equals(FarmersDelightContent.MOD_ID) && !Platform.INSTANCE.isFarmersDelightAddon(id.getNamespace())) {
            return false;
        }
        if (!isFood(json.get("result"))) {
            return false;
        }
        return baseIndex(json) >= 0 && !hasContainer(json.getAsJsonArray("ingredients"));
    }

    private static boolean isFood(JsonElement result) {
        String name = result.isJsonObject() ? Objects.requireNonNullElse(string(result.getAsJsonObject(), "id"), string(result.getAsJsonObject(), "item"))
                : result.isJsonPrimitive() ? result.getAsString() : null;
        ResourceLocation item = name == null ? null : ResourceLocation.tryParse(name);
        return item != null && BuiltInRegistries.ITEM.containsKey(item) && BuiltInRegistries.ITEM.get(item).components().has(DataComponents.FOOD);
    }

    /** Where the bread is among the ingredients, -1 without any. */
    private static int baseIndex(JsonObject json) {
        JsonArray ingredients = json.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            if (names(ingredients.get(i), "bread")) {
                return i;
            }
        }
        return -1;
    }

    /** A dish served in a bowl or a bottle is plated, not stacked: that comes later. */
    private static boolean hasContainer(JsonArray ingredients) {
        for (JsonElement ingredient : ingredients) {
            if (names(ingredient, "bowl") || names(ingredient, "bottle") || names(ingredient, "bucket")) {
                return true;
            }
        }
        return false;
    }

    /** Whether an ingredient, or one of its choices, is an item or a tag with {@code word} in its path. */
    private static boolean names(JsonElement ingredient, String word) {
        if (ingredient.isJsonArray()) {
            for (JsonElement choice : ingredient.getAsJsonArray()) {
                if (names(choice, word)) {
                    return true;
                }
            }
            return false;
        }
        if (!ingredient.isJsonObject()) {
            return false;
        }
        JsonObject object = ingredient.getAsJsonObject();
        String name = object.has("item") ? string(object, "item") : string(object, "tag");
        ResourceLocation id = name == null ? null : ResourceLocation.tryParse(name);
        return id != null && id.getPath().contains(word);
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /** Whether {@code item} starts a dish: it is the bread of one. */
    public static boolean isBase(ItemStack item) {
        for (Assembly assembly : recipes) {
            if (assembly.ingredients().get(assembly.base()).test(item)) {
                return true;
            }
        }
        return false;
    }

    /** Whether what lies on the board, bread first, is all part of some dish. */
    public static boolean fits(List<ItemStack> layers) {
        for (Assembly assembly : recipes) {
            if (matches(assembly, layers, false)) {
                return true;
            }
        }
        return false;
    }

    /** Whether more could still go on the layers for a bigger dish: then a finished one waits for the cook's word. */
    public static boolean canGrow(List<ItemStack> layers) {
        for (Assembly assembly : recipes) {
            if (assembly.ingredients().size() > layers.size() && matches(assembly, layers, false)) {
                return true;
            }
        }
        return false;
    }

    /** The dish that what lies on the board makes, all of it and nothing more. */
    public static Optional<Assembly> complete(List<ItemStack> layers) {
        for (Assembly assembly : recipes) {
            if (matches(assembly, layers, true)) {
                return Optional.of(assembly);
            }
        }
        return Optional.empty();
    }

    /** Every item takes an ingredient of its own, the first one the bread; {@code all}: every ingredient is taken. */
    static boolean matches(Assembly assembly, List<ItemStack> layers, boolean all) {
        List<Ingredient> ingredients = assembly.ingredients();
        if (layers.isEmpty() || layers.size() > ingredients.size() || all && layers.size() != ingredients.size()) {
            return false;
        }
        if (!ingredients.get(assembly.base()).test(layers.get(0))) {
            return false;
        }
        boolean[] used = new boolean[ingredients.size()];
        used[assembly.base()] = true;
        return assign(ingredients, layers, 1, used);
    }

    private static boolean assign(List<Ingredient> ingredients, List<ItemStack> layers, int next, boolean[] used) {
        if (next == layers.size()) {
            return true;
        }
        for (int i = 0; i < ingredients.size(); i++) {
            if (!used[i] && ingredients.get(i).test(layers.get(next))) {
                used[i] = true;
                if (assign(ingredients, layers, next + 1, used)) {
                    return true;
                }
                used[i] = false;
            }
        }
        return false;
    }
}
