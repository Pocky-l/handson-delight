package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import vectorwing.farmersdelight.common.crafting.CookingPotBookCategory;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.Cookable;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.ItemNbt;
import com.pockyl.handson_delight.KitchenRecipes;
import com.pockyl.handson_delight.RecipeParts;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.CookingPotStation;
import com.pockyl.handson_delight.station.KitchenPot;
import com.pockyl.handson_delight.station.PotLogic;
import com.pockyl.handson_delight.station.PotRecipes;
import com.pockyl.handson_delight.station.StationRecipes;
import com.pockyl.handson_delight.station.StirSkill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The station's rules against a real pot of Farmer's Delight, the same on every loader: each loader project runs these through its
 * own test registration (NeoForge's {@code ModGameTests}, Fabric's {@code FabricPotTests}).
 */
public final class PotTests {
    private PotTests() {
    }

    private static Item fd(String path) {
        return BuiltInRegistries.ITEM.getValue(FarmersDelightContent.id(path));
    }

    /** A pot on a lit campfire, with the logic the station would make for it. */
    private record Setup(TestStation station, PotLogic logic, CookingPotBlockEntity pot) {
    }

    private static Setup pot(GameTestHelper helper) {
        BlockPos fire = new BlockPos(1, 1, 1);
        helper.setBlock(fire, Blocks.CAMPFIRE);
        helper.setBlock(fire.above(), FarmersDelightContent.COOKING_POT.get());
        BlockPos pos = helper.absolutePos(fire.above());
        TestStation station = new TestStation(helper.getLevel(), pos);
        CookingPotBlockEntity pot = (CookingPotBlockEntity) helper.getLevel().getBlockEntity(pos);
        return new Setup(station, new PotLogic(station), pot);
    }

