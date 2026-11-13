package com.pockyl.handson_delight.assembly;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CakeBlock;
import vectorwing.farmersdelight.common.block.FeastBlock;
import vectorwing.farmersdelight.common.block.PieBlock;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.KitchenTags;
import com.pockyl.handson_delight.mixin.CraftingMenuAccessor;
import com.pockyl.handson_delight.mixin.InventoryMenuAccessor;
import com.pockyl.handson_delight.mixin.TransientCraftingContainerAccessor;
import com.pockyl.handson_delight.platform.Platform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Food that Farmer's Delight and its addons make in the crafting table and that is put together on the cutting board instead. The
 * recipes are found when the server loads them and kept here as they were, with the same ingredients and result, so that an addon's
 * dish needs nothing from us. They stay among the server's recipes for automation (Create's crafters, AE2, Refined Storage): only
 * a player's own crafting grid does not make them, and no player learns them for the recipe book.
 *
 * <p>The rule: a crafting recipe of Farmer's Delight or of a mod that needs it, made of more than one kind of thing, whose result is
 * food, a drink made of food, a feast, a pie or a cake. Slices back into a whole pie, crates opened and leaves pressed back into a
 * cabbage are one kind of thing and stay in the crafting table. Item tags {@link #ASSEMBLED} and {@link #NOT_ASSEMBLED} on the
 * result correct the rule.
 */
public final class AssemblyRecipes {
    /** Results whose recipes are put together on the board although the rule would leave them in the crafting table. */
    public static final TagKey<Item> ASSEMBLED = TagKey.create(Registries.ITEM, HandsOnDelight.id("assembled"));
    /** Results whose recipes stay in the crafting table although the rule would take them. */
    public static final TagKey<Item> NOT_ASSEMBLED = TagKey.create(Registries.ITEM, HandsOnDelight.id("not_assembled"));
    /**
     * Materials of a workshop, not of a kitchen: a recipe with one of them stays in the crafting table, as a golden carrot does (an
     * addon's golden egg, a copper carrot, a dragon egg of obsidian).
     */
    public static final TagKey<Item> WORKSHOP = TagKey.create(Registries.ITEM, HandsOnDelight.id("workshop"));

    /** Farmer's Delight's dough from water is a recipe type of its own, with nothing to read: the board knows what it takes. */
    private static final String DOUGH = FarmersDelightContent.id("dough").toString();
    private static final String SHAPELESS = "minecraft:crafting_shapeless";
    private static final String SHAPED = "minecraft:crafting_shaped";
    /**
     * Forge's choice among recipes by their conditions: addons put food in it (Delightful wraps its own and other addons' dishes, burgers
     * from a bun when there is one). The board reads the one the recipe manager loaded.
     */
    private static final String CONDITIONAL = "forge:conditional";
    private static final int GRID = 3;
    /**
     * Culinary Compat's cuts of several things at once: Pam's HarvestCraft crafts that need its cutting board, made on Farmer's
     * Delight's board with a knife instead. They come into the server's recipe manager after it loaded, when the server starts.
     */
    private static final ResourceLocation CUT_TOGETHER = ResourceLocation.tryParse("culinarycompat:multi_cutting");
    /** Culinary Compat cuts them with any knife. */
    private static final String KNIVES = "forge:tools/knives";
    /** In a dish's JSON: what the dish is cut together with. */
    private static final String TOOL = HandsOnDelight.id("tool").toString();

    /** Every recipe that may be put together, as the recipe manager loaded it; the tags choose among them once they are bound. */
    private static volatile List<Assembly> candidates = List.of();
    /** The dishes cut together, read from the recipe manager after it loaded: see {@link #CUT_TOGETHER}. */
    private static volatile List<Assembly> cutTogether = List.of();
    private static volatile Chosen chosen;

    private AssemblyRecipes() {
    }

    /** How a dish is finished once its layers make it: what the cook's hands would do. */
    public enum Finish {
        /** Pressed together from the top: a pie into its crust, a juice, a cake. */
        PRESS,
        /** Tossed in the bowl: a salad. */
        TOSS,
        /** Rolled up: a kelp roll, a wrap round its filling. */
        ROLL,
        /** Pushed along the stick, from its tip down. */
        SKEWER,
        /** Kneaded. */
        KNEAD,
        /** Laid in the bowl, each piece in its place: a plated meal, a feast. */
        ARRANGE,
        /** The bread is split and its top half closes the filling: a sandwich, a burger. */
        SANDWICH,
        /** Rice pressed into a block, the slices laid along its top: nigiri. */
        SHAPE,
        /** Fruit frozen together round the top of the stick: a popsicle. */
        FREEZE,
        /** Cut through together with the knife: a dish of Pam's HarvestCraft that Culinary Compat cuts on the board. */
        CHOP
    }

    /**
     * What goes down first, found by name among the ingredients in this order: it says what kind of dish it is. A recipe with none
     * of them starts with its first ingredient. Wheat, its dough or flour is kneaded (a cake an addon makes from dough).
     */
    public enum Base {
        BOWL("bowl", Finish.TOSS),
        BOTTLE("glass_bottle", Finish.PRESS),
        /** Bread, or a burger already made that gets more on top: its bread opens and closes again. */
        BREAD(Finish.PRESS, "bread", "burger"),
        /** A tortilla or a flatbread: what lies on it is rolled up in it, before rice or anything else in the recipe counts as the base. */
        TORTILLA(Finish.ROLL, "tortilla", "flatbread"),
        /** Toast, with what is spread on it. */
        TOAST("toast", Finish.PRESS),
        CRUST("pie_crust", Finish.PRESS),
        /** A stick, or a blaze rod for a Nether skewer; packs often name sticks by their tag, wooden rods. */
        STICK(Finish.SKEWER, "stick", "rods/wooden", "blaze_rod"),
        KELP("kelp", Finish.ROLL),
        RICE("rice", Finish.ROLL),
        WHEAT(Finish.KNEAD, "wheat", "dough", "flour"),
        FIRST(null, Finish.PRESS);

        private final String[] words;
        private final Finish finish;

        Base(String word, Finish finish) {
            this(finish, word == null ? new String[0] : new String[] {word});
        }

        Base(Finish finish, String... words) {
            this.words = words;
            this.finish = finish;
        }

        public Finish finish() {
            return finish;
        }
    }

    /**
     * A dish put together on the board: everything lies on ingredient {@code base}, which goes down first. {@code ingredients} are the
     * recipe's own, without the empty places of a shaped one; {@code slots}: where each lies in a 3 x 3 crafting grid, so the recipe
     * itself can make the dish and say what is left (a bucket, a bottle). {@code json}: the recipe as the clients read it. {@code tool}:
     * what the layers are cut through with to make the dish, worn by every dish made; null for a dish made by hand.
     */
    public record Assembly(ResourceLocation id, CraftingRecipe recipe, List<Ingredient> ingredients, int[] slots, int base, Base kind,
            ItemStack result, JsonObject json, Ingredient tool) {
        /**
         * What the cook's hands do: a bowl of plants is tossed and anything else in a bowl arranged; a pie or a cake is pressed
         * whatever goes down first; a stick with ice freezes, bread closes over the filling or is rolled round it, rice is shaped, or
         * topped where its dish is tagged arranged (bibimbap).
         * What is cut together is chopped through.
         */
        public Finish finish() {
            if (tool != null) {
                return Finish.CHOP;
            }
            if (kind == Base.BOWL) {
                return isTossed() ? Finish.TOSS : Finish.ARRANGE;
            }
            if (isBlock()) {
                return Finish.PRESS;
            }
            if (BuiltInRegistries.ITEM.getKey(result.getItem()).getPath().endsWith("dough")) {
                return Finish.KNEAD;
            }
            return switch (kind) {
                case STICK -> result.is(KitchenTags.FROZEN) || ingredients.stream().anyMatch(in -> in.test(new ItemStack(Items.ICE)))
                        ? Finish.FREEZE : Finish.SKEWER;
                case BREAD -> isWrap() ? Finish.ROLL : Finish.SANDWICH;
                case RICE -> result.is(KitchenTags.ARRANGED) ? Finish.ARRANGE : Finish.SHAPE;
                default -> kind.finish();
            };
        }

        /** Bread rolled round its filling: tagged so, or named a wrap or a burrito, as addons name theirs. */
        private boolean isWrap() {
            String name = BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
            return result.is(KitchenTags.WRAPPED) || name.contains("wrap") || name.contains("burrito");
        }

        /** A salad: tagged so, or, untagged, a bowl of nothing but plants. Meat, fish and eggs are laid in their places. */
        private boolean isTossed() {
            if (result.is(KitchenTags.TOSSED)) {
                return true;
            }
            if (result.is(KitchenTags.ARRANGED) || isBlock()) {
                return false;
            }
            for (int i = 0; i < ingredients.size(); i++) {
                ItemStack[] choices = ingredients.get(i).getItems();
                if (i == base || choices.length == 0) {
                    continue;
                }
                ItemStack food = choices[0];
                FoodProperties properties = food.getItem().getFoodProperties();
                boolean meat = properties != null && properties.isMeat();
                String name = BuiltInRegistries.ITEM.getKey(food.getItem()).getPath();
                if (meat || food.is(ItemTags.FISHES) || food.is(KitchenTags.CRACKED) || name.contains("egg") || name.contains("bacon")) {
                    return false;
                }
            }
            return true;
        }

        /** Whether the dish is a block put down whole: a feast, a pie, a cake. */
        public boolean isBlock() {
            return result.getItem() instanceof BlockItem;
        }
    }

    /** The recipes chosen among the candidates, and the list the clients get. */
    private record Chosen(List<Assembly> all, Set<ResourceLocation> ids, ListTag book) {
    }

    /** Every recipe that may be put together, before the tags choose: for the recipe audit. */
    public static List<Assembly> candidates() {
        return candidates;
    }

    public static List<Assembly> all() {
        return chosen().all();
    }

    /** Every recipe as its id and JSON, for {@link #read}. */
    public static ListTag book() {
        return chosen().book();
    }

    /** Whether the recipe {@code id} is made on the board: a player's crafting grid does not make it. */
    public static boolean isMadeOnTheBoard(ResourceLocation id) {
        return chosen().ids().contains(id);
    }

    /**
     * Whether {@code grid} is a player's crafting table or inventory grid, which does not make {@code recipe}: the board does.
     * Automation crafts with grids of its own (Create's crafters, AE2's assemblers, Refined Storage) and goes on making it, as without
     * this mod. So does a player whose client lacks the mod: it cannot open the board, the crafting table stays theirs.
     */
    public static boolean notInThisGrid(Recipe<?> recipe, Container grid) {
        if (!(grid instanceof TransientCraftingContainer table) || !HandsOnConfig.removeCraftingRecipes()
                || !isMadeOnTheBoard(recipe.getId())) {
            return false;
        }
        AbstractContainerMenu menu = ((TransientCraftingContainerAccessor) table).handson$menu();
        Player player = menu instanceof CraftingMenu crafting ? ((CraftingMenuAccessor) crafting).handson$player()
                : menu instanceof InventoryMenu inventory ? ((InventoryMenuAccessor) inventory).handson$owner() : null;
        return player != null && !(player instanceof ServerPlayer server && !Platform.INSTANCE.hasThisMod(server));
    }

    /**
     * Chosen when first asked after the recipes or the tags changed: while the recipes load, the item tags are not bound yet. The
     * server asks first when a player joins, or when it sends the reloaded recipes, after the tags.
     */
    private static Chosen chosen() {
        Chosen now = chosen;
        if (now == null) {
            synchronized (AssemblyRecipes.class) {
                now = chosen;
                if (now == null) {
                    List<Assembly> from = new ArrayList<>(candidates);
                    from.addAll(cutTogether);
                    now = choose(from);
                    chosen = now;
                }
            }
        }
        return now;
    }

    /** The tags may have changed: the recipes are chosen again when next asked. */
    public static void tagsChanged() {
        chosen = null;
    }

    private static Chosen choose(List<Assembly> from) {
        List<Assembly> all = new ArrayList<>();
        ListTag book = new ListTag();
        for (Assembly assembly : from) {
            ItemStack result = assembly.result();
            // What another mod already cuts on Farmer's Delight's board is the board's, whatever it makes.
            boolean taken = assembly.tool() != null ? !result.is(NOT_ASSEMBLED) : result.is(ASSEMBLED) || !result.is(NOT_ASSEMBLED)
                    && !usesWorkshop(assembly.ingredients()) && (isDishResult(result) || isCutIntoFood(result) || isDrinkOfFood(assembly));
            if (!taken) {
                continue;
            }
            all.add(assembly);
            CompoundTag entry = new CompoundTag();
            entry.putString("id", assembly.id().toString());
            entry.putString("json", assembly.json().toString());
            book.add(entry);
        }
        return new Chosen(List.copyOf(all), Set.copyOf(all.stream().map(Assembly::id).toList()), book);
    }

    /**
     * Food, or a block of food put down whole. A pie may be no block item and no food of its own: Delightful makes the vanilla pumpkin
     * pie a pie block that is put down and eaten in slices.
     */
    public static boolean isDishResult(ItemStack result) {
        if (result.isEdible()) {
            return true;
        }
        if (result.getItem() instanceof BlockItem item && isWholeFood(item.getBlock())) {
            return true;
        }
        Set<Item> placed = wholeFoodItems;
        if (placed == null) {
            placed = BuiltInRegistries.BLOCK.stream().filter(AssemblyRecipes::isWholeFood).map(Block::asItem).collect(Collectors.toSet());
            wholeFoodItems = placed;
        }
        return placed.contains(result.getItem());
    }

    /** The items that put down a block of whole food, once the blocks are all registered. */
    private static volatile Set<Item> wholeFoodItems;
    /** The cutting board's recipes the server loaded with the dishes. */
    private static volatile List<CuttingBoardRecipe> cutting = List.of();

    /**
     * A whole dish the board cuts into food: a pie no one eats whole. Farmer's Delight puts the vanilla pumpkin pie down through an item of
     * its own, and Delightful leaves the vanilla one no food: only its slices are.
     */
    public static boolean isCutIntoFood(ItemStack result) {
        for (CuttingBoardRecipe recipe : cutting) {
            if (!recipe.getIngredients().isEmpty() && recipe.getIngredients().get(0).test(result)
                    && recipe.getResults().stream().anyMatch(ItemStack::isEdible)) {
                return true;
            }
        }
        return false;
    }

    /** Whether some ingredient can only be a workshop's material. */
    public static boolean usesWorkshop(List<Ingredient> ingredients) {
        return ingredients.stream().anyMatch(ingredient -> ingredient.getItems().length > 0
                && Arrays.stream(ingredient.getItems()).allMatch(item -> item.is(WORKSHOP)));
    }

    /** A drink made of food (melon juice); milk poured from a bucket into bottles is no dish. */
    private static boolean isDrinkOfFood(Assembly assembly) {
        return assembly.result().getUseAnimation() == UseAnim.DRINK && assembly.ingredients().stream()
                .anyMatch(ingredient -> Arrays.stream(ingredient.getItems()).anyMatch(ItemStack::isEdible));
    }

    private static boolean isWholeFood(Block block) {
        return block instanceof FeastBlock || block instanceof PieBlock || block instanceof CakeBlock;
    }

    /** The recipes {@link #book} lists, read where they are needed: on a client its tags are bound and its items known. */
    public static List<Assembly> read(ListTag list) {
        List<Assembly> read = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            try {
                JsonObject json = JsonParser.parseString(entry.getString("json")).getAsJsonObject();
                if (id != null) {
                    parse(id, json).ifPresent(read::add);
                }
            } catch (RuntimeException broken) {
                HandsOnDelight.LOGGER.warn("Could not read the board's recipe {}", id, broken);
            }
        }
        return read;
    }

    /**
     * Finds the recipes of {@code json} that the board may make; all of them go on to the recipe manager. Their ingredients are only
     * read as JSON here: item tags are not bound yet while recipes load.
     */
    public static void collect(Map<ResourceLocation, JsonElement> json, Map<ResourceLocation, JsonObject> found) {
        json.forEach((id, element) -> {
            // One broken recipe of an addon or datapack must not stop the reload: the game itself only logs it.
            try {
                if (element.isJsonObject()) {
                    candidate(id, element.getAsJsonObject()).ifPresent(recipe -> found.put(id, recipe));
                }
            } catch (RuntimeException broken) {
                HandsOnDelight.LOGGER.warn("Could not read the recipe {} for the cutting board", id, broken);
            }
        });
    }

    /**
     * Reads the recipes {@link #collect} found, after the recipe manager has read its own: only those it {@code loaded}, and of a
     * conditional recipe the choice that it loaded.
     */
    public static void load(Map<ResourceLocation, JsonObject> taken, Map<ResourceLocation, Recipe<?>> loaded) {
        List<Assembly> read = new ArrayList<>();
        cutting = loaded.values().stream().filter(CuttingBoardRecipe.class::isInstance).map(CuttingBoardRecipe.class::cast).toList();
        taken.forEach((id, json) -> {
            Recipe<?> recipe = loaded.get(id);
            if (recipe == null) {
                return;
            }
            try {
                (CONDITIONAL.equals(string(json, "type")) ? chosenOf(id, json, recipe) : parse(id, json)).ifPresent(read::add);
            } catch (RuntimeException broken) {
                HandsOnDelight.LOGGER.error("Could not read the recipe {} for the cutting board", id, broken);
            }
        });
        candidates = List.copyOf(read);
        // Culinary Compat adds its cuts again when the reloaded recipes go out to the players.
        cutTogether = List.of();
        chosen = null;
        HandsOnDelight.LOGGER.info("{} crafting recipes of Farmer's Delight may be put together on the cutting board", read.size());
    }

    /**
     * Reads the dishes another mod put into the server's recipe manager after it loaded: Culinary Compat's cuts of several things at
     * once ({@link #CUT_TOGETHER}). They are found by their recipe type, so nothing of that mod is loaded here, and are laid on the
     * board as layers, then cut through with a knife, as Culinary Compat does on Farmer's Delight's own board.
     */
    public static void recipesReplaced(Collection<Recipe<?>> recipes) {
        List<Assembly> found = new ArrayList<>();
        for (Recipe<?> recipe : recipes) {
            if (!CUT_TOGETHER.equals(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()))) {
                continue;
            }
            try {
                cutTogether(recipe).ifPresent(found::add);
            } catch (RuntimeException broken) {
                HandsOnDelight.LOGGER.warn("Could not read the recipe {} for the cutting board", recipe.getId(), broken);
            }
        }
        if (found.isEmpty() && cutTogether.isEmpty()) {
            return;
        }
        cutTogether = List.copyOf(found);
        chosen = null;
        HandsOnDelight.LOGGER.info("{} dishes of Culinary Compat are cut together on the cutting board", found.size());
    }

    /** A cut of several things as a shapeless craft of the same ingredients and result, which the board lays and makes as its own. */
    private static Optional<Assembly> cutTogether(Recipe<?> recipe) {
        ItemStack result = recipe.getResultItem(RegistryAccess.EMPTY);
        List<Ingredient> ingredients = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        if (result.isEmpty() || result.hasTag() || ingredients.isEmpty() || ingredients.size() > GRID * GRID) {
            return Optional.empty();
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", SHAPELESS);
        JsonArray named = new JsonArray();
        ingredients.forEach(ingredient -> named.add(ingredient.toJson()));
        json.add("ingredients", named);
        JsonObject made = item(BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
        made.addProperty("count", result.getCount());
        json.add("result", made);
        JsonObject knife = new JsonObject();
        knife.addProperty("tag", KNIVES);
        json.add(TOOL, knife);
        return parse(recipe.getId(), json);
    }

    /**
     * The recipe as the board reads it, when it is one the board could make: else empty. A conditional recipe is kept whole when one
     * of its choices could be made: which one counts is known once the manager has loaded it.
     */
    private static Optional<JsonObject> candidate(ResourceLocation id, JsonObject json) {
        if (!id.getNamespace().equals(FarmersDelightContent.MOD_ID) && !Platform.INSTANCE.isFarmersDelightAddon(id.getNamespace())) {
            return Optional.empty();
        }
        if (CONDITIONAL.equals(string(json, "type"))) {
            return choices(json).stream().anyMatch(choice -> plain(choice).isPresent()) ? Optional.of(json) : Optional.empty();
        }
        return plain(json);
    }

    /** The recipes a conditional recipe chooses among, in its order. */
    private static List<JsonObject> choices(JsonObject json) {
        List<JsonObject> choices = new ArrayList<>();
        if (json.has("recipes") && json.get("recipes").isJsonArray()) {
            for (JsonElement entry : json.getAsJsonArray("recipes")) {
                if (entry.isJsonObject() && entry.getAsJsonObject().has("recipe") && entry.getAsJsonObject().get("recipe").isJsonObject()) {
                    choices.add(entry.getAsJsonObject().getAsJsonObject("recipe"));
                }
            }
        }
        return choices;
    }

    /**
     * The choice of a conditional recipe that the manager {@code loaded}: the same ingredients and result. Its conditions are not read
     * again; a choice naming items that are not there cannot be read and is passed over.
     */
    private static Optional<Assembly> chosenOf(ResourceLocation id, JsonObject json, Recipe<?> loaded) {
        if (!(loaded instanceof CraftingRecipe crafting)) {
            return Optional.empty();
        }
        for (JsonObject choice : choices(json)) {
            Optional<JsonObject> recipe = plain(choice);
            if (recipe.isEmpty()) {
                continue;
            }
            Optional<Assembly> read;
            try {
                read = parse(id, recipe.get());
            } catch (RuntimeException unknown) {
                continue;
            }
            if (read.isPresent() && same(read.get().recipe(), crafting)) {
                return read;
            }
        }
        return Optional.empty();
    }

    private static boolean same(CraftingRecipe read, CraftingRecipe loaded) {
        ItemStack result = read.getResultItem(RegistryAccess.EMPTY);
        ItemStack other = loaded.getResultItem(RegistryAccess.EMPTY);
        return read.getClass() == loaded.getClass() && ItemStack.isSameItemSameTags(result, other) && result.getCount() == other.getCount()
                && read.getIngredients().stream().map(Ingredient::toJson).toList()
                .equals(loaded.getIngredients().stream().map(Ingredient::toJson).toList());
    }

    /**
     * A recipe of the crafting table or Farmer's Delight's dough, as the board reads it, when the board could make it. A crafting type
     * of an addon's own is read like the crafting table's when it is written like one: whether it lays its items as the crafting table
     * does is known once it is read ({@link #parse}).
     */
    private static Optional<JsonObject> plain(JsonObject json) {
        String type = string(json, "type");
        JsonObject recipe = json;
        if (DOUGH.equals(type)) {
            // One wheat and a water bucket, which stays: the recipe's own matches and remainders say so, the board only lays them.
            recipe = json.deepCopy();
            JsonArray ingredients = new JsonArray();
            ingredients.add(item("minecraft:wheat"));
            ingredients.add(item("minecraft:water_bucket"));
            recipe.add("ingredients", ingredients);
            recipe.add("result", item(FarmersDelightContent.id("wheat_dough").toString()));
        }
        List<JsonElement> named = named(recipe);
        if (named == null || named.size() < 2 || named.size() > GRID * GRID || named.stream().allMatch(named.get(0)::equals)) {
            return Optional.empty();
        }
        JsonElement result = recipe.get("result");
        String name = result == null ? null : result.isJsonObject() ? string(result.getAsJsonObject(), "item")
                : result.isJsonPrimitive() ? result.getAsString() : null;
        ResourceLocation item = name == null ? null : ResourceLocation.tryParse(name);
        return item != null && BuiltInRegistries.ITEM.containsKey(item) ? Optional.of(recipe) : Optional.empty();
    }

    private static Optional<Assembly> parse(ResourceLocation id, JsonObject json) {
        Recipe<?> recipe = RecipeManager.fromJson(id, json);
        List<JsonElement> named = named(json);
        if (!(recipe instanceof CraftingRecipe crafting) || named == null || named.isEmpty() || !laysLikeACraftingTable(json, crafting)) {
            return Optional.empty();
        }
        List<Ingredient> ingredients = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        if (crafting instanceof ShapedRecipe shaped) {
            List<Ingredient> all = shaped.getIngredients();
            for (int i = 0; i < all.size(); i++) {
                if (!all.get(i).isEmpty()) {
                    ingredients.add(all.get(i));
                    slots.add(i / shaped.getWidth() * GRID + i % shaped.getWidth());
                }
            }
        } else if (!crafting.getIngredients().isEmpty()) {
            ingredients.addAll(crafting.getIngredients());
        } else {
            // A recipe of its own type lists nothing: the ingredients the board knows for it.
            named.forEach(ingredient -> ingredients.add(Ingredient.fromJson(ingredient)));
        }
        while (slots.size() < ingredients.size()) {
            slots.add(slots.size());
        }
        if (ingredients.size() != named.size() || ingredients.size() > GRID * GRID) {
            return Optional.empty();
        }
        ItemStack result = crafting.getResultItem(RegistryAccess.EMPTY);
        if (result.isEmpty() && json.has("result") && json.get("result").isJsonObject()) {
            result = ShapedRecipe.itemStackFromJson(json.getAsJsonObject("result"));
        }
        if (result.isEmpty()) {
            return Optional.empty();
        }
        int base = 0;
        Base kind = Base.FIRST;
        search:
        for (Base candidate : Base.values()) {
            for (String word : candidate.words) {
                for (int i = 0; i < named.size(); i++) {
                    if (names(named.get(i), word)) {
                        base = i;
                        kind = candidate;
                        break search;
                    }
                }
            }
        }
        Ingredient tool = json.has(TOOL) ? Ingredient.fromJson(json.get(TOOL)) : null;
        return Optional.of(new Assembly(id, crafting, List.copyOf(ingredients), slots.stream().mapToInt(Integer::intValue).toArray(),
                base, kind, result.copy(), json, tool));
    }

    /** The ingredients of a shapeless or shaped recipe's JSON, a shaped one's row by row as the recipe lists them; null if broken. */
    /**
     * Whether the board can make {@code crafting} by laying its ingredients in a grid: the crafting table's own types and Farmer's
     * Delight's dough, or a type of an addon built on the crafting table's shaped or shapeless craft. Such a type only adds to what it
     * makes, and its own code still does that on the board, which makes the dish through the recipe: Barbeque's Delight's kebabs and
     * bibimbap carry the seasoning of their skewers. A type that matches in a way of its own stays in the crafting table.
     */
    private static boolean laysLikeACraftingTable(JsonObject json, CraftingRecipe crafting) {
        String type = string(json, "type");
        return SHAPELESS.equals(type) || SHAPED.equals(type) || DOUGH.equals(type) || crafting instanceof ShapedRecipe
                || crafting instanceof ShapelessRecipe;
    }

    private static List<JsonElement> named(JsonObject json) {
        List<JsonElement> named = new ArrayList<>();
        if (json.has("ingredients") && json.get("ingredients").isJsonArray()) {
            json.getAsJsonArray("ingredients").forEach(named::add);
            return named;
        }
        if (!json.has("pattern") || !json.get("pattern").isJsonArray() || !json.has("key") || !json.get("key").isJsonObject()) {
            return null;
        }
        JsonObject key = json.getAsJsonObject("key");
        for (JsonElement row : json.getAsJsonArray("pattern")) {
            for (char symbol : row.getAsString().toCharArray()) {
                if (symbol != ' ') {
                    JsonElement ingredient = key.get(String.valueOf(symbol));
                    if (ingredient == null) {
                        return null;
                    }
                    named.add(ingredient);
                }
            }
        }
        return named;
    }

    private static JsonObject item(String id) {
        JsonObject item = new JsonObject();
        item.addProperty("item", id);
        return item;
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

    /** Whether {@code item} is what a dish of the board makes: lying there, it is a dish made, not food to be cut. */
    public static boolean isDish(Item item) {
        return all().stream().anyMatch(dish -> dish.result().is(item));
    }

    /** Whether {@code item} starts a dish: it is what goes down first in one. */
    public static boolean isBase(ItemStack item) {
        for (Assembly assembly : all()) {
            if (assembly.ingredients().get(assembly.base()).test(item)) {
                return true;
            }
        }
        return false;
    }

    /** Whether what lies on the board, the base first, is all part of some dish. */
    public static boolean fits(List<ItemStack> layers) {
        for (Assembly assembly : all()) {
            if (assign(assembly, layers, false) != null) {
                return true;
            }
        }
        return false;
    }

    /** Whether more could still go on the layers for a bigger dish: then a finished one waits for the cook's word. */
    public static boolean canGrow(List<ItemStack> layers) {
        for (Assembly assembly : all()) {
            if (assembly.ingredients().size() > layers.size() && assign(assembly, layers, false) != null) {
                return true;
            }
        }
        return false;
    }

    /** The dish that what lies on the board makes, all of it and nothing more. */
    public static Optional<Assembly> complete(List<ItemStack> layers) {
        for (Assembly assembly : all()) {
            if (assign(assembly, layers, true) != null) {
                return Optional.of(assembly);
            }
        }
        return Optional.empty();
    }

    /**
     * Which ingredient each layer takes, the first one the base; {@code all}: every ingredient is taken. Null when the layers do not
     * go into the dish.
     */
    public static int[] assign(Assembly assembly, List<ItemStack> layers, boolean all) {
        List<Ingredient> ingredients = assembly.ingredients();
        if (layers.isEmpty() || layers.size() > ingredients.size() || all && layers.size() != ingredients.size()) {
            return null;
        }
        if (!ingredients.get(assembly.base()).test(layers.get(0))) {
            return null;
        }
        int[] taken = new int[layers.size()];
        taken[0] = assembly.base();
        boolean[] used = new boolean[ingredients.size()];
        used[assembly.base()] = true;
        return assign(ingredients, layers, 1, used, taken) ? taken : null;
    }

    private static boolean assign(List<Ingredient> ingredients, List<ItemStack> layers, int next, boolean[] used, int[] taken) {
        if (next == layers.size()) {
            return true;
        }
        for (int i = 0; i < ingredients.size(); i++) {
            if (!used[i] && ingredients.get(i).test(layers.get(next))) {
                used[i] = true;
                taken[next] = i;
                if (assign(ingredients, layers, next + 1, used, taken)) {
                    return true;
                }
                used[i] = false;
            }
        }
        return false;
    }
}
