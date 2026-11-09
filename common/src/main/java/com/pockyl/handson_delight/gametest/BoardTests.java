package com.pockyl.handson_delight.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;

import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.assembly.AssemblyRecipes;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.BoardLayers;
import com.pockyl.handson_delight.station.BoardLogic;
import com.pockyl.handson_delight.station.Cutting;
import com.pockyl.handson_delight.station.CuttingBoardStation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The cutting board's rules against a real board of Farmer's Delight, on every loader, like {@link PotTests}. */
public final class BoardTests {
    private BoardTests() {
    }

    private static Item fd(String path) {
        return BuiltInRegistries.ITEM.get(FarmersDelightContent.id(path));
    }

    /** A board, the logic the station would make for it, and a cook holding nothing. */
    private record Board(Station station, BoardLogic logic, CuttingBoardBlockEntity board, Player cook, ItemStack knife) {
        void carry(ItemStack stack) {
            cook.inventoryMenu.setCarried(stack);
        }

        void act(String action, CompoundTag data) {
            logic.onCustomAction(station, cook, action, data);
        }

        void place() {
            act(CuttingBoardStation.ACTION_PLACE, new CompoundTag());
        }

        void chop(int mark) {
            if (cook.inventoryMenu.getCarried().isEmpty()) {
                carry(knife);
            }
            CompoundTag at = new CompoundTag();
            at.putInt(CuttingBoardStation.MARK, mark);
            at.putFloat(CuttingBoardStation.AT, mark == 0 ? 10.0F : 6.0F);
            act(CuttingBoardStation.ACTION_CHOP, at);
        }

        int layers() {
            return BoardLogic.layers(station.state()).size();
        }

        ItemStack onBoard() {
            return Platform.INSTANCE.getItem(board);
        }
    }

    private static Board board(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        helper.setBlock(at, FarmersDelightContent.CUTTING_BOARD.get());
        BlockPos pos = helper.absolutePos(at);
        Station station = new Station(helper.getLevel(), pos);
        CuttingBoardBlockEntity board = (CuttingBoardBlockEntity) helper.getLevel().getBlockEntity(pos);
        return new Board(station, new BoardLogic(station), board, helper.makeMockPlayer(), new ItemStack(fd("iron_knife")));
    }