    public static void slotsAreThePotsOwn(GameTestHelper helper) {
        Setup setup = pot(helper);
        SlotRef first = SlotRef.of(CookingPotStation.INGREDIENTS, 0);
        setup.station.state().setItem(first, new ItemStack(Items.CARROT, 3));
        helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 0).is(Items.CARROT), "the slot writes into the pot");
        Platform.INSTANCE.setItem(setup.pot, CookingPotBlockEntity.OUTPUT_SLOT, new ItemStack(Items.BOWL, 2));
        helper.assertTrue(setup.station.state().getItem(CookingPotStation.SERVED_SLOT).is(Items.BOWL), "and reads from it");
        helper.succeed();
    }

    public static void theMealStaysInThePot(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        helper.assertFalse(setup.logic.mayTake(setup.station, player, CookingPotStation.MEAL_SLOT), "the meal is only served");
        helper.assertTrue(setup.logic.mayTake(setup.station, player, CookingPotStation.SERVED_SLOT), "servings are taken");
        helper.succeed();
    }

    public static void aBowlClickedOnTheSoupFills(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.inventoryMenu.setCarried(new ItemStack(Items.BOWL, 3));
        // No meal: nothing to scoop, the bowls stay on the cursor.
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_SCOOP, new CompoundTag());
        helper.assertTrue(player.inventoryMenu.getCarried().getCount() == 3, "nothing happens without a meal");

        Platform.INSTANCE.setItem(setup.pot, CookingPotBlockEntity.MEAL_DISPLAY_SLOT, new ItemStack(fd("beef_stew"), 2));
        // What a finished recipe leaves in the pot: it knows its container only from the recipe or from its save.
        CompoundTag saved = setup.pot.saveWithoutMetadata(helper.getLevel().registryAccess());
        saved.put("Container", ItemNbt.save(new ItemStack(Items.BOWL), helper.getLevel().registryAccess()));
        setup.pot.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(CookingPotStation.INGREDIENTS, 0), new ItemStack(Items.BOWL)),
                "a bowl for the soup does not go in");
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_SCOOP, new CompoundTag());
        helper.assertTrue(player.inventoryMenu.getCarried().getCount() == 2, "one bowl fills");
        helper.assertTrue(player.getInventory().contains(new ItemStack(fd("beef_stew"))), "and goes to the inventory");
        helper.assertTrue(setup.pot.getMeal().getCount() == 1, "one serving left");

        player.inventoryMenu.setCarried(new ItemStack(Items.BOWL));
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_SCOOP, new CompoundTag());
        helper.assertTrue(player.inventoryMenu.getCarried().is(fd("beef_stew")), "the last bowl in the hand stays in it, full");
        helper.succeed();
    }

    public static void anEmptyHandScoopsWithBowlsFromTheInventory(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getInventory().setItem(5, new ItemStack(Items.BOWL, 3));
        Platform.INSTANCE.setItem(setup.pot, CookingPotBlockEntity.MEAL_DISPLAY_SLOT, new ItemStack(fd("beef_stew"), 4));
        CompoundTag saved = setup.pot.saveWithoutMetadata(helper.getLevel().registryAccess());
        saved.put("Container", ItemNbt.save(new ItemStack(Items.BOWL), helper.getLevel().registryAccess()));
        setup.pot.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        CompoundTag one = new CompoundTag();
        one.putBoolean(CookingPotStation.FROM_INVENTORY, true);
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_SCOOP, one);
        helper.assertTrue(player.getInventory().countItem(Items.BOWL) == 2, "one bowl from the inventory fills");
        helper.assertTrue(player.getInventory().countItem(fd("beef_stew")) == 1, "into the inventory");
        helper.assertTrue(player.inventoryMenu.getCarried().isEmpty(), "the hand stays empty");
        CompoundTag all = one.copy();
        all.putBoolean(CookingPotStation.SCOOP_ALL, true);
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_SCOOP, all);
        helper.assertTrue(player.getInventory().countItem(Items.BOWL) == 0, "Shift fills every bowl there is");
        helper.assertTrue(setup.pot.getMeal().getCount() == 1, "and leaves the serving it has no bowl for");
        helper.succeed();
    }

    public static void aStackIsSharedOutForARecipeOfTwoAlike(GameTestHelper helper) {
        Setup setup = pot(helper);
        SlotRef first = SlotRef.of(CookingPotStation.INGREDIENTS, 0);
        // A mod may match a stack in one slot as two tomatoes (Autochef's Delight): then the pot cooks them as they lie.
        boolean stacksMatch = stacksMatch(helper);
        Platform.INSTANCE.setItem(setup.pot, 0, new ItemStack(fd("tomato"), 5));
        setup.logic.onSlotChanged(setup.station, first);
        setup.logic.tick(setup.station);
        if (!stacksMatch) {
            helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 0).getCount() == 3, "tomatoes are shared out");
            helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 1).is(fd("tomato")), "over two slots, as tomato sauce wants");
            helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 1).getCount() == 2, "and none get lost");
        }

        Platform.INSTANCE.setItem(setup.pot, 0, new ItemStack(Items.CARROT, 6));
        Platform.INSTANCE.setItem(setup.pot, 1, ItemStack.EMPTY);
        setup.logic.onSlotChanged(setup.station, first);
        setup.logic.tick(setup.station);
        helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 0).getCount() == 6, "what makes nothing stays as it is");
        helper.succeed();
    }

    /** The dish the ingredient slots make as Farmer's Delight reads them, or null. */
    private static Identifier made(GameTestHelper helper, CookingPotBlockEntity pot) {
        List<ItemStack> inPot = new ArrayList<>();
        for (int i = 0; i < CookingPotBlockEntity.MEAL_DISPLAY_SLOT; i++) {
            inPot.add(Platform.INSTANCE.getItem(pot, i));
        }
        return Platform.INSTANCE.findRecipe(helper.getLevel(), inPot).map(holder -> holder.id().identifier()).orElse(null);
    }

    /**
     * Whether a mod reads the pot's slots as amounts (Autochef's Delight): then a stack of tomatoes is two tomatoes, and among recipes
     * of the same food that mod picks the dish, whichever way the food lies.
     */
    private static boolean stacksMatch(GameTestHelper helper) {
        return Platform.INSTANCE.findRecipe(helper.getLevel(), List.of(new ItemStack(fd("tomato"), 5))).isPresent();
    }

    /** Whether the pot makes {@code dish}, or, where a mod picks among recipes of the same food, at least some dish. */
    private static boolean makes(GameTestHelper helper, CookingPotBlockEntity pot, String dish) {
        return stacksMatch(helper) ? made(helper, pot) != null : HandsOnDelight.id(dish).equals(made(helper, pot));
    }

    /** Puts {@code stack} into the pot as a cook does, and lets the station tick once. */
    private static void throwIn(Setup setup, int slot, ItemStack stack) {
        putIn(setup, slot, stack);
        setup.logic.tick(setup.station);
    }

    /**
     * Two alike thrown in lie in one slot. Christmas Delight's candy cane is sugar, sugar and a dye: the pot lays the sugars in two slots
     * for it, and does not cook the dish of sugar and dye beside it, which would leave a sugar over. More servings come first, though.
     */
    public static void twoAlikeThrownInMakeTheirDish(GameTestHelper helper) {
        addRecipe(helper, "cane", new ItemStack(Items.STICK), ItemStack.EMPTY, 40, Ingredient.of(Items.GLOWSTONE_DUST),
                Ingredient.of(Items.GLOWSTONE_DUST), Ingredient.of(Items.DYE.red()));
        addRecipe(helper, "cane_half", new ItemStack(Items.STRING), ItemStack.EMPTY, 40, Ingredient.of(Items.GLOWSTONE_DUST),
                Ingredient.of(Items.DYE.red()));
        Setup setup = pot(helper);
        throwIn(setup, 0, new ItemStack(Items.GLOWSTONE_DUST, 2));
        throwIn(setup, 1, new ItemStack(Items.DYE.red()));
        helper.assertTrue(makes(helper, setup.pot, "test_cane"), "the cane, got " + made(helper, setup.pot));
        empty(setup.pot);
        // A batch of the smaller dish with some dust over (ten corn and six milk for elote) stays that dish: six servings, not five.
        throwIn(setup, 0, new ItemStack(Items.GLOWSTONE_DUST, 10));
        throwIn(setup, 1, new ItemStack(Items.DYE.red(), 6));
        helper.assertTrue(makes(helper, setup.pot, "test_cane_half"), "six of the half, got " + made(helper, setup.pot));
        empty(setup.pot);
        // Three dust and a dye make one of either and neither whole (three corn and a milk bucket): it cooks as it did, the half.
        throwIn(setup, 0, new ItemStack(Items.GLOWSTONE_DUST, 3));
        throwIn(setup, 1, new ItemStack(Items.DYE.red()));
        helper.assertTrue(makes(helper, setup.pot, "test_cane_half"), "the half as before, got " + made(helper, setup.pot));
        empty(setup.pot);
        helper.succeed();
    }

    /**
     * A dish of tomato, rice, apple, sugar and two vegetables, a tomato being one (GH #34): two stacks of tomatoes lie over the tomato
     * and both vegetables, so all six slots are full. Carrots clicked onto the pot still go in, the tomatoes giving a vegetable's slot up;
     * what no dish of the pot reads stays in the hand.
     */
    public static void aFullPotTakesWhatItsDishStillReads(GameTestHelper helper) {
        Ingredient vegetable = Ingredient.of(Items.COPPER_INGOT, Items.QUARTZ);
        addRecipe(helper, "full_pot", new ItemStack(Items.STICK), ItemStack.EMPTY, 40, Ingredient.of(Items.COPPER_INGOT),
                Ingredient.of(Items.IRON_NUGGET), Ingredient.of(Items.GOLD_NUGGET), Ingredient.of(Items.LAPIS_LAZULI), vegetable, vegetable);
        Setup setup = pot(helper);
        Player cook = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(cook);
        throwIn(setup, 0, new ItemStack(Items.COPPER_INGOT, 64));
        throwIn(setup, 1, new ItemStack(Items.COPPER_INGOT, 64));
        throwIn(setup, 2, new ItemStack(Items.IRON_NUGGET, 64));
        throwIn(setup, 3, new ItemStack(Items.GOLD_NUGGET, 64));
        throwIn(setup, 4, new ItemStack(Items.LAPIS_LAZULI, 64));
        boolean full = true;
        for (int slot = 0; slot < 6; slot++) {
            full &= !Platform.INSTANCE.getItem(setup.pot, slot).isEmpty();
        }
        helper.assertTrue(full && makes(helper, setup.pot, "test_full_pot"), "the copper lies over three slots, the pot is full");
        CompoundTag one = new CompoundTag();
        cook.inventoryMenu.setCarried(new ItemStack(Items.STICK, 5));
        setup.logic.onCustomAction(setup.station, cook, StationRecipes.ACTION_OFFER, one);
        helper.assertTrue(cook.inventoryMenu.getCarried().getCount() == 5, "a stick no dish reads stays in the hand");
        cook.inventoryMenu.setCarried(new ItemStack(Items.QUARTZ, 64));
        setup.logic.onCustomAction(setup.station, cook, StationRecipes.ACTION_OFFER, one);
        setup.logic.tick(setup.station);
        int copper = 0;
        int quartz = 0;
        int copperSlots = 0;
        for (int slot = 0; slot < 6; slot++) {
            ItemStack there = Platform.INSTANCE.getItem(setup.pot, slot);
            copper += there.is(Items.COPPER_INGOT) ? there.getCount() : 0;
            copperSlots += there.is(Items.COPPER_INGOT) ? 1 : 0;
            quartz += there.is(Items.QUARTZ) ? there.getCount() : 0;
        }
        helper.assertTrue(cook.inventoryMenu.getCarried().isEmpty() && quartz == 64, "the carrots go in, got " + quartz);
        helper.assertTrue(copper == 128 && copperSlots == 2, "the tomatoes give a slot up and none comes out, got " + copper);
        helper.assertTrue(makes(helper, setup.pot, "test_full_pot"), "the same dish, got " + made(helper, setup.pot));
        helper.succeed();
    }

    /**
     * Christmas Delight's cevapi is Burek and two more things: with Burek's food in the pot it makes Burek (the second mutton lies over),
     * and once the last thing is in, the cevapi.
     */
    public static void theFullerDishOnceItsLastIngredientIsIn(GameTestHelper helper) {
        addRecipe(helper, "burek", new ItemStack(Items.STICK), ItemStack.EMPTY, 40, Ingredient.of(Items.AMETHYST_SHARD),
                Ingredient.of(Items.PRISMARINE_SHARD), Ingredient.of(Items.ECHO_SHARD));
        addRecipe(helper, "cevapi", new ItemStack(Items.STRING), ItemStack.EMPTY, 40, Ingredient.of(Items.AMETHYST_SHARD),
                Ingredient.of(Items.PRISMARINE_SHARD), Ingredient.of(Items.PRISMARINE_SHARD), Ingredient.of(Items.ECHO_SHARD),
                Ingredient.of(Items.NAUTILUS_SHELL));
        Setup setup = pot(helper);
        throwIn(setup, 0, new ItemStack(Items.AMETHYST_SHARD));
        throwIn(setup, 1, new ItemStack(Items.PRISMARINE_SHARD, 2));
        throwIn(setup, 2, new ItemStack(Items.ECHO_SHARD));
        helper.assertTrue(makes(helper, setup.pot, "test_burek"), "burek so far, got " + made(helper, setup.pot));
        throwIn(setup, 3, new ItemStack(Items.NAUTILUS_SHELL));
        helper.assertTrue(makes(helper, setup.pot, "test_cevapi"), "the cevapi, got " + made(helper, setup.pot));
        empty(setup.pot);
        helper.succeed();
    }

    /** A tag may take what another ingredient wants: a flower and two dandelions are a dandelion stack and a poppy. */
    public static void aTagTakesWhatTheOtherIngredientsLeave(GameTestHelper helper) {
        addRecipe(helper, "posy", new ItemStack(Items.STICK), ItemStack.EMPTY, 40, tagged(BlockItemTags.SMALL_FLOWERS.item()),
                Ingredient.of(Items.DANDELION), Ingredient.of(Items.DANDELION));
        Setup setup = pot(helper);
        throwIn(setup, 0, new ItemStack(Items.DANDELION, 2));
        throwIn(setup, 1, new ItemStack(Items.POPPY));
        helper.assertTrue(makes(helper, setup.pot, "test_posy"), "the posy, got " + made(helper, setup.pot));
        empty(setup.pot);
        helper.succeed();
    }

    /**
     * Two quartz and two flint make one dish of four or two of a dish of two (Rustic Delight's dark coffee and coffee): thrown in, the
     * fuller dish; picked in the book, the one picked.
     */
    public static void theBookKeepsThePickedDish(GameTestHelper helper) {
        addRecipe(helper, "pair", new ItemStack(Items.STICK), ItemStack.EMPTY, 40, Ingredient.of(Items.QUARTZ), Ingredient.of(Items.FLINT));
        addRecipe(helper, "pairs", new ItemStack(Items.STRING), ItemStack.EMPTY, 40, Ingredient.of(Items.QUARTZ), Ingredient.of(Items.QUARTZ),
                Ingredient.of(Items.FLINT), Ingredient.of(Items.FLINT));
        Setup setup = pot(helper);
        throwIn(setup, 0, new ItemStack(Items.QUARTZ, 2));
        throwIn(setup, 1, new ItemStack(Items.FLINT, 2));
        helper.assertTrue(makes(helper, setup.pot, "test_pairs"), "thrown in: the dish of four, got "
                + made(helper, setup.pot));
        empty(setup.pot);
        setup.logic.tick(setup.station);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(Items.QUARTZ, 2));
        player.getInventory().add(new ItemStack(Items.FLINT, 2));
        CompoundTag fill = new CompoundTag();
        fill.putString(CookingPotStation.DISH, HandsOnDelight.id("test_pair").toString());
        fill.putBoolean(CookingPotStation.SCOOP_ALL, true);
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_FILL, fill);
        setup.logic.tick(setup.station);
        helper.assertTrue(makes(helper, setup.pot, "test_pair"), "picked: the dish of two, got " + made(helper, setup.pot));
        helper.assertTrue(player.getInventory().countItem(Items.QUARTZ) == 0, "both sets went in");
        empty(setup.pot);
        helper.succeed();
    }

    /**
     * Christmas Delight asks for milk by a tag nothing fills: such a dish cannot be made, so it is not offered, the book puts nothing in
     * for it, and its food is never laid out for it.
     */
    public static void aDishNobodyCanMakeIsNotOffered(GameTestHelper helper) {
        Ingredient nothing = tagged(TagKey.create(Registries.ITEM, HandsOnDelight.id("test_nothing")));
        addRecipe(helper, "yogurt", new ItemStack(Items.STICK), ItemStack.EMPTY, 40, Ingredient.of(Items.HONEY_BOTTLE), nothing, nothing);
        helper.assertFalse(Cookable.ingredient(nothing), "an empty tag is no ingredient anyone has");
        helper.assertTrue(Cookable.ingredient(Ingredient.of(Items.HONEY_BOTTLE)) && Cookable.ingredient(RecipeParts.NOTHING),
                "an item is, and a gap asks for nothing");
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(Items.HONEY_BOTTLE, 2));
        CompoundTag fill = new CompoundTag();
        fill.putString(CookingPotStation.DISH, HandsOnDelight.id("test_yogurt").toString());
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_FILL, fill);
        helper.assertTrue(PotRecipes.kinds((KitchenPot) setup.pot).isEmpty() && player.getInventory().countItem(Items.HONEY_BOTTLE) == 2,
                "the book puts nothing in");
        empty(setup.pot);
        helper.succeed();
    }

    /** Christmas Delight asks for milk as {@code c:foods/milk}, which no other mod fills: it takes the milk bottle and the bucket. */
    public static void milkAskedForAsFoodIsMilk(GameTestHelper helper) {
        Ingredient milk = tagged(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "foods/milk")));
        helper.assertTrue(milk.test(new ItemStack(fd("milk_bottle"))), "a milk bottle is milk");
        helper.assertTrue(milk.test(new ItemStack(Items.MILK_BUCKET)), "a milk bucket is milk");
        helper.assertTrue(Cookable.ingredient(milk), "so the dishes of milk can be made");
        helper.succeed();
    }

    /** Every cooking recipe the game has, its food thrown in or picked in the book: see {@link StationAudit}. */
    public static void everyDishFromItsFoodThrownIn(GameTestHelper helper) {
        StationAudit.audit(helper, "cooking pot", PotRecipes.candidates(Platform.INSTANCE.cookingRecipes(helper.getLevel())), recipe -> null,
                CookingPotStation.SLOTS);
    }

    public static void dishesStayOutOfTheWater(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        SlotRef first = SlotRef.of(CookingPotStation.INGREDIENTS, 0);
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.BOWL)), "no bowl in an empty pot");
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.CARROT)), "food goes in");
        helper.succeed();
    }

    public static void shiftFillsTheWholeStack(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        Platform.INSTANCE.setItem(setup.pot, CookingPotBlockEntity.MEAL_DISPLAY_SLOT, new ItemStack(fd("beef_stew"), 2));
        CompoundTag saved = setup.pot.saveWithoutMetadata(helper.getLevel().registryAccess());
        saved.put("Container", ItemNbt.save(new ItemStack(Items.BOWL), helper.getLevel().registryAccess()));
        setup.pot.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        player.inventoryMenu.setCarried(new ItemStack(Items.BOWL, 5));
        CompoundTag all = new CompoundTag();
        all.putBoolean(CookingPotStation.SCOOP_ALL, true);
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_SCOOP, all);
        helper.assertTrue(setup.pot.getMeal().isEmpty(), "the soup is gone");
        helper.assertTrue(player.inventoryMenu.getCarried().getCount() == 3, "three bowls are left");
        helper.assertTrue(player.getInventory().countItem(fd("beef_stew")) == 2, "two are full");
        helper.succeed();
    }

    /** Puts {@code stack} into the pot as a cook at the station does. */
    private static void putIn(Setup setup, int slot, ItemStack stack) {
        SlotRef ref = SlotRef.of(CookingPotStation.INGREDIENTS, slot);
        setup.station.state().setItem(ref, stack);
        setup.logic.onSlotChanged(setup.station, ref);
    }

    private static void fill(CookingPotBlockEntity pot, int servings) {
        Platform.INSTANCE.setItem(pot, 0, new ItemStack(Items.BEEF, servings));
        Platform.INSTANCE.setItem(pot, 1, new ItemStack(Items.CARROT, servings));
        Platform.INSTANCE.setItem(pot, 2, new ItemStack(Items.POTATO, servings));
    }

    /** The spoon at {@code angle} round the pot, most of the way out. */
    private static CompoundTag spoonAt(double angle) {
        CompoundTag where = new CompoundTag();
        where.putFloat(CookingPotStation.STIR_X, (float) (Math.cos(angle) * 0.9));
        where.putFloat(CookingPotStation.STIR_Z, (float) (Math.sin(angle) * 0.9));
        return where;
    }

    public static void theWholeBatchCooksAtOnce(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        fill(setup.pot, 4);
        for (int tick = 0; tick < 320; tick += 20) {
            helper.runAfterDelay(tick, () -> setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_STIR, new CompoundTag()));
        }
        helper.runAfterDelay(200, () -> {
            setup.logic.tick(setup.station);
            helper.assertTrue(setup.pot.getMeal().isEmpty(), "no serving comes out on its own");
            helper.assertTrue(setup.station.state().getControl(CookingPotStation.PROGRESS) > 0.5F, "the batch is more than half done");
        });
        helper.runAfterDelay(300, () -> {
            // Four servings take twice the time of one, which is two thirds of Farmer's Delight's: 10 s * 2 / 1.5.
            helper.assertTrue(setup.pot.getMeal().getCount() == 4, "all four come out together, in about thirteen seconds");
            helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 0).isEmpty(), "out of all the ingredients");
            helper.succeed();
        });
    }

    /** One of an ingredient beside more of the others makes one serving: the rest never cooks without it. */
    public static void theSmallestStackSetsTheBatch(GameTestHelper helper) {
        Setup setup = pot(helper);
        Platform.INSTANCE.setItem(setup.pot, 0, new ItemStack(Items.BEEF, 4));
        Platform.INSTANCE.setItem(setup.pot, 1, new ItemStack(Items.CARROT, 4));
        Platform.INSTANCE.setItem(setup.pot, 2, new ItemStack(Items.POTATO, 1));
        stirFor(helper, setup, 200);
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(setup.pot.getMeal().getCount() == 1, "one potato makes one stew, got " + setup.pot.getMeal());
            helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 0).getCount() == 3, "the beef left over stays");
            helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 1).getCount() == 3, "and the carrots");
            helper.succeed();
        });
    }

    /** Food a hopper brings cooks by itself, Farmer's Delight's way, a serving at a time: nobody stirs a pot in a factory. */
    public static void aHopperFedPotCooksByItself(GameTestHelper helper) {
        Setup setup = pot(helper);
        BlockPos above = new BlockPos(1, 3, 1);
        helper.setBlock(above, Blocks.HOPPER);
        HopperBlockEntity hopper = helper.getBlockEntity(above, HopperBlockEntity.class);
        hopper.setItem(0, new ItemStack(Items.BEEF, 2));
        hopper.setItem(1, new ItemStack(Items.CARROT, 2));
        hopper.setItem(2, new ItemStack(Items.POTATO, 2));
        helper.runAfterDelay(270, () -> {
            helper.assertTrue(setup.pot.getMeal().is(fd("beef_stew")) && setup.pot.getMeal().getCount() == 1,
                    "one stew with nobody stirring, at Farmer's Delight's pace, got " + setup.pot.getMeal());
            helper.succeed();
        });
    }

    /**
     * A hopper feeding the pot while a cook stands at it still feeds it by itself: its food cooks Farmer's Delight's way and does not
     * wait for the spoon.
     */
    public static void aHopperFeedsThePotByItselfBesideACook(GameTestHelper helper) {
        Setup setup = pot(helper);
        BlockPos above = new BlockPos(1, 3, 1);
        helper.setBlock(above, Blocks.HOPPER);
        HopperBlockEntity hopper = helper.getBlockEntity(above, HopperBlockEntity.class);
        hopper.setItem(0, new ItemStack(Items.BEEF, 2));
        hopper.setItem(1, new ItemStack(Items.CARROT, 2));
        hopper.setItem(2, new ItemStack(Items.POTATO, 2));
        // The cook is at the station all along.
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        helper.runAfterDelay(270, () -> {
            helper.assertTrue(setup.pot.getMeal().is(fd("beef_stew")) && setup.pot.getMeal().getCount() == 1,
                    "one stew with the cook beside it and nobody stirring, got " + setup.pot.getMeal());
            empty(setup.pot);
            helper.succeed();
        });
    }

    /** One spoon: while one cook stirs, another's strokes do not move it; once it has been still a moment, they do. */
    public static void oneSpoonOneHand(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player first = helper.makeMockPlayer(GameType.CREATIVE);
        Player second = helper.makeMockPlayer(GameType.CREATIVE);
        CompoundTag left = new CompoundTag();
        left.putFloat(CookingPotStation.STIR_X, -0.5F);
        CompoundTag right = new CompoundTag();
        right.putFloat(CookingPotStation.STIR_X, 0.5F);
        setup.logic.onCustomAction(setup.station, first, CookingPotStation.ACTION_STIR, left);
        setup.logic.onCustomAction(setup.station, second, CookingPotStation.ACTION_STIR, right);
        helper.assertTrue(setup.station.state().getControl(CookingPotStation.SPOON_X) < 0.0F, "the spoon stays in the first cook's hand");
        helper.runAfterDelay(20, () -> {
            setup.logic.onCustomAction(setup.station, second, CookingPotStation.ACTION_STIR, right);
            helper.assertTrue(setup.station.state().getControl(CookingPotStation.SPOON_X) > 0.0F, "left still, it is the second cook's");
            helper.succeed();
        });
    }

    /** A serving taken with a bowl in the hand, without opening the pot, counts as made: it opens the dish's stars. */
    public static void aServingTakenByHandCounts(GameTestHelper helper) {
        Setup setup = pot(helper);
        ServerPlayer player = TestPlayers.serverPlayer(helper, "server");
        Platform.INSTANCE.setItem(setup.pot, CookingPotBlockEntity.MEAL_DISPLAY_SLOT, new ItemStack(fd("beef_stew"), 2));
        CompoundTag saved = setup.pot.saveWithoutMetadata(helper.getLevel().registryAccess());
        saved.put("Container", ItemNbt.save(new ItemStack(Items.BOWL), helper.getLevel().registryAccess()));
        setup.pot.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL, 2));
        BlockPos pos = setup.station.pos();
        helper.getLevel().getBlockState(pos).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        int made = player.getStats().getValue(Stats.ITEM_CRAFTED.get(fd("beef_stew")));
        helper.assertTrue(setup.pot.getMeal().getCount() == 1, "the bowl took a serving");
        helper.assertTrue(made == 1, "and it counts as made, got " + made);
        helper.succeed();
    }

    /** Steady wide rounds for twelve seconds, by a cook who has made {@code made} beef stews before. */
    private static void stirWell(GameTestHelper helper, Setup setup, int made) {
        ServerPlayer player = TestPlayers.serverPlayer(helper, "cook");
        player.awardStat(Stats.ITEM_CRAFTED.get(fd("beef_stew")), made);
        fill(setup.pot, 2);
        for (int tick = 0; tick < 240; tick += 2) {
            double angle = tick * Math.PI / 10.0;
            helper.runAfterDelay(tick, () -> setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_STIR, spoonAt(angle)));
        }
    }

    public static void aCookWhoKnowsTheDishGetsMore(GameTestHelper helper) {
        Setup setup = pot(helper);
        stirWell(helper, setup, 40);
        helper.runAfterDelay(120, () -> {
            setup.logic.tick(setup.station);
            helper.assertTrue(setup.station.state().getControl(CookingPotStation.STARS) > 2.99F, "steady wide rounds earn three stars");
        });
        helper.runAfterDelay(240, () -> {
            helper.assertTrue(setup.pot.getMeal().getCount() == 3, "and two servings come out as three");
            helper.succeed();
        });
    }

    /** A dish picked in the book fills the pot from the inventory: one set, then with shift all the sets there are, whole sets only. */
    public static void theBookFillsThePot(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getInventory().add(new ItemStack(Items.BEEF, 3));
        player.getInventory().add(new ItemStack(Items.CARROT, 5));
        player.getInventory().add(new ItemStack(Items.POTATO, 3));
        CompoundTag fill = new CompoundTag();
        fill.putString(CookingPotStation.DISH, FarmersDelightContent.id("cooking/beef_stew").toString());
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_FILL, fill);
        int first = 0;
        for (int i = 0; i < 3; i++) {
            first += Platform.INSTANCE.getItem(setup.pot, i).getCount();
        }
        helper.assertTrue(first == 3, "a click puts one set in, got " + first);
        fill.putBoolean(CookingPotStation.SCOOP_ALL, true);
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_FILL, fill);
        helper.assertTrue(player.getInventory().countItem(Items.BEEF) == 0 && player.getInventory().countItem(Items.CARROT) == 2,
                "shift puts in every whole set, the carrots left over stay");
        helper.assertTrue(Platform.INSTANCE.findRecipe(helper.getLevel(), List.of(Platform.INSTANCE.getItem(setup.pot, 0),
                Platform.INSTANCE.getItem(setup.pot, 1), Platform.INSTANCE.getItem(setup.pot, 2))).isPresent(), "and the pot makes the stew");
        helper.succeed();
    }

    /** A bonus is rounded down and the rest carried on: a single serving at three stars brings half of one, the next makes it whole. */
    public static void aBonusIsCarriedOn(GameTestHelper helper) {
        helper.assertTrue(PotLogic.bonus(1, 3, 0.0F) == 0, "one serving at three stars brings half of one");
        helper.assertTrue(PotLogic.bonus(1, 3, 0.5F) == 1, "the next one makes it whole");
        helper.assertTrue(PotLogic.bonus(4, 2, 0.0F) == 1 && PotLogic.bonus(3, 2, 0.0F) == 0, "two stars one for every four");
        helper.assertTrue(PotLogic.bonus(16, 3, 0.0F) == 8, "a full pot at three stars half again");
        helper.assertTrue(PotLogic.bonus(16, 1, 0.9F) == 0, "one star none");
        helper.succeed();
    }

    /** When the meal's stack fills before the batch is used up, the sets the stars saved go back to the cook. */
    public static void theSetsSavedGoBack(GameTestHelper helper) {
        Setup setup = pot(helper);
        ServerPlayer cook = TestPlayers.serverPlayer(helper, "cook");
        cook.awardStat(Stats.ITEM_CRAFTED.get(fd("beef_stew")), 40);
        setup.station.users.add(cook);
        Platform.INSTANCE.setItem(setup.pot, CookingPotBlockEntity.MEAL_DISPLAY_SLOT, new ItemStack(fd("beef_stew"), 12));
        CompoundTag saved = setup.pot.saveWithoutMetadata(helper.getLevel().registryAccess());
        saved.put("Container", ItemNbt.save(new ItemStack(Items.BOWL), helper.getLevel().registryAccess()));
        setup.pot.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        fill(setup.pot, 6);
        for (int tick = 0; tick < 340; tick += 2) {
            double angle = tick * Math.PI / 10.0;
            helper.runAfterDelay(tick, () -> {
                setup.logic.onCustomAction(setup.station, cook, CookingPotStation.ACTION_STIR, spoonAt(angle));
                setup.logic.tick(setup.station);
            });
        }
        helper.runAfterDelay(345, () -> {
            setup.logic.tick(setup.station);
            // Room for four: three sets at three stars make four, the fourth set is saved.
            helper.assertTrue(setup.pot.getMeal().getCount() == 16, "the stack is full, got " + setup.pot.getMeal());
            helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 0).getCount() == 2, "three sets cooked and one went back, left "
                    + Platform.INSTANCE.getItem(setup.pot, 0));
            helper.assertTrue(cook.getInventory().countItem(Items.BEEF) == 1, "the cook has the saved set");
            helper.succeed();
        });
    }

    /**
     * A batch that ends just after its cook closed the pot keeps the sets it saved in the pot: the cook opening it again later (or
     * anybody) is not handed what lies there then.
     */
    public static void theSetsSavedStayWhenTheCookLeft(GameTestHelper helper) {
        Setup setup = pot(helper);
        ServerPlayer cook = TestPlayers.serverPlayer(helper, "cook");
        cook.awardStat(Stats.ITEM_CRAFTED.get(fd("beef_stew")), 40);
        setup.station.users.add(cook);
        Platform.INSTANCE.setItem(setup.pot, CookingPotBlockEntity.MEAL_DISPLAY_SLOT, new ItemStack(fd("beef_stew"), 12));
        CompoundTag saved = setup.pot.saveWithoutMetadata(helper.getLevel().registryAccess());
        saved.put("Container", ItemNbt.save(new ItemStack(Items.BOWL), helper.getLevel().registryAccess()));
        setup.pot.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        fill(setup.pot, 6);
        for (int tick = 0; tick < 340; tick += 1) {
            double angle = tick * Math.PI / 10.0;
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                if (at % 2 == 0) {
                    setup.logic.onCustomAction(setup.station, cook, CookingPotStation.ACTION_STIR, spoonAt(angle));
                }
                // The cook closes the pot the moment the batch is done, before the station hands out what it saved.
                if (setup.pot.getMeal().getCount() < 16) {
                    setup.logic.tick(setup.station);
                }
            });
        }
        helper.runAfterDelay(400, () -> {
            helper.assertTrue(setup.pot.getMeal().getCount() == 16, "the stack is full, got " + setup.pot.getMeal());
            setup.logic.tick(setup.station);
            helper.assertTrue(cook.getInventory().countItem(Items.BEEF) == 0, "the cook opening it again later is handed nothing");
            helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 0).getCount() == 3, "the saved set stays in the pot, left "
                    + Platform.INSTANCE.getItem(setup.pot, 0));
            helper.succeed();
        });
    }

    /** Milk poured into the pot leaves its bottle: with the cook at the station it goes to them, not onto the floor. */
    public static void theEmptiedContainerGoesToTheCook(GameTestHelper helper) {
        Setup setup = pot(helper);
        ServerPlayer cook = TestPlayers.serverPlayer(helper, "cook");
        setup.station.users.add(cook);
        Platform.INSTANCE.setItem(setup.pot, 0, new ItemStack(fd("milk_bottle")));
        Platform.INSTANCE.setItem(setup.pot, 1, new ItemStack(Items.SUGAR));
        Platform.INSTANCE.setItem(setup.pot, 2, new ItemStack(Items.COCOA_BEANS));
        Platform.INSTANCE.setItem(setup.pot, 3, new ItemStack(Items.COCOA_BEANS));
        for (int tick = 0; tick < 400; tick++) {
            double angle = tick * Math.PI / 10.0;
            helper.runAfterDelay(tick, () -> {
                setup.logic.onCustomAction(setup.station, cook, CookingPotStation.ACTION_STIR, spoonAt(angle));
                setup.logic.tick(setup.station);
            });
        }
        helper.runAfterDelay(405, () -> {
            helper.assertTrue(setup.pot.getMeal().is(fd("hot_cocoa")), "the cocoa is made, got " + setup.pot.getMeal());
            helper.assertTrue(cook.getInventory().countItem(Items.GLASS_BOTTLE) == 1, "the cook has the bottle back");
            List<net.minecraft.world.entity.item.ItemEntity> landed = helper.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(setup.station.pos()).inflate(3),
                    // Only the bottle: the pots of the tests beside this one spill what they held when their structures are cleared.
                    entity -> entity.getItem().is(Items.GLASS_BOTTLE));
            helper.assertTrue(landed.isEmpty(), "the bottle does not land beside the pot, got " + landed.stream()
                    .map(entity -> entity.getItem() + " at " + entity.blockPosition()).toList());
            helper.succeed();
        });
    }

    /**
     * What went in without floating cannot be clicked: an empty hand on the soup takes it back out whole, the last first, milk in its
     * bucket. What floats stays for its own click.
     */
    public static void whatWentInWithoutFloatingComesBackOut(GameTestHelper helper) {
        Setup setup = pot(helper);
        ServerPlayer cook = TestPlayers.serverPlayer(helper, "cook");
        setup.station.users.add(cook);
        Platform.INSTANCE.setItem(setup.pot, 0, new ItemStack(Items.MILK_BUCKET));
        Platform.INSTANCE.setItem(setup.pot, 1, new ItemStack(Items.CARROT, 2));
        Platform.INSTANCE.setItem(setup.pot, 2, new ItemStack(Items.SUGAR, 3));
        setup.logic.onCustomAction(setup.station, cook, CookingPotStation.ACTION_TAKE_BACK, new CompoundTag());
        helper.assertTrue(cook.getInventory().countItem(Items.SUGAR) == 3, "the sugar, the last in, comes back first");
        helper.assertTrue(cook.getInventory().countItem(Items.MILK_BUCKET) == 0, "the milk stays for the next click");
        setup.logic.onCustomAction(setup.station, cook, CookingPotStation.ACTION_TAKE_BACK, new CompoundTag());
        helper.assertTrue(cook.getInventory().countItem(Items.MILK_BUCKET) == 1, "the milk comes back in its bucket");
        helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 0).isEmpty(), "no milk is left in the pot");
        setup.logic.onCustomAction(setup.station, cook, CookingPotStation.ACTION_TAKE_BACK, new CompoundTag());
        helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 1).getCount() == 2, "the carrots float and stay");
        helper.assertTrue(cook.getInventory().countItem(Items.CARROT) == 0, "no carrot comes back this way");
        helper.succeed();
    }

    /** Food a cook put in waits for the spoon while they are at the pot; once they walked away it cooks Farmer's Delight's way. */
    public static void aPotLeftByItsCookCooksOn(GameTestHelper helper) {
        Setup setup = pot(helper);
        putIn(setup, 0, new ItemStack(Items.BEEF, 2));
        putIn(setup, 1, new ItemStack(Items.CARROT, 2));
        putIn(setup, 2, new ItemStack(Items.POTATO, 2));
        setup.logic.tick(setup.station);
        helper.runAfterDelay(20, () -> setup.logic.tick(setup.station));
        helper.runAfterDelay(40, () -> {
            setup.logic.tick(setup.station);
            helper.assertTrue(setup.station.state().getControl(CookingPotStation.PROGRESS) == 0.0F, "with the cook there it waits");
        });
        // The cook leaves at 40: two seconds later it cooks, a serving at a time at Farmer's Delight's pace (ten seconds).
        helper.runAfterDelay(320, () -> {
            helper.assertTrue(setup.pot.getMeal().is(fd("beef_stew")) && setup.pot.getMeal().getCount() == 1,
                    "one stew without the cook, got " + setup.pot.getMeal());
            helper.succeed();
        });
    }

    public static void theFirstTimeHasOneStar(GameTestHelper helper) {
        Setup setup = pot(helper);
        stirWell(helper, setup, 0);
        helper.runAfterDelay(120, () -> {
            setup.logic.tick(setup.station);
            helper.assertTrue(setup.station.state().getControl(CookingPotStation.STARS) == 1.0F, "a dish cooked for the first time opens one star");
            helper.assertTrue(setup.station.state().data().getIntOr(CookingPotStation.OPEN_STARS, 0) == 1, "and the badge shows only that one");
        });
        helper.runAfterDelay(240, () -> {
            helper.assertTrue(setup.pot.getMeal().getCount() == 2, "one star brings nothing more");
            helper.succeed();
        });
    }

    public static void onlyGoodStirringEarnsStars(GameTestHelper helper) {
        StirSkill rounds = new StirSkill();
        StirSkill shaking = new StirSkill();
        StirSkill middle = new StirSkill();
        StirSkill cold = new StirSkill();
        for (int tick = 0; tick <= 200; tick += 2) {
            double angle = tick * Math.PI / 10.0;
            rounds.move(tick, (float) Math.cos(angle) * 0.9F, (float) Math.sin(angle) * 0.9F, true, 3);
            double back = Math.sin(tick * Math.PI / 6.0);
            shaking.move(tick, (float) Math.cos(back) * 0.9F, (float) Math.sin(back) * 0.9F, true, 3);
            middle.move(tick, (float) Math.cos(angle) * 0.2F, (float) Math.sin(angle) * 0.2F, true, 3);
            cold.move(tick, (float) Math.cos(angle) * 0.9F, (float) Math.sin(angle) * 0.9F, false, 3);
        }
        helper.assertTrue(rounds.grade(200) == 3, "wide rounds one way earn three stars, got " + rounds.stars(200));
        helper.assertTrue(shaking.grade(200) == 0, "shaking back and forth earns none, got " + shaking.stars(200));
        helper.assertTrue(middle.grade(200) == 0, "circling the middle earns none, got " + middle.stars(200));
        helper.assertTrue(cold.grade(200) == 0, "stirring a pot that does not cook earns none");
        helper.assertTrue(rounds.grade(230) == 2, "a pause takes a star, got " + rounds.stars(230));
        helper.assertTrue(rounds.grade(400) == 0, "a long one all of them");
        helper.succeed();
    }

    /** Strokes that reach the server bunched by the network are judged by the cook's own clock: an even hand still earns three stars. */
    public static void theCooksClockJudgesThePace(GameTestHelper helper) {
        StirSkill bunched = new StirSkill();
        for (int tick = 0; tick <= 200; tick += 2) {
            double angle = tick * Math.PI / 10.0;
            long arrived = tick / 6 * 6 + 1;
            bunched.move(arrived, tick, (float) Math.cos(angle) * 0.9F, (float) Math.sin(angle) * 0.9F, true, 3);
        }
        helper.assertTrue(bunched.grade(199) == 3, "steady rounds that arrive in bunches earn three stars, got " + bunched.stars(199));
        helper.succeed();
    }

    /**
     * A modified client sending a flood of strokes in one tick, each saying its clock went on, fills the stars no faster than the
     * strokes of one: those in the same tick share the slack the network gets.
     */
    public static void aFloodOfStrokesFillsNoFaster(GameTestHelper helper) {
        StirSkill flood = new StirSkill();
        for (int stroke = 0; stroke <= 200; stroke++) {
            double angle = stroke * 0.6;
            flood.move(100, stroke * 2.0F, (float) Math.cos(angle) * 0.9F, (float) Math.sin(angle) * 0.9F, true, 3);
        }
        helper.assertTrue(flood.grade(100) == 0, "two hundred strokes in one tick earn no star, got " + flood.stars(100));
        helper.succeed();
    }

    public static void nothingCooksWithoutStirring(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        putIn(setup, 0, new ItemStack(Items.BEEF));
        putIn(setup, 1, new ItemStack(Items.CARROT));
        putIn(setup, 2, new ItemStack(Items.POTATO));
        // A cook at the station put it in: it waits for the spoon.
        setup.logic.tick(setup.station);
        helper.runAfterDelay(30, () -> {
            setup.logic.tick(setup.station);
            helper.assertTrue(setup.station.state().getControl(CookingPotStation.PROGRESS) == 0.0F, "left alone it does not cook");
            CompoundTag where = new CompoundTag();
            where.putFloat(CookingPotStation.STIR_X, 0.5F);
            setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_STIR, where);
            setup.logic.tick(setup.station);
            helper.assertTrue(setup.station.state().getControl(CookingPotStation.STIRRING) > 0.5F, "the spoon counts");
            helper.assertTrue(setup.station.state().getControl(CookingPotStation.SPOON_X) > 0.4F, "and everybody sees where it is");
        });
        helper.runAfterDelay(60, () -> {
            setup.logic.tick(setup.station);
            float stirred = setup.station.state().getControl(CookingPotStation.PROGRESS);
            helper.assertTrue(stirred > 0.0F, "stirred it cooks");
        });
        helper.runAfterDelay(100, () -> {
            setup.logic.tick(setup.station);
            float after = setup.station.state().getControl(CookingPotStation.PROGRESS);
            helper.assertTrue(setup.station.state().getControl(CookingPotStation.STIRRING) < 0.5F, "the stir runs out");
            helper.runAfterDelay(20, () -> {
                setup.logic.tick(setup.station);
                helper.assertTrue(setup.station.state().getControl(CookingPotStation.PROGRESS) == after, "and it stops where it got to");
                helper.succeed();
            });
        });
    }

    public static void theStationReadsHeat(GameTestHelper helper) {
        Setup setup = pot(helper);
        setup.logic.tick(setup.station);
        helper.assertTrue(setup.station.state().getControl(CookingPotStation.HEATED) > 0.5F, "a lit campfire heats the pot");
        int water = setup.station.state().getTank(CookingPotStation.BROTH).amount();
        helper.assertTrue(water > 0, "there is water in an empty pot");
        Platform.INSTANCE.setItem(setup.pot, 0, new ItemStack(Items.CARROT));
        setup.logic.tick(setup.station);
        helper.assertTrue(setup.station.state().getTank(CookingPotStation.BROTH).amount() > water, "ingredients raise it");
        helper.succeed();
    }

    /** A recipe as a data pack or another mod would add it, put among the server's recipes. */
    private static void addRecipe(GameTestHelper helper, String name, ItemStack result, ItemStack container, int cookTime, Ingredient... inputs) {
        addRecipe(helper, name, TestRecipes.cooking(CookingPotBookCategory.MISC, List.of(inputs), result, container, 0.1F, cookTime));
    }

    private static void addRecipe(GameTestHelper helper, String name, CookingPotRecipe recipe) {
        RecipeManager recipes = helper.getLevel().recipeAccess();
        TestRecipes.add(helper.getLevel(), HandsOnDelight.id("test_" + name), recipe);
        // Autochef's Delight keeps its own list of pot recipes, built when the recipes load: a recipe added here must go into it too.
        try {
            Class.forName("snownee.autochefsdelight.AutochefsDelight").getMethod("buildRecipeCache", RecipeManager.class).invoke(null, recipes);
        } catch (ReflectiveOperationException absent) {
            // Not installed.
        }
    }

    /** An ingredient of a tag's items, of none when the tag is unknown (an ingredient no longer names an empty tag). */
    private static Ingredient tagged(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.get(tag).map(Ingredient::of).orElseGet(() -> RecipeParts.emptyTag(tag));
    }

    private static boolean isLoaded(String className) {
        try {
            Class.forName(className, false, PotTests.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    private static void stirFor(GameTestHelper helper, Setup setup, int ticks) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        for (int tick = 0; tick < ticks; tick += 20) {
            helper.runAfterDelay(tick, () -> setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_STIR, new CompoundTag()));
        }
    }

    /** A modded drink: two servings a time, served in a bottle, one ingredient a tag. */
    public static void aModdedRecipeCooksAsABatch(GameTestHelper helper) {
        addRecipe(helper, "nectar", new ItemStack(Items.HONEY_BOTTLE, 2), new ItemStack(Items.GLASS_BOTTLE), 40, Ingredient.of(Items.SUGAR),
                tagged(BlockItemTags.SMALL_FLOWERS.item()));
        Setup setup = pot(helper);
        Platform.INSTANCE.setItem(setup.pot, 0, new ItemStack(Items.SUGAR, 5));
        Platform.INSTANCE.setItem(setup.pot, 1, new ItemStack(Items.DANDELION, 5));
        helper.assertFalse(setup.logic.mayPlace(setup.station, helper.makeMockPlayer(GameType.CREATIVE), SlotRef.of(CookingPotStation.INGREDIENTS, 2),
                new ItemStack(Items.GLASS_BOTTLE)), "what the drink is served in does not go in");
        stirFor(helper, setup, 140);
        helper.runAfterDelay(140, () -> {
            // Five servings of a 2 s recipe: 2 s * sqrt(5) / 1.5, about three seconds.
            helper.assertTrue(setup.pot.getMeal().is(Items.HONEY_BOTTLE) && setup.pot.getMeal().getCount() == 10,
                    "five servings of two come out together, got " + setup.pot.getMeal());
            helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 0).isEmpty() && Platform.INSTANCE.getItem(setup.pot, 1).isEmpty(),
                    "out of all the ingredients");
            helper.succeed();
        });
    }

    /**
     * Makes a meal of what lies in the pot, as Modest Meals' are to the station: it lists no ingredients, it matches {@code inputs} by
     * its own check, and its dish ({@code made}) carries what went in, unlike its plain result ({@code plain}). A subclass of Farmer's
     * Delight's recipe, whose input is the loader's own type, so each loader's tests make it, served in a bowl and cooked in 2 s.
     */
    public interface MealMaker {
        CookingPotRecipe make(NonNullList<Ingredient> inputs, ItemStack made, ItemStack plain);
    }

    /**
     * With a recipe that makes its meal of whatever lies in the pot (Modest Meals), the inventory offers everything the pot takes, else
     * its food could never go in at the station; and the meal cooks as a batch, the dish being what the recipe makes of the ingredients.
     */
    public static void aMealOfWhatLiesThereCooksAsABatch(GameTestHelper helper, MealMaker maker) {
        Level level = helper.getLevel();
        List<CookingPotRecipe> before = Platform.INSTANCE.cookingRecipes(level).stream().map(RecipeHolder::value).toList();
        ItemStack bush = new ItemStack(Items.DEAD_BUSH);
        if (before.stream().noneMatch(PotLogic::takesAnything)) {
            helper.assertFalse(PotLogic.offered(before, bush), "Farmer's Delight's recipes take no dead bush: the inventory hides it");
        }
        ItemStack made = new ItemStack(Items.COOKIE);
        made.set(DataComponents.CUSTOM_NAME, Component.literal("Meal of what lay there"));
        CookingPotRecipe meal = maker.make(NonNullList.of(RecipeParts.NOTHING, Ingredient.of(Items.DEAD_BUSH), Ingredient.of(Items.SPIDER_EYE)),
                made, new ItemStack(Items.COOKIE));
        List<CookingPotRecipe> with = new ArrayList<>(before);
        with.add(meal);
        helper.assertTrue(PotLogic.offered(with, bush) && PotLogic.offered(with, new ItemStack(Items.SMOOTH_STONE)),
                "beside a meal of anything the inventory offers whatever the pot takes");
        helper.assertTrue(PotLogic.offered(with, new ItemStack(Items.BOWL)), "and the bowl to serve with, as before");
        if (isLoaded("snownee.autochefsdelight.AutochefsDelight")) {
            // Autochef's Delight cooks only recipes that list their ingredients: such a meal never cooks with it, at the station or not.
            helper.succeed();
            return;
        }

        addRecipe(helper, "meal_of_anything", meal);
        Setup setup = pot(helper);
        Platform.INSTANCE.setItem(setup.pot, 0, new ItemStack(Items.DEAD_BUSH, 9));
        Platform.INSTANCE.setItem(setup.pot, 1, new ItemStack(Items.SPIDER_EYE, 9));
        stirFor(helper, setup, 120);
        helper.runAfterDelay(110, () -> {
            // Nine servings of a 2 s recipe: 2 s * 3 / 1.5, four seconds. One at a time the ninth would take three times as long.
            ItemStack cooked = setup.pot.getMeal();
            helper.assertTrue(ItemStack.isSameItemSameComponents(cooked, made) && cooked.getCount() == 9,
                    "nine meals as the recipe makes them come out together, got " + cooked);
            helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 0).isEmpty() && Platform.INSTANCE.getItem(setup.pot, 1).isEmpty(),
                    "out of all the ingredients");
            helper.succeed();
        });
    }

    /** A modded dish with no container goes straight out, as Farmer's Delight serves those, the whole batch. */
    public static void aDishWithoutAContainerComesOutWhole(GameTestHelper helper) {
        // Ingredients no addon cooks with: Cultural Delights makes raw pasta of wheat and an egg.
        addRecipe(helper, "loaf", new ItemStack(Items.BREAD), ItemStack.EMPTY, 40, Ingredient.of(Items.STRING), Ingredient.of(Items.FEATHER));
        Setup setup = pot(helper);
        Platform.INSTANCE.setItem(setup.pot, 0, new ItemStack(Items.STRING, 20));
        Platform.INSTANCE.setItem(setup.pot, 1, new ItemStack(Items.FEATHER, 16));
        stirFor(helper, setup, 200);
        helper.runAfterDelay(200, () -> {
            ItemStack served = Platform.INSTANCE.getItem(setup.pot, CookingPotBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(served.is(Items.BREAD) && served.getCount() == 16, "sixteen loaves, as many as there are feathers, got " + served);
            helper.assertTrue(Platform.INSTANCE.getItem(setup.pot, 0).getCount() == 4, "the string left over stays");
            helper.succeed();
        });
    }

    /**
     * Cooked rice has one ingredient, so Farmer's Delight cooks it from one slot only: a second stack of rice beside the first made no
     * recipe and nothing cooked. The pot keeps a stack and gives the rest back; the rice is served in the bowl the item leaves behind.
     */
    public static void moreRiceThanAStackStillCooks(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        setup.station.users.add(player);
        Platform.INSTANCE.setItem(setup.pot, 0, new ItemStack(fd("rice"), 40));
        Platform.INSTANCE.setItem(setup.pot, 1, new ItemStack(fd("rice"), 40));
        setup.logic.onSlotChanged(setup.station, SlotRef.of(CookingPotStation.INGREDIENTS, 1));
        setup.logic.tick(setup.station);
        // Without a mod that matches stacks (Autochef's Delight) a stack stays in the pot and the rest goes back.
        helper.assertTrue(cooks(helper, setup.pot), "the rice makes the recipe, got " + Platform.INSTANCE.getItem(setup.pot, 0) + " + "
                + Platform.INSTANCE.getItem(setup.pot, 1));
        helper.assertTrue(riceInPot(setup.pot) + player.getInventory().countItem(fd("rice")) == 80, "no rice is lost");
        stirFor(helper, setup, 300);
        helper.runAfterDelay(300, () -> {
            // Sixteen servings of a 5 s recipe: 5 s * 4 / 1.5, about thirteen seconds.
            helper.assertTrue(setup.pot.getMeal().is(fd("cooked_rice")) && setup.pot.getMeal().getCount() == 16,
                    "sixteen servings of rice, got " + setup.pot.getMeal());
            player.inventoryMenu.setCarried(new ItemStack(Items.BOWL, 2));
            setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_SCOOP, new CompoundTag());
            helper.assertTrue(player.getInventory().contains(new ItemStack(fd("cooked_rice"))), "a bowl takes a serving");
            empty(setup.pot);
            helper.succeed();
        });
    }

    /** A stack of rice put in while the pot cooks: the batch goes on from where it was. */
    public static void riceAddedWhileCookingKeepsTheProgress(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        setup.station.users.add(player);
        Platform.INSTANCE.setItem(setup.pot, 0, new ItemStack(fd("rice"), 60));
        stirFor(helper, setup, 300);
        float[] before = new float[1];
        helper.runAfterDelay(100, () -> {
            setup.logic.tick(setup.station);
            before[0] = setup.station.state().getControl(CookingPotStation.PROGRESS);
            Platform.INSTANCE.setItem(setup.pot, 1, new ItemStack(fd("rice"), 20));
            setup.logic.onSlotChanged(setup.station, SlotRef.of(CookingPotStation.INGREDIENTS, 1));
            // The station shares the rice out on its tick, as in game: the pot never stands a tick without its recipe.
            setup.logic.tick(setup.station);
        });
        helper.runAfterDelay(110, () -> {
            setup.logic.tick(setup.station);
            float after = setup.station.state().getControl(CookingPotStation.PROGRESS);
            helper.assertTrue(before[0] > 0.2F && after > before[0], "the progress goes on, " + before[0] + " -> " + after);
            helper.assertTrue(cooks(helper, setup.pot), "the rice makes the recipe");
            empty(setup.pot);
            helper.succeed();
        });
    }

    /**
     * The pot's cooking time in the config: 1.0 keeps the recipe's own time, 2.0 doubles it. It is the total the pot's step counts to,
     * so food left alone, a stirred batch (at its speed over that total), the badge and Farmer's Delight's own menu all follow it. Both
     * steps run in this one tick: the config is the whole game's.
     */
    public static void theCookingTimeFollowsTheConfig(GameTestHelper helper) {
        Setup setup = pot(helper);
        Platform.INSTANCE.setItem(setup.pot, 0, new ItemStack(fd("rice"), 4));
        CookingPotRecipe recipe = Platform.INSTANCE.findRecipe(helper.getLevel(), List.of(new ItemStack(fd("rice")))).orElseThrow().value();
        BlockState state = helper.getLevel().getBlockState(setup.pot.getBlockPos());
        float pot = HandsOnConfig.cookingPotTime();
        float skillet = HandsOnConfig.skilletTime();
        float stove = HandsOnConfig.stoveTime();
        int[] totals = new int[2];
        try {
            HandsOnConfig.setCookingTimes(1.0F, skillet, stove);
            CookingPotBlockEntity.cookingTick(helper.getLevel(), setup.pot.getBlockPos(), state, setup.pot);
            totals[0] = ((KitchenPot) setup.pot).handson$cookTimeTotal();
            HandsOnConfig.setCookingTimes(2.0F, skillet, stove);
            CookingPotBlockEntity.cookingTick(helper.getLevel(), setup.pot.getBlockPos(), state, setup.pot);
            totals[1] = ((KitchenPot) setup.pot).handson$cookTimeTotal();
        } finally {
            HandsOnConfig.setCookingTimes(pot, skillet, stove);
        }
        helper.assertTrue(totals[0] == recipe.getCookTime(), "1.0: the recipe's " + recipe.getCookTime() + " ticks, got " + totals[0]);
        helper.assertTrue(totals[1] == 2 * recipe.getCookTime(), "2.0: twice the recipe's time, got " + totals[1]);
        empty(setup.pot);
        helper.succeed();
    }

    /**
     * A client is sent the pot's recipes as a dedicated server's connection writes them (it gets none from the game since 1.21.2) and
     * reads each back whole: the book lists every dish of Farmer's Delight. A recipe the client could not read (a tag no mod fills, as
     * an addon's recipe for a mod that is not there) is left out alone. Other tests lay their own recipes in meanwhile: only Farmer's
     * Delight's are compared.
     */
    public static void theClientIsSentThePotsRecipes(GameTestHelper helper) {
        List<RecipeHolder<?>> recipes = new ArrayList<>(KitchenRecipes.payload(helper.getLevel()).recipes());
        List<RecipeHolder<?>> dishes = recipes.stream().filter(holder -> holder.value().getType() == ModRecipeTypes.COOKING.get()
                && holder.id().identifier().getNamespace().equals(FarmersDelightContent.MOD_ID)).toList();
        Identifier unreadable = HandsOnDelight.id("test/stew_of_a_missing_mod");
        TagKey<Item> unfilled = TagKey.create(Registries.ITEM, HandsOnDelight.id("test/filled_by_no_mod"));
        recipes.add(new RecipeHolder<>(TestRecipes.key(unreadable), TestRecipes.cooking(CookingPotBookCategory.MEALS,
                List.of(Ingredient.of(HolderSet.emptyNamed(BuiltInRegistries.ITEM, unfilled)), Ingredient.of(Items.BEEF)),
                new ItemStack(fd("beef_stew")), new ItemStack(Items.BOWL), 0.0F, 200)));
        KitchenRecipes.Payload received = TestConnection.sent(helper.getLevel(), new KitchenRecipes.Payload(recipes));
        Map<Identifier, RecipeHolder<?>> read = new HashMap<>();
        received.recipes().forEach(holder -> read.put(holder.id().identifier(), holder));
        helper.assertTrue(!read.containsKey(unreadable), "the recipe naming a tag no mod fills is left out");
        helper.assertTrue(!dishes.isEmpty(), "the server has the pot's recipes");
        for (RecipeHolder<?> dish : dishes) {
            RecipeHolder<?> sent = read.get(dish.id().identifier());
            helper.assertTrue(sent != null, "the client is sent " + dish.id().identifier() + ", " + read.size() + " recipes read");
            helper.assertTrue(ItemStack.isSameItemSameComponents(RecipeParts.result(sent.value()), RecipeParts.result(dish.value()))
                    && RecipeParts.ingredients(sent.value()).size() == RecipeParts.ingredients(dish.value()).size(),
                    dish.id().identifier() + " reads back with its result and ingredients");
        }
        helper.succeed();
    }

    private static boolean cooks(GameTestHelper helper, CookingPotBlockEntity pot) {
        List<ItemStack> inPot = new ArrayList<>();
        for (int i = 0; i < CookingPotBlockEntity.MEAL_DISPLAY_SLOT; i++) {
            inPot.add(Platform.INSTANCE.getItem(pot, i));
        }
        return Platform.INSTANCE.findRecipe(helper.getLevel(), inPot).isPresent();
    }

    private static int riceInPot(CookingPotBlockEntity pot) {
        int rice = 0;
        for (int i = 0; i < CookingPotBlockEntity.MEAL_DISPLAY_SLOT; i++) {
            rice += Platform.INSTANCE.getItem(pot, i).is(fd("rice")) ? Platform.INSTANCE.getItem(pot, i).getCount() : 0;
        }
        return rice;
    }

    /** Empties the pot, so nothing spills over the tests beside it when its structure is cleared. */
    private static void empty(CookingPotBlockEntity pot) {
        for (int i = 0; i <= CookingPotBlockEntity.OUTPUT_SLOT; i++) {
            Platform.INSTANCE.setItem(pot, i, ItemStack.EMPTY);
        }
    }

    /** A station of {@code type} at a block, as the library would make it, with nobody at it. */
    static final class TestStation implements StationContext {
        private final Level level;
        private final BlockPos pos;
        private final StationType type;
        private final StationState state;
        final List<Player> users = new ArrayList<>();

        TestStation(Level level, BlockPos pos) {
            this(level, pos, CookingPotStation.TYPE);
        }

        TestStation(Level level, BlockPos pos, StationType type) {
            this.level = level;
            this.pos = pos;
            this.type = type;
            this.state = new StationState(type.schema());
        }

        @Override
        public StationType type() {
            return type;
        }

        @Override
        public Level level() {
            return level;
        }

        @Override
        public BlockPos pos() {
            return pos;
        }

        @Override
        public BlockState blockState() {
            return level.getBlockState(pos);
        }

        @Override
        public Direction facing() {
            return Direction.NORTH;
        }

        @Override
        public StationState state() {
            return state;
        }

        @Override
        public Collection<? extends Player> users() {
            return users;
        }

        @Override
        public void effect(Identifier effect, String node, CompoundTag data) {
        }
    }
}
