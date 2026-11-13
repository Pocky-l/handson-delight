package com.pockyl.handson_delight.assembly;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.pockyl.handson_delight.HandsOnDelight;

import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Modest Meals' meals of the crafting table, put together on the board: its sandwiches, salads, wraps, rolls, pies, cakes, pizzas and
 * milkshakes, which take the place of Farmer's Delight's burgers, salads and pies. One recipe of Modest Meals makes them all of
 * whatever it is given, so the board writes a dish for each of its meal types, read from the types' own files: what is laid first
 * and where (the bread above and below, the crust under the filling), and room for as many fillings as the type takes. Each named
 * dish a type makes of given fillings (a hamburger, an apple pie) is a dish of its own, with a page in the board's book. The dishes
 * are still made by Modest Meals' recipe, so a meal carries what went into it.
 *
 * <p>Where any filling may lie, a dish's JSON holds the marker {@link #ANY}: the board puts in the list of everything Modest Meals
 * takes as an ingredient, which the server sends once beside the dishes ({@link #entry}).
 */
public final class ModestMeals {
    /** In a dish's JSON: any of Modest Meals' ingredients may lie here. */
    static final String ANY = "handson_delight:meal_ingredient";
    /** In a dish's JSON: a dish of free fillings, with no page in the book or in recipe viewers. */
    static final String HIDDEN = "handson_delight:hidden";
    /** The entry of the board's book that lists Modest Meals' ingredients, by item id. */
    static final String ITEMS = "meal_ingredients";

    private static final ResourceLocation SERIALIZER = ResourceLocation.fromNamespaceAndPath("modest_meals", "meal_crafting");
    private static final String ANY_INGREDIENT = "modest_meals:any_ingredient";
    private static final String TYPES = "modest_meals/meal_types";
    private static final String CRAFTING_TABLE = "crafting_table";
    private static final String PROFILES = "com.evandev.modest_meals.food.ingredient.IngredientProfileManager";
    /** Symbols for the fillings of a shaped dish: Modest Meals' own shapes use capitals and {@code #}. */
    private static final String FILLINGS = "abcdefghi";
    private static final int GRID = 9;

    private static volatile RecipeHolder<?> recipe;
    private static volatile List<JsonObject> types = List.of();

    private ModestMeals() {
    }

    /** Finds Modest Meals' recipe among those the manager loaded, and its meal types of the crafting table. */
    static void load(ResourceManager resources, Map<ResourceLocation, RecipeHolder<?>> loaded) {
        RecipeHolder<?> found = null;
        for (RecipeHolder<?> holder : loaded.values()) {
            if (holder.value() instanceof CraftingRecipe
                    && SERIALIZER.equals(BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer()))) {
                found = holder;
                break;
            }
        }
        List<JsonObject> read = new ArrayList<>();
        if (found != null) {
            for (Map.Entry<ResourceLocation, Resource> file
                    : resources.listResources(TYPES, path -> path.getPath().endsWith(".json")).entrySet()) {
                try (Reader reader = file.getValue().openAsReader()) {
                    JsonObject type = JsonParser.parseReader(reader).getAsJsonObject();
                    if (CRAFTING_TABLE.equals(string(type, "station", CRAFTING_TABLE))) {
                        read.add(type);
                    }
                } catch (Exception broken) {
                    HandsOnDelight.LOGGER.warn("Could not read Modest Meals' meal type {} for the cutting board", file.getKey(), broken);
                }
            }
        }
        recipe = found;
        types = List.copyOf(read);
    }

    /** The id of Modest Meals' recipe, which a player's crafting grid no longer makes; empty without Modest Meals. */
    static Optional<ResourceLocation> recipeId() {
        RecipeHolder<?> holder = recipe;
        return holder == null || types.isEmpty() ? Optional.empty() : Optional.of(holder.id());
    }

    /** The board's dishes of Modest Meals' meals, the named ones first; none without Modest Meals. */
    static List<AssemblyRecipes.Assembly> dishes(List<ResourceLocation> ingredients) {
        RecipeHolder<?> holder = recipe;
        if (holder == null || !(holder.value() instanceof CraftingRecipe crafting) || types.isEmpty() || ingredients.isEmpty()) {
            return List.of();
        }
        HolderLookup.Provider registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        JsonArray any = choices(ingredients);
        List<AssemblyRecipes.Assembly> named = new ArrayList<>();
        List<AssemblyRecipes.Assembly> free = new ArrayList<>();
        Set<ResourceLocation> ids = new HashSet<>();
        for (JsonObject type : types) {
            try {
                Meal meal = Meal.of(type);
                if (meal == null) {
                    continue;
                }
                for (JsonElement element : array(type, "item_overrides")) {
                    JsonObject dish = meal.named(element.getAsJsonObject());
                    if (dish != null) {
                        ResourceLocation result = ResourceLocation.parse(string(element.getAsJsonObject(), "result", ""));
                        add(named, unique(ids, HandsOnDelight.id("modest_meals/" + result.getNamespace() + "/" + result.getPath())),
                                dish, any, crafting, registries);
                    }
                }
                for (int count = meal.least; count <= meal.most; count++) {
                    add(free, unique(ids, HandsOnDelight.id("modest_meals/" + meal.path + "/" + count)), meal.free(count), any, crafting,
                            registries);
                }
            } catch (RuntimeException broken) {
                HandsOnDelight.LOGGER.warn("Could not put Modest Meals' meal type {} on the cutting board", string(type, "id", "?"),
                        broken);
            }
        }
        List<AssemblyRecipes.Assembly> all = new ArrayList<>(named);
        all.addAll(free);
        return all;
    }

    /** Everything Modest Meals takes as an ingredient, asked of Modest Meals itself; any food when it cannot be asked. */
    static List<ResourceLocation> ingredients() {
        RecipeHolder<?> holder = recipe;
        if (holder == null || types.isEmpty()) {
            return List.of();
        }
        List<ResourceLocation> found = new ArrayList<>();
        try {
            Class<?> profiles = Class.forName(PROFILES, true, holder.value().getClass().getClassLoader());
            Method isIngredient = profiles.getMethod("isIngredient", ItemStack.class);
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR && (Boolean) isIngredient.invoke(null, new ItemStack(item))) {
                    found.add(BuiltInRegistries.ITEM.getKey(item));
                }
            }
            return found;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException missing) {
            HandsOnDelight.LOGGER.warn("Could not ask Modest Meals for its ingredients; any food goes into its meals on the board", missing);
        }
        found.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            if (new ItemStack(item).has(DataComponents.FOOD)) {
                found.add(BuiltInRegistries.ITEM.getKey(item));
            }
        }
        return found;
    }

    /** The book's entry that lists {@code ingredients} for the clients. */
    static CompoundTag entry(List<ResourceLocation> ingredients) {
        ListTag items = new ListTag();
        ingredients.forEach(item -> items.add(StringTag.valueOf(item.toString())));
        CompoundTag entry = new CompoundTag();
        entry.put(ITEMS, items);
        return entry;
    }

    /** The ingredients an entry of the book lists, or null when it is a dish. */
    static List<ResourceLocation> read(CompoundTag entry) {
        if (!entry.contains(ITEMS, Tag.TAG_LIST)) {
            return null;
        }
        ListTag items = entry.getList(ITEMS, Tag.TAG_STRING);
        List<ResourceLocation> read = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(items.getString(i));
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                read.add(id);
            }
        }
        return read;
    }

    /** The JSON choices of {@code items}, for the marker {@link #ANY}. */
    static JsonArray choices(List<ResourceLocation> items) {
        JsonArray choices = new JsonArray();
        for (ResourceLocation item : items) {
            JsonObject choice = new JsonObject();
            choice.addProperty("item", item.toString());
            choices.add(choice);
        }
        return choices;
    }

    /** {@code json} with every marker {@link #ANY} replaced by {@code any}. */
    static JsonElement expand(JsonElement json, JsonArray any) {
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has(ANY)) {
                return any;
            }
            JsonObject copy = new JsonObject();
            object.entrySet().forEach(entry -> copy.add(entry.getKey(), expand(entry.getValue(), any)));
            return copy;
        }
        if (json.isJsonArray()) {
            JsonArray copy = new JsonArray();
            json.getAsJsonArray().forEach(element -> copy.add(expand(element, any)));
            return copy;
        }
        return json;
    }

    private static void add(List<AssemblyRecipes.Assembly> to, ResourceLocation id, JsonObject json, JsonArray any, CraftingRecipe crafting,
            HolderLookup.Provider registries) {
        if (json == null) {
            return;
        }
        // Read as the crafting table's own recipe for its ingredients and places; made by Modest Meals' recipe, laid out in those places.
        AssemblyRecipes.parse(id, json, any, registries).ifPresent(dish -> to.add(new AssemblyRecipes.Assembly(dish.id(), crafting,
                dish.ingredients(), dish.slots(), dish.base(), dish.kind(), dish.result(), dish.json())));
    }

    private static ResourceLocation unique(Set<ResourceLocation> taken, ResourceLocation id) {
        ResourceLocation free = id;
        for (int i = 2; !taken.add(free); i++) {
            free = id.withSuffix("_" + i);
        }
        return free;
    }

    /**
     * An ingredient of a meal type as Modest Meals writes it: an item, a {@code #tag}, choices joined by {@code |}. Null when none of
     * them is in this game.
     */
    private static JsonElement ingredient(String written) {
        JsonArray choices = new JsonArray();
        for (String choice : written.split("\\|")) {
            String name = choice.trim();
            JsonObject json = new JsonObject();
            if (name.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(name.substring(1));
                if (tag == null || BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tag)).map(set -> set.size() == 0).orElse(true)) {
                    continue;
                }
                json.addProperty("tag", tag.toString());
            } else {
                ResourceLocation item = ResourceLocation.tryParse(name);
                if (item == null || !BuiltInRegistries.ITEM.containsKey(item)) {
                    continue;
                }
                json.addProperty("item", item.toString());
            }
            choices.add(json);
        }
        return choices.isEmpty() ? null : choices.size() == 1 ? choices.get(0) : choices;
    }

    private static JsonObject marker() {
        JsonObject any = new JsonObject();
        any.addProperty(ANY, true);
        return any;
    }

    private static JsonObject result(String item, int count) {
        JsonObject result = new JsonObject();
        result.addProperty("id", item);
        result.addProperty("count", count);
        return result;
    }

    private static List<JsonElement> array(JsonObject json, String key) {
        List<JsonElement> list = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            json.getAsJsonArray(key).forEach(list::add);
        }
        return list;
    }

    private static String string(JsonObject json, String key, String otherwise) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : otherwise;
    }

    private static int number(JsonObject json, String key, int otherwise) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : otherwise;
    }

    /**
     * A meal type of the crafting table: its item, the shape it is laid in or what it is laid on, and how many fillings it takes.
     * {@code pattern} holds {@code #} where a filling may go and the shape's own symbols elsewhere; a shapeless type has no pattern
     * and its {@code base} instead.
     */
    private record Meal(String path, String item, List<String> pattern, JsonObject key, List<JsonElement> base, int least, int most) {
        static Meal of(JsonObject type) {
            String item = string(type, "item", null);
            ResourceLocation id = ResourceLocation.tryParse(string(type, "id", ""));
            if (item == null || id == null || !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(item))) {
                return null;
            }
            List<String> pattern = new ArrayList<>();
            JsonObject key = new JsonObject();
            List<JsonElement> base = new ArrayList<>();
            int cells;
            if (type.has("shape")) {
                JsonObject shape = type.getAsJsonObject("shape");
                JsonObject written = shape.getAsJsonObject("key");
                cells = 0;
                for (JsonElement row : shape.getAsJsonArray("pattern")) {
                    StringBuilder line = new StringBuilder();
                    for (char symbol : row.getAsString().toCharArray()) {
                        String value = symbol == ' ' ? null : string(written, String.valueOf(symbol), null);
                        if (value == null) {
                            line.append(' ');
                        } else if (ANY_INGREDIENT.equals(value)) {
                            line.append('#');
                            cells++;
                        } else {
                            JsonElement pinned = ingredient(value);
                            if (pinned == null) {
                                return null;
                            }
                            char own = Character.toUpperCase(symbol);
                            key.add(String.valueOf(own), pinned);
                            line.append(own);
                        }
                    }
                    pattern.add(line.toString());
                }
            } else {
                for (JsonElement element : array(type, "base")) {
                    JsonElement ingredient = element.getAsJsonObject().get("ingredient");
                    if (ingredient == null) {
                        return null;
                    }
                    base.add(ingredient);
                }
                cells = GRID - base.size();
            }
            int most = Math.min(cells, number(type, "max_ingredients", cells));
            int least = Math.max(1, number(type, "min_ingredients", 1));
            return least > most ? null : new Meal(id.getPath(), item, pattern, key, base, least, most);
        }

        /** The dish of {@code count} free fillings. */
        JsonObject free(int count) {
            List<JsonElement> fillings = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                fillings.add(marker());
            }
            JsonObject json = dish(fillings, result(item, 1));
            json.addProperty(HIDDEN, true);
            return json;
        }

        /** The dish a type makes of given fillings ({@code item_overrides}); null when it cannot be laid here. */
        JsonObject named(JsonObject override) {
            String result = string(override, "result", null);
            if (result == null || override.has("base") || !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(result))) {
                return null;
            }
            List<JsonElement> fillings = new ArrayList<>();
            for (JsonElement written : array(override, "ingredients")) {
                JsonElement filling = ingredient(written.getAsString());
                if (filling == null) {
                    return null;
                }
                fillings.add(filling);
            }
            if (fillings.isEmpty() || fillings.size() > most) {
                return null;
            }
            return dish(fillings, result(result, number(override, "count", 1)));
        }

        /** A shaped dish with each filling in the next free place of the pattern, or a shapeless one on its base. */
        private JsonObject dish(List<JsonElement> fillings, JsonObject result) {
            JsonObject json = new JsonObject();
            json.add("result", result);
            if (pattern.isEmpty()) {
                json.addProperty("type", "minecraft:crafting_shapeless");
                JsonArray ingredients = new JsonArray();
                base.forEach(ingredients::add);
                fillings.forEach(ingredients::add);
                json.add("ingredients", ingredients);
                return json;
            }
            json.addProperty("type", "minecraft:crafting_shaped");
            JsonObject keys = key.deepCopy();
            JsonArray rows = new JsonArray();
            int next = 0;
            for (String row : pattern) {
                StringBuilder line = new StringBuilder();
                for (char symbol : row.toCharArray()) {
                    if (symbol != '#') {
                        line.append(symbol);
                    } else if (next < fillings.size()) {
                        char filling = FILLINGS.charAt(next);
                        keys.add(String.valueOf(filling), fillings.get(next));
                        line.append(filling);
                        next++;
                    } else {
                        line.append(' ');
                    }
                }
                rows.add(line.toString());
            }
            json.add("pattern", rows);
            json.add("key", keys);
            return json;
        }
    }
}