    /**
     * A burger is put together on the board: a player's crafting grid does not make it and the recipe book does not learn it; the grid
     * of a machine (an autocrafter in a modpack) still makes it.
     */
    public static void burgersLeaveTheCraftingTable(GameTestHelper helper) {
        Level level = helper.getLevel();
        Player player = helper.makeMockPlayer();
        CraftingContainer table = burger(new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 3, 3));
        CraftingContainer machine = burger(new TransientCraftingContainer(new MachineMenu(), 3, 3));
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, table, level).isEmpty(),
                "the crafting table makes no hamburger");
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, machine, level)
                .filter(recipe -> recipe.getResultItem(level.registryAccess()).is(fd("hamburger"))).isPresent(), "an autocrafter still does");
        Recipe<?> hamburger = level.getRecipeManager().byKey(FarmersDelightContent.id("hamburger")).orElseThrow();
        ServerPlayer cook = new ServerPlayer(level.getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "cook"));
        helper.assertTrue(cook.getRecipeBook().addRecipes(List.of(hamburger), cook) == 0 && !cook.getRecipeBook().contains(hamburger),
                "and the recipe book does not learn it");
        helper.assertTrue(AssemblyRecipes.all().stream().anyMatch(dish -> dish.id().getPath().equals("hamburger")), "the board does");
        helper.assertTrue(level.getRecipeManager().byKey(FarmersDelightContent.id("cooking_pot")).isPresent(), "blocks are still crafted");
        helper.succeed();
    }

    /**
     * Plated meals and salads go down from the bowl up. What is one kind of thing put back together (slices into a pie, leaves into
     * a cabbage) and what is no food (horse feed, milk bottles) stays in the crafting table.
     */
    public static void bowlDishesLeaveTheCraftingTable(GameTestHelper helper) {
        for (String dish : List.of("bacon_and_eggs", "steak_and_potatoes", "roasted_mutton_chops", "grilled_salmon", "mixed_salad",
                "fruit_salad", "nether_salad")) {
            helper.assertTrue(made(dish).filter(it -> it.kind() == AssemblyRecipes.Base.BOWL).isPresent(), dish + " starts from its bowl");
        }
        for (String crafted : List.of("apple_pie_from_slices", "cabbage_from_leaves", "horse_feed", "milk_bottle", "cooking_pot")) {
            helper.assertTrue(made(crafted).isEmpty(), crafted + " stays in the crafting table");
        }
        Level level = helper.getLevel();
        Player player = helper.makeMockPlayer();
        CraftingContainer table = netherSalad(new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 3, 3));
        CraftingContainer machine = netherSalad(new TransientCraftingContainer(new MachineMenu(), 3, 3));
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, table, level).isEmpty(), "the table makes no salad");
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, machine, level).isPresent(), "an autocrafter does");
        helper.succeed();
    }

    private static Optional<AssemblyRecipes.Assembly> made(String path) {
        return AssemblyRecipes.all().stream().filter(dish -> dish.id().equals(FarmersDelightContent.id(path))).findFirst();
    }

    private static CraftingContainer netherSalad(CraftingContainer grid) {
        grid.setItem(0, new ItemStack(Items.CRIMSON_FUNGUS));
        grid.setItem(1, new ItemStack(Items.WARPED_FUNGUS));
        grid.setItem(4, new ItemStack(Items.BOWL));
        return grid;
    }

    /** Every other kind of FD food craft goes onto the board too, each starting from what goes down first. */
    public static void everyFoodCraftComesToTheBoard(GameTestHelper helper) {
        Map<String, AssemblyRecipes.Base> kinds = Map.ofEntries(Map.entry("kelp_roll", AssemblyRecipes.Base.KELP),
                Map.entry("cod_roll", AssemblyRecipes.Base.RICE), Map.entry("salmon_roll", AssemblyRecipes.Base.RICE),
                Map.entry("barbecue_stick", AssemblyRecipes.Base.STICK), Map.entry("melon_popsicle", AssemblyRecipes.Base.STICK),
                Map.entry("wheat_dough_from_egg", AssemblyRecipes.Base.WHEAT), Map.entry("wheat_dough_from_water", AssemblyRecipes.Base.WHEAT),
                Map.entry("pie_crust", AssemblyRecipes.Base.WHEAT), Map.entry("honey_cookie", AssemblyRecipes.Base.WHEAT),
                Map.entry("sweet_berry_cookie", AssemblyRecipes.Base.WHEAT), Map.entry("cake_from_milk_bottle", AssemblyRecipes.Base.WHEAT),
                Map.entry("apple_pie", AssemblyRecipes.Base.CRUST), Map.entry("chocolate_pie", AssemblyRecipes.Base.CRUST),
                Map.entry("sweet_berry_cheesecake", AssemblyRecipes.Base.CRUST),
                Map.entry("pumpkin_pie_from_pie_crust", AssemblyRecipes.Base.CRUST), Map.entry("melon_juice", AssemblyRecipes.Base.BOTTLE),
                Map.entry("stuffed_potato", AssemblyRecipes.Base.FIRST), Map.entry("roast_chicken_block", AssemblyRecipes.Base.BOWL),
                Map.entry("honey_glazed_ham_block", AssemblyRecipes.Base.BOWL), Map.entry("shepherds_pie_block", AssemblyRecipes.Base.BOWL),
                Map.entry("rice_roll_medley_block", AssemblyRecipes.Base.BOWL), Map.entry("gleaming_salad_block", AssemblyRecipes.Base.BOWL));
        kinds.forEach((dish, base) -> helper.assertTrue(made(dish).filter(it -> it.kind() == base).isPresent(), dish + " goes down from its " + base));
        helper.assertTrue(made("honey_glazed_ham_block").map(AssemblyRecipes.Assembly::finish).orElse(null) == AssemblyRecipes.Finish.PRESS,
                "a feast is pressed together, not tossed");
        for (String crafted : List.of("cake_from_slices", "pumpkin_pie_from_slices", "pumpkin_from_slices", "milk_bucket_from_bottles",
                "milk_bottle", "rice_panicle", "tomato_seeds")) {
            helper.assertTrue(made(crafted).isEmpty(), crafted + " stays in the crafting table");
        }
        Level level = helper.getLevel();
        CraftingContainer table = new TransientCraftingContainer(new CraftingMenu(0, helper.makeMockPlayer().getInventory()), 3, 3);
        table.setItem(0, new ItemStack(Items.WHEAT));
        table.setItem(1, new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, table, level).isEmpty(),
                "the table makes no dough from water either, though it is a recipe of its own type");
        helper.succeed();
    }

    /** Dough from water: as many as there is wheat, and the bucket of water stays full, as in the crafting table. */
    public static void doughKeepsItsWater(GameTestHelper helper) {
        Board setup = board(helper);
        setup.carry(new ItemStack(Items.WHEAT, 5));
        setup.place();
        setup.carry(new ItemStack(Items.WATER_BUCKET));
        setup.place();
        helper.assertTrue(setup.onBoard().is(fd("wheat_dough")) && setup.onBoard().getCount() == 5, "five dough of five wheat");
        helper.assertTrue(setup.cook.getInventory().countItem(Items.WATER_BUCKET) == 1, "and the water comes back");
        helper.succeed();
    }

    /** A shaped recipe: the crust first, then all the rest in any order; the pie is a block and lies on the board. */
    public static void aPieGoesIntoItsCrust(GameTestHelper helper) {
        Board setup = board(helper);
        setup.carry(new ItemStack(fd("pie_crust"), 2));
        setup.place();
        for (Item layer : List.of(Items.APPLE, Items.SUGAR, Items.WHEAT, Items.APPLE, Items.WHEAT, Items.SUGAR, Items.APPLE, Items.WHEAT)) {
            setup.carry(new ItemStack(layer, 2));
            setup.place();
        }
        helper.assertTrue(setup.layers() == 0 && setup.onBoard().is(fd("apple_pie")) && setup.onBoard().getCount() == 2,
                "two apple pies of nine layers");
        helper.succeed();
    }

    /** Milk from a bucket: the bucket comes back, and a bucket goes down only one at a time, so one potato is stuffed. */
    public static void milkGivesItsBucketBack(GameTestHelper helper) {
        Board setup = board(helper);
        setup.carry(new ItemStack(Items.BAKED_POTATO, 3));
        setup.place();
        setup.carry(new ItemStack(Items.COOKED_BEEF, 3));
        setup.place();
        setup.carry(new ItemStack(Items.MILK_BUCKET));
        setup.place();
        helper.assertTrue(setup.onBoard().is(fd("stuffed_potato")) && setup.onBoard().getCount() == 1, "one stuffed potato");
        Inventory inventory = setup.cook.getInventory();
        helper.assertTrue(inventory.countItem(Items.BUCKET) == 1 && inventory.countItem(Items.MILK_BUCKET) == 0, "the empty bucket comes back");
        helper.assertTrue(inventory.countItem(Items.BAKED_POTATO) == 2 && inventory.countItem(Items.COOKED_BEEF) == 2, "and the rest");
        helper.succeed();
    }

    /** Cookies come eight a round; the honey's bottles come back empty. */
    public static void cookiesComeEightARound(GameTestHelper helper) {
        Board setup = board(helper);
        for (Item layer : List.of(Items.WHEAT, Items.HONEY_BOTTLE, Items.WHEAT)) {
            setup.carry(new ItemStack(layer, 2));
            setup.place();
        }
        helper.assertTrue(setup.onBoard().is(fd("honey_cookie")) && setup.onBoard().getCount() == 16, "sixteen cookies of two rounds");
        helper.assertTrue(setup.cook.getInventory().countItem(Items.GLASS_BOTTLE) == 2, "two empty bottles");
        helper.succeed();
    }

    /** A feast takes nine things: the book lays them all from the inventory, and the feast lies on the board. */
    public static void aFeastIsLaidFromTheBook(GameTestHelper helper) {
        Board setup = board(helper);
        Inventory inventory = setup.cook.getInventory();
        inventory.setItem(0, new ItemStack(Items.SWEET_BERRIES, 4));
        inventory.setItem(1, new ItemStack(Items.HONEY_BOTTLE));
        inventory.setItem(2, new ItemStack(fd("smoked_ham")));
        inventory.setItem(3, new ItemStack(fd("cooked_rice"), 2));
        inventory.setItem(4, new ItemStack(Items.BOWL));
        CompoundTag pick = new CompoundTag();
        pick.putString(CuttingBoardStation.DISH, FarmersDelightContent.id("honey_glazed_ham_block").toString());
        setup.act(CuttingBoardStation.ACTION_PICK, pick);
        helper.assertTrue(setup.onBoard().is(fd("honey_glazed_ham_block")), "the ham feast lies on the board");
        helper.assertTrue(inventory.countItem(Items.SWEET_BERRIES) == 0 && inventory.countItem(Items.GLASS_BOTTLE) == 1,
                "everything was used; the honey's bottle came back");
        helper.succeed();
    }

    /** The bowl goes down first, then what it holds; the thinnest layer says how many, the rest goes back. */
    public static void aSaladStartsFromItsBowl(GameTestHelper helper) {
        Board setup = board(helper);
        setup.carry(new ItemStack(Items.CRIMSON_FUNGUS, 5));
        setup.place();
        helper.assertTrue(setup.layers() == 0 && setup.onBoard().isEmpty(), "a fungus is no bowl");
        setup.carry(new ItemStack(Items.BOWL, 3));
        setup.place();
        setup.carry(new ItemStack(Items.CRIMSON_FUNGUS, 5));
        setup.place();
        helper.assertTrue(setup.layers() == 2 && setup.cook.inventoryMenu.getCarried().getCount() == 2, "three fungi, as three bowls");
        setup.carry(new ItemStack(Items.WARPED_FUNGUS, 2));
        setup.place();
        helper.assertTrue(setup.layers() == 0, "the last one makes the salad");
        helper.assertTrue(setup.onBoard().is(fd("nether_salad")) && setup.onBoard().getCount() == 2, "two, as there were two warped");
        helper.assertTrue(setup.cook.getInventory().countItem(Items.BOWL) == 1 && setup.cook.getInventory().countItem(Items.CRIMSON_FUNGUS) == 1,
                "a bowl and a fungus go back");
        helper.succeed();
    }

    /** A dish picked in the book is laid from the inventory: a batch with shift, one set without; a dish made before is taken off. */
    public static void theBookLaysADishFromTheInventory(GameTestHelper helper) {
        Board setup = board(helper);
        setup.cook.getInventory().setItem(5, new ItemStack(Items.BOWL, 20));
        setup.cook.getInventory().setItem(6, new ItemStack(Items.CRIMSON_FUNGUS, 20));
        setup.cook.getInventory().setItem(7, new ItemStack(Items.WARPED_FUNGUS, 20));
        CompoundTag pick = new CompoundTag();
        pick.putString(CuttingBoardStation.DISH, FarmersDelightContent.id("nether_salad").toString());
        pick.putBoolean(CuttingBoardStation.ALL, true);
        setup.act(CuttingBoardStation.ACTION_PICK, pick);
        helper.assertTrue(setup.onBoard().is(fd("nether_salad")) && setup.onBoard().getCount() == CuttingBoardStation.BATCH,
                "sixteen salads with shift");
        helper.assertTrue(setup.cook.getInventory().countItem(Items.BOWL) == 4 && setup.cook.getInventory().countItem(Items.WARPED_FUNGUS) == 4,
                "taken from the inventory");
        pick.putBoolean(CuttingBoardStation.ALL, false);
        setup.act(CuttingBoardStation.ACTION_PICK, pick);
        helper.assertTrue(setup.onBoard().getCount() == 1 && setup.cook.getInventory().countItem(fd("nether_salad")) == CuttingBoardStation.BATCH,
                "one more without shift; the sixteen went to the cook");
        setup.cook.getInventory().clearContent();
        setup.act(CuttingBoardStation.ACTION_TAKE, new CompoundTag());
        setup.act(CuttingBoardStation.ACTION_PICK, pick);
        helper.assertTrue(setup.layers() == 0 && setup.onBoard().isEmpty(), "nothing in the inventory, nothing on the board");
        setup.carry(new ItemStack(Items.BOWL, 3));
        setup.place();
        setup.cook.getInventory().setItem(1, new ItemStack(Items.CRIMSON_FUNGUS, 5));
        setup.cook.getInventory().setItem(2, new ItemStack(Items.WARPED_FUNGUS, 5));
        setup.act(CuttingBoardStation.ACTION_PICK, pick);
        helper.assertTrue(setup.onBoard().is(fd("nether_salad")) && setup.onBoard().getCount() == 3,
                "onto three bowls the book lays the rest of three salads");
        helper.succeed();
    }

    private static CraftingContainer burger(CraftingContainer grid) {
        grid.setItem(0, new ItemStack(Items.BREAD));
        grid.setItem(1, new ItemStack(fd("beef_patty")));
        grid.setItem(2, new ItemStack(fd("cabbage_leaf")));
        grid.setItem(3, new ItemStack(fd("tomato")));
        grid.setItem(4, new ItemStack(fd("onion")));
        return grid;
    }

    /** A crafting grid that is no player's, as a machine has. */
    private static final class MachineMenu extends AbstractContainerMenu {
        MachineMenu() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    public static void aBurgerIsBuiltFromTheBreadUp(GameTestHelper helper) {
        Board setup = board(helper);
        setup.carry(new ItemStack(fd("tomato"), 4));
        setup.place();
        helper.assertTrue(setup.layers() == 0 && setup.onBoard().isEmpty(), "a tomato is no base, and the board does not cut it");
        helper.assertTrue(setup.cook.inventoryMenu.getCarried().getCount() == 4, "it stays in the hand");

        setup.carry(new ItemStack(Items.BREAD, 3));
        setup.place();
        helper.assertTrue(setup.layers() == 1, "the bread goes down");
        helper.assertTrue(setup.cook.inventoryMenu.getCarried().isEmpty(), "all three: a batch of three");
        for (Item layer : List.of(fd("beef_patty"), fd("cabbage_leaf"), fd("tomato"))) {
            setup.carry(new ItemStack(layer));
            setup.place();
        }
        setup.carry(new ItemStack(Items.CARROT));
        setup.place();
        helper.assertTrue(setup.layers() == 4, "a carrot belongs to no burger");
        setup.act(CuttingBoardStation.ACTION_ASSEMBLE, new CompoundTag());
        helper.assertTrue(setup.layers() == 4, "without the onion it is no burger yet");

        setup.carry(new ItemStack(fd("onion")));
        setup.place();
        helper.assertTrue(setup.layers() == 0, "the last layer makes the burger at once: nothing bigger starts this way");
        helper.assertTrue(setup.onBoard().is(fd("hamburger")) && setup.onBoard().getCount() == 1, "one, as the patty was one");
        helper.assertTrue(setup.cook.getInventory().countItem(Items.BREAD) == 2, "the bread not used goes back");
        setup.act(CuttingBoardStation.ACTION_TAKE, new CompoundTag());
        helper.assertTrue(setup.cook.getInventory().countItem(fd("hamburger")) == 1, "a click gives it to the cook");
        helper.succeed();
    }

    public static void sixteenBurgersAtOnce(GameTestHelper helper) {
        Board setup = board(helper);
        for (Item layer : List.of(Items.BREAD, fd("beef_patty"), fd("cabbage_leaf"), fd("tomato"), fd("onion"))) {
            setup.carry(new ItemStack(layer, 20));
            setup.place();
        }
        helper.assertTrue(setup.onBoard().is(fd("hamburger")) && setup.onBoard().getCount() == CuttingBoardStation.BATCH,
                "sixteen burgers from stacks of twenty");
        helper.assertTrue(setup.cook.inventoryMenu.getCarried().getCount() == 4, "the onions over sixteen stay in the hand");
        helper.succeed();
    }

    public static void theTopLayerComesBack(GameTestHelper helper) {
        Board setup = board(helper);
        setup.carry(new ItemStack(Items.BREAD));
        setup.place();
        setup.carry(new ItemStack(fd("tomato")));
        setup.place();
        setup.act(CuttingBoardStation.ACTION_TAKE, new CompoundTag());
        helper.assertTrue(setup.layers() == 1, "one layer off");
        helper.assertTrue(setup.cook.getInventory().countItem(fd("tomato")) == 1, "the top one, back to the cook");
        helper.succeed();
    }

    public static void aHamTakesTwoChopsAndFeedsTheCook(GameTestHelper helper) {
        Board setup = board(helper);
        setup.carry(new ItemStack(fd("ham"), 2));
        setup.place();
        helper.assertTrue(setup.onBoard().getCount() == 2, "the whole stack lies on the board");
        setup.chop(0);
        helper.assertTrue(setup.onBoard().getCount() == 2, "one chop does not cut a ham");
        helper.runAfterDelay(4, () -> {
            setup.chop(0);
            helper.assertTrue(setup.onBoard().getCount() == 2, "nor two on the same mark");
        });
        helper.runAfterDelay(8, () -> {
            setup.chop(1);
            helper.assertTrue(setup.onBoard().isEmpty(), "the second mark cuts both hams");
            helper.assertTrue(setup.cook.getInventory().countItem(Items.PORKCHOP) == 4, "their pieces go to the cook");
            helper.assertTrue(setup.cook.getInventory().countItem(Items.BONE) == 2, "all of them");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(setup.station.pos()).inflate(3)).isEmpty(),
                    "nothing falls on the floor");
            helper.succeed();
        });
    }

    public static void anEmptyHandCutsWithTheKnifeInTheInventory(GameTestHelper helper) {
        Board setup = board(helper);
        setup.carry(new ItemStack(fd("cabbage"), 2));
        setup.place();
        setup.cook.getInventory().setItem(7, setup.knife.copy());
        CompoundTag first = new CompoundTag();
        first.putInt(CuttingBoardStation.MARK, 0);
        first.putFloat(CuttingBoardStation.AT, 10.0F);
        setup.act(CuttingBoardStation.ACTION_CHOP, first);
        helper.runAfterDelay(4, () -> {
            CompoundTag second = new CompoundTag();
            second.putInt(CuttingBoardStation.MARK, 1);
            second.putFloat(CuttingBoardStation.AT, 6.0F);
            setup.act(CuttingBoardStation.ACTION_CHOP, second);
            helper.assertTrue(setup.onBoard().isEmpty(), "both cabbages are cut");
            helper.assertTrue(setup.cook.getInventory().countItem(fd("cabbage_leaf")) == 4, "the leaves go to the cook");
            helper.assertTrue(setup.cook.getInventory().getItem(7).is(fd("iron_knife")), "the knife stays in its slot");
            helper.assertTrue(setup.cook.inventoryMenu.getCarried().isEmpty(), "the hand stays empty");
            helper.succeed();
        });
    }

    public static void anEmptyHandWithoutAKnifeCutsNothing(GameTestHelper helper) {
        Board setup = board(helper);
        setup.carry(new ItemStack(fd("cabbage"), 1));
        setup.place();
        CompoundTag first = new CompoundTag();
        first.putInt(CuttingBoardStation.MARK, 0);
        setup.act(CuttingBoardStation.ACTION_CHOP, first);
        helper.runAfterDelay(4, () -> {
            CompoundTag second = new CompoundTag();
            second.putInt(CuttingBoardStation.MARK, 1);
            setup.act(CuttingBoardStation.ACTION_CHOP, second);
            helper.assertTrue(setup.onBoard().getCount() == 1, "no knife, no cut");
            helper.succeed();
        });
    }

    public static void sixteenAreCutAtOnce(GameTestHelper helper) {
        Board setup = board(helper);
        setup.carry(new ItemStack(fd("cabbage"), 20));
        setup.place();
        setup.chop(0);
        helper.runAfterDelay(4, () -> {
            setup.chop(1);
            helper.assertTrue(setup.onBoard().getCount() == 20 - CuttingBoardStation.BATCH, "one cut goes through sixteen cabbages");
            helper.assertTrue(setup.cook.getInventory().countItem(fd("cabbage_leaf")) == 2 * CuttingBoardStation.BATCH, "two leaves each");
        });
        helper.runAfterDelay(8, () -> setup.chop(0));
        helper.runAfterDelay(12, () -> {
            setup.chop(1);
            helper.assertTrue(setup.onBoard().isEmpty(), "the next cut takes the rest");
            helper.succeed();
        });
    }

    public static void shiftClickLaysTheStackFromTheInventory(GameTestHelper helper) {
        Board setup = board(helper);
        setup.cook.getInventory().setItem(3, new ItemStack(fd("cabbage"), 5));
        CompoundTag from = new CompoundTag();
        from.putInt(CuttingBoardStation.FROM, 3);
        setup.act(CuttingBoardStation.ACTION_PLACE, from);
        helper.assertTrue(setup.onBoard().is(fd("cabbage")) && setup.onBoard().getCount() == 5, "the whole stack lies on the board");
        helper.assertTrue(setup.cook.getInventory().getItem(3).isEmpty(), "and left its slot");
        setup.cook.getInventory().setItem(4, new ItemStack(Items.BREAD, 2));
        from.putInt(CuttingBoardStation.FROM, 4);
        setup.act(CuttingBoardStation.ACTION_PLACE, from);
        helper.assertTrue(setup.cook.getInventory().getItem(4).getCount() == 2, "bread does not go onto food being cut");
        from.putInt(CuttingBoardStation.FROM, 99);
        setup.act(CuttingBoardStation.ACTION_PLACE, from);
        helper.succeed();
    }

    public static void marksAreCutInOrderAtNewPlaces(GameTestHelper helper) {
        Board setup = board(helper);
        setup.carry(new ItemStack(fd("cabbage"), 2));
        setup.place();
        float[] first = setup.logic.spots();
        helper.assertTrue(Math.abs(first[0] - first[1]) >= 0.3F, "the two marks lie apart");
        for (float spot : first) {
            helper.assertTrue(spot >= 0.2F && spot <= 0.8F, "on the item, never at its ends");
        }
        float[] angles = setup.logic.angles();
        for (float angle : angles) {
            helper.assertTrue(Math.abs(angle) <= 60.0F * Mth.DEG_TO_RAD + 1.0e-4F, "straight across or diagonal, never along the item");
        }
        helper.assertFalse(BoardLogic.crossOnTheItem(first[0], angles[0], first[1], angles[1]), "the two cuts never meet on the item");
        helper.assertTrue(BoardLogic.crossOnTheItem(0.5F, 0.5F, 0.5F, -0.5F), "two marks through one point do");
        helper.assertFalse(BoardLogic.crossOnTheItem(0.2F, 0.0F, 0.8F, 0.0F), "two straight across do not");
        setup.chop(1);
        helper.runAfterDelay(4, () -> {
            setup.chop(0);
            helper.assertTrue(setup.onBoard().getCount() == 2, "the second mark is not there before the first is cut");
        });
        helper.runAfterDelay(8, () -> {
            setup.chop(1);
            helper.assertTrue(setup.onBoard().isEmpty(), "then it cuts");
            setup.carry(new ItemStack(fd("cabbage")));
            setup.place();
            float[] next = setup.logic.spots();
            helper.assertFalse(next[0] == first[0] && next[1] == first[1], "the next item has marks of its own");
            helper.succeed();
        });
    }

    public static void layersStayOnTheBoard(GameTestHelper helper) {
        Board setup = board(helper);
        setup.carry(new ItemStack(Items.BREAD));
        setup.place();
        setup.carry(new ItemStack(fd("beef_patty")));
        setup.place();
        Station again = new Station(helper.getLevel(), setup.station.pos());
        new BoardLogic(again);
        helper.assertTrue(BoardLogic.layers(again.state()).size() == 2, "the next cook finds the dish where it was");
        CompoundTag saved = setup.board.saveWithoutMetadata();
        CuttingBoardBlockEntity copy = (CuttingBoardBlockEntity) setup.board.getType().create(setup.station.pos(), setup.station.blockState());
        copy.load(saved);
        helper.assertTrue(((BoardLayers) copy).handson$layers().get(1).is(fd("beef_patty")), "it is saved with the board");
        helper.succeed();
    }

    public static void woodStaysWithFarmersDelight(GameTestHelper helper) {
        Level level = helper.getLevel();
        helper.assertTrue(Cutting.isOthers(level, new ItemStack(Items.OAK_LOG)), "a log is Farmer's Delight's own board's");
        helper.assertTrue(Cutting.isOthers(level, new ItemStack(Items.POPPY)), "so is a flower");
        helper.assertFalse(Cutting.isOthers(level, new ItemStack(fd("cabbage"))), "a cabbage is cooking");
        helper.assertTrue(Cutting.all(level).stream().noneMatch(recipe -> recipe.getIngredients().get(0).test(new ItemStack(Items.OAK_LOG))),
                "the book has no wood");
        helper.assertTrue(Cutting.all(level).stream().anyMatch(recipe -> recipe.getIngredients().get(0).test(new ItemStack(fd("rice_panicle")))),
                "rice for the pot is cooking");
        Board setup = board(helper);
        setup.carry(new ItemStack(Items.OAK_LOG));
        setup.place();
        helper.assertTrue(setup.onBoard().isEmpty(), "the station puts no log down");
        helper.succeed();
    }

    /** A knife carved into the board is Farmer's Delight's: its own use gives it back, the station does not open on it. */
    public static void aCarvedKnifeStaysFarmersDelights(GameTestHelper helper) {
        Board setup = board(helper);
        BlockPos pos = setup.station.pos();
        BlockState state = helper.getLevel().getBlockState(pos);
        helper.assertTrue(CuttingBoardStation.opens(setup.cook, pos, state), "a bare board opens");
        setup.board.carveToolOnBoard(new ItemStack(fd("iron_knife")));
        helper.assertFalse(CuttingBoardStation.opens(setup.cook, pos, state), "a board with a knife carved in does not");
        helper.succeed();
    }

    /** A chop the board does not take is answered, so the cook's client can close the cut it drew at once. */
    public static void anIgnoredChopIsAnswered(GameTestHelper helper) {
        Board setup = board(helper);
        setup.carry(new ItemStack(fd("cabbage"), 2));
        setup.place();
        setup.station.effects.clear();
        setup.chop(1);
        helper.assertTrue(setup.station.effects.contains(CuttingBoardStation.EFFECT_REFUSED), "the second mark before the first: refused");
        setup.station.effects.clear();
        setup.chop(0);
        setup.chop(1);
        helper.assertTrue(setup.station.effects.contains(CuttingBoardStation.EFFECT_REFUSED), "two chops in one tick: the second refused");
        helper.assertTrue(setup.onBoard().getCount() == 2, "and nothing was cut");
        helper.succeed();
    }

    /** One cook leaving does not take the marks from another one still cutting; the last one leaving does. */
    public static void anotherCookKeepsTheMarks(GameTestHelper helper) {
        Board setup = board(helper);
        Player other = helper.makeMockPlayer();
        setup.station.users.add(other);
        setup.carry(new ItemStack(fd("cabbage"), 2));
        setup.place();
        setup.chop(0);
        setup.logic.onClose(setup.station, setup.cook);
        helper.assertTrue(setup.station.state().data().getInt(CuttingBoardStation.MARKS) == 1, "the first mark stays cut for the other cook");
        setup.station.users.clear();
        setup.logic.onClose(setup.station, other);
        helper.assertTrue(setup.station.state().data().getInt(CuttingBoardStation.MARKS) == 0, "and starts over when the board is left");
        helper.succeed();
    }

    public static void noKnifeNoCut(GameTestHelper helper) {
        Board setup = board(helper);
        setup.cook.getInventory().setItem(5, new ItemStack(fd("iron_knife")));
        setup.carry(new ItemStack(fd("cabbage")));
        setup.place();
        setup.carry(new ItemStack(Items.STICK));
        setup.chop(0);
        setup.carry(ItemStack.EMPTY);
        helper.assertTrue(setup.onBoard().is(fd("cabbage")), "a knife in the inventory does not cut: it must be in the hand");
        setup.act(CuttingBoardStation.ACTION_TAKE, new CompoundTag());
        helper.assertTrue(setup.onBoard().isEmpty(), "and can be taken back");
        helper.assertTrue(setup.cook.getInventory().countItem(fd("cabbage")) == 1, "into the inventory");
        helper.succeed();
    }

    /** The board's station as the library would hold it, without a player at it. */
    private static final class Station implements StationContext {
        private final Level level;
        private final BlockPos pos;
        private final StationState state = new StationState(CuttingBoardStation.SCHEMA);
        private final List<Player> users = new ArrayList<>();
        private final List<ResourceLocation> effects = new ArrayList<>();

        Station(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        @Override
        public StationType type() {
            return CuttingBoardStation.TYPE;
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
        public void effect(ResourceLocation effect, String node, CompoundTag data) {
            effects.add(effect);
        }
    }
}
