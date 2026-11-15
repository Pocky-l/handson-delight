package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.CuisineBoardStation;
import com.pockyl.handson_delight.station.CuisineLogic;
import com.pockyl.handson_delight.station.KitchenCuisine;

import java.util.ArrayList;
import java.util.List;

/**
 * Youkai's Feasts' cuisine board at the station: nigiri shaped by hand and topped, a maki laid up from the book, rolled and cut with
 * a knife, the last step taken back, several dishes from one pick. Every step goes through the board's own code. Each test passes at
 * once where Youkai's Feasts is not there.
 */
public final class YoukaiBoardTests {
    private YoukaiBoardTests() {
    }

    @SuppressWarnings("removal")
    private static Item feasts(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(KitchenCuisine.MOD_ID, path));
    }

    @SuppressWarnings("removal")
    private static Item farmers(String path) {
        return BuiltInRegistries.ITEM.get(FarmersDelightContent.id(path));
    }

    private record Setup(PotTests.TestStation station, CuisineLogic logic, KitchenCuisine board) {
    }

    /** A board with the logic the station would make for it; null without Youkai's Feasts. */
    private static Setup board(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenCuisine.MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, CuisineBoardStation.BOARD.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, CuisineBoardStation.TYPE);
        KitchenCuisine board = KitchenCuisine.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(board != null, "Youkai's Feasts' cuisine board is a kitchen board");
        return new Setup(station, new CuisineLogic(station), board);
    }

    /** A player who uses things up, as one in survival does: the game test's mock player builds as in creative. */
    private static Player cook(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = false;
        return player;
    }

    private static void act(Setup setup, Player player, String action, boolean all) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(CuisineBoardStation.ALL, all);
        setup.logic.onCustomAction(setup.station, player, action, data);
    }

    /** A click with the knife offered from the inventory on mark {@code mark}. */
    private static void cutAt(Setup setup, Player player, int mark) {
        CompoundTag data = new CompoundTag();
        data.putInt(CuisineBoardStation.MARK, mark);
        setup.logic.onCustomAction(setup.station, player, CuisineBoardStation.ACTION_CUT, data);
    }

    private static void fill(Setup setup, Player player, String dish, boolean all) {
        CompoundTag data = new CompoundTag();
        data.putString(CuisineBoardStation.DISH, dish);
        data.putBoolean(CuisineBoardStation.ALL, all);
        setup.logic.onCustomAction(setup.station, player, CuisineBoardStation.ACTION_FILL, data);
    }

    /** Its recipes are read with every step from the empty board, the steps done by hand among them, in order. */
    public static void youkaiBoardReadsItsRecipes(GameTestHelper helper) {
        Setup setup = board(helper);
        if (setup == null) {
            return;
        }
        KitchenCuisine.Dish salmon = setup.board.handson$dishes().stream()
                .filter(dish -> dish.id().toString().equals("youkaisfeasts:cuisine_ordered/salmon_roll")).findFirst().orElse(null);
        helper.assertTrue(salmon != null, "the salmon nigiri is among " + setup.board.handson$dishes().size() + " recipes");
        helper.assertTrue(salmon.steps().size() == 3 && salmon.steps().get(0).test(new ItemStack(farmers("cooked_rice")))
                && salmon.steps().get(1).isEmpty() && salmon.steps().get(2).test(new ItemStack(Items.SALMON)),
                "rice, shaped by hand, then salmon; got " + salmon.steps().size() + " steps");
        KitchenCuisine.Dish kappa = setup.board.handson$dishes().stream()
                .filter(dish -> dish.id().toString().equals("youkaisfeasts:cuisine_ordered/kappa_maki")).findFirst().orElse(null);
        helper.assertTrue(kappa != null && kappa.steps().get(0).test(new ItemStack(Items.DRIED_KELP)) && kappa.handSteps() == 1
                && kappa.steps().get(kappa.steps().size() - 1).isEmpty(), "a maki starts from kelp and is rolled up last");
        // 14 ordered, 7 mixed, 3 bamboo tubes in its 0.5.2.
        helper.assertTrue(setup.board.handson$dishes().size() >= 24, "every board recipe, got " + setup.board.handson$dishes().size());
        helper.succeed();
    }

    /**
     * Nigiri by hand: cooked rice from the cursor (its bowl back to the cook), shaped by an empty hand, salmon on top; the two salmon
     * rolls go to the cook. A knife cuts nothing there, food not of the dish is refused.
     */
    public static void youkaiNigiriIsShapedAndTopped(GameTestHelper helper) {
        Setup setup = board(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(helper);
        player.inventoryMenu.setCarried(new ItemStack(farmers("cooked_rice"), 2));
        act(setup, player, CuisineBoardStation.ACTION_PUT, false);
        helper.assertTrue(setup.board.handson$contents().size() == 1 && player.inventoryMenu.getCarried().getCount() == 1,
                "one cooked rice went on, one stays in the hand");
        helper.assertTrue(player.getInventory().countItem(Items.BOWL) == 1, "its bowl went back to the cook");
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(setup.board.handson$byHand(), "rice waits to be shaped by hand");
        act(setup, player, CuisineBoardStation.ACTION_SHAPE, false);
        helper.assertTrue(setup.board.handson$contents().size() == 2 && setup.board.handson$contents().get(1).isEmpty(),
                "shaped: a step done by hand");
        player.inventoryMenu.setCarried(new ItemStack(Items.CARROT));
        act(setup, player, CuisineBoardStation.ACTION_PUT, false);
        helper.assertTrue(setup.board.handson$contents().size() == 2 && player.inventoryMenu.getCarried().is(Items.CARROT),
                "a carrot is no nigiri topping: refused, it stays in the hand");
        player.inventoryMenu.setCarried(new ItemStack(Items.SALMON));
        act(setup, player, CuisineBoardStation.ACTION_PUT, false);
        ItemStack finished = setup.board.handson$finished();
        helper.assertTrue(finished.is(farmers("salmon_roll")) && finished.getCount() == 2, "two salmon rolls lie done, got " + finished);
        helper.assertTrue(setup.board.handson$modelsOf(setup.board.handson$contents()) != null
                && !setup.board.handson$models().isEmpty(), "the board draws what lies on it");
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        act(setup, player, CuisineBoardStation.ACTION_TAKE, false);
        helper.assertTrue(player.getInventory().countItem(farmers("salmon_roll")) == 2, "the two salmon rolls went to the cook");
        helper.assertTrue(setup.board.handson$contents().isEmpty(), "the board is empty again");
        helper.succeed();
    }

    /**
     * Kappa maki from the book: kelp, rice, the soy sauce poured (its bottle back) and the cucumber are laid from the inventory in
     * order, the sheet is rolled up; then the knife from the inventory cuts it at its marks, one after the other, into its three
     * pieces for the cook, and wears once.
     */
    public static void youkaiMakiFromTheBookIsRolledAndCut(GameTestHelper helper) {
        Setup setup = board(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(helper);
        player.getInventory().setItem(0, new ItemStack(Items.DRIED_KELP, 3));
        player.getInventory().setItem(1, new ItemStack(farmers("cooked_rice"), 3));
        player.getInventory().setItem(2, new ItemStack(feasts("soy_sauce_bottle"), 3));
        player.getInventory().setItem(3, new ItemStack(feasts("cucumber_slice"), 3));
        player.getInventory().setItem(4, new ItemStack(farmers("iron_knife")));
        fill(setup, player, "youkaisfeasts:cuisine_ordered/kappa_maki", false);
        ItemStack finished = setup.board.handson$finished();
        helper.assertTrue(finished.is(feasts("kappa_maki")), "the book laid up and rolled a kappa maki, got " + finished
                + " on " + setup.board.handson$contents());
        Inventory inventory = player.getInventory();
        helper.assertTrue(inventory.countItem(Items.DRIED_KELP) == 2 && inventory.countItem(farmers("cooked_rice")) == 2
                && inventory.countItem(feasts("soy_sauce_bottle")) == 2 && inventory.countItem(feasts("cucumber_slice")) == 2,
                "one of each from the inventory");
        helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 1 && player.getInventory().countItem(Items.BOWL) == 1,
                "the sauce's bottle and the rice's bowl went back to the cook");
        // Two marks, as the board cuts a roll into three: the first is only a cut, the second cuts it through; a click on the cut
        // mark again does nothing.
        cutAt(setup, player, 0);
        helper.assertTrue(setup.station.state().data().getInt(CuisineBoardStation.CUTS) == 1
                && setup.board.handson$finished().is(feasts("kappa_maki")), "the first mark is cut, the roll still lies whole");
        cutAt(setup, player, 0);
        helper.assertTrue(setup.station.state().data().getInt(CuisineBoardStation.CUTS) == 1, "a mark is cut once");
        cutAt(setup, player, 1);
        helper.assertTrue(player.getInventory().countItem(feasts("kappa_maki_slice")) == 3, "cut into three pieces for the cook, got "
                + player.getInventory().countItem(feasts("kappa_maki_slice")));
        helper.assertTrue(setup.board.handson$contents().isEmpty(), "the board is empty after the cut");
        helper.assertTrue(player.getInventory().getItem(4).getDamageValue() == 1, "the knife wore by one");
        helper.succeed();
    }

    /**
     * The last step comes back: a hand step undone gives nothing; rice goes back into a bowl of the cook's, and without a bowl it
     * stays on the board.
     */
    public static void youkaiBoardTakesTheLastStepBack(GameTestHelper helper) {
        Setup setup = board(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(helper);
        player.inventoryMenu.setCarried(new ItemStack(farmers("cooked_rice")));
        act(setup, player, CuisineBoardStation.ACTION_PUT, false);
        act(setup, player, CuisineBoardStation.ACTION_SHAPE, false);
        act(setup, player, CuisineBoardStation.ACTION_BACK, false);
        helper.assertTrue(setup.board.handson$contents().size() == 1 && setup.board.handson$byHand(),
                "unshaped: the rice lies there to be shaped again");
        player.getInventory().clearContent();
        act(setup, player, CuisineBoardStation.ACTION_BACK, false);
        helper.assertTrue(setup.board.handson$contents().size() == 1, "without a bowl the rice stays");
        player.getInventory().setItem(0, new ItemStack(Items.BOWL));
        act(setup, player, CuisineBoardStation.ACTION_BACK, false);
        helper.assertTrue(setup.board.handson$contents().isEmpty() && player.getInventory().countItem(farmers("cooked_rice")) == 1
                && player.getInventory().countItem(Items.BOWL) == 0, "the rice went back into the cook's bowl");
        helper.succeed();
    }

    /**
     * Every step of every dish taken back leaves on the board what lay there before it, and the board can draw that: the scene
     * redraws it in place. Most steps draw the whole thing anew (kelp becomes an open sheet under the rice), so the models of what
     * stays are not always among those shown; a half-made roll used to lift off whole and come back.
     */
    public static void youkaiBoardTakesBackOnlyTheLastStep(GameTestHelper helper) {
        if (board(helper) == null) {
            return;
        }
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        int redrawn = 0;
        for (KitchenCuisine.Dish dish : board(helper).board.handson$dishes()) {
            helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
            Setup setup = board(helper);
            Player player = helper.makeMockPlayer(GameType.CREATIVE);
            for (Ingredient step : dish.steps()) {
                List<ItemStack> before = List.copyOf(setup.board.handson$contents());
                if (step.isEmpty()) {
                    act(setup, player, CuisineBoardStation.ACTION_SHAPE, false);
                } else if (step.getItems().length > 0) {
                    player.inventoryMenu.setCarried(step.getItems()[0].copyWithCount(16));
                    act(setup, player, CuisineBoardStation.ACTION_PUT, false);
                }
                List<ItemStack> after = List.copyOf(setup.board.handson$contents());
                if (after.size() != before.size() + 1 || before.isEmpty()) {
                    continue;
                }
                checked++;
                List<ResourceLocation> stays = setup.board.handson$modelsOf(before);
                List<ResourceLocation> shown = setup.board.handson$modelsOf(after);
                if (stays == null || stays.isEmpty() || shown == null) {
                    wrong.add(dish.id().getPath() + " " + before + " -> " + after);
                } else if (!shown.containsAll(stays)) {
                    redrawn++;
                }
                act(setup, player, CuisineBoardStation.ACTION_BACK, false);
                if (!setup.board.handson$contents().equals(before) && !sameItems(setup.board.handson$contents(), before)) {
                    wrong.add(dish.id().getPath() + " back from " + after + " left " + setup.board.handson$contents());
                    break;
                }
                act(setup, player, step.isEmpty() ? CuisineBoardStation.ACTION_SHAPE : CuisineBoardStation.ACTION_PUT, false);
            }
        }
        helper.assertTrue(checked > 40 && redrawn > 0, "steps checked " + checked + ", redrawn in place " + redrawn);
        helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + checked + " steps: " + String.join("; ", wrong));
        helper.succeed();
    }

    private static boolean sameItems(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.isSameItem(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * A pick with shift makes as many as the inventory has whole sets for: each dish but the last goes to the cook, the last stays on
     * the board. A dish lying done is given to the cook before a pick starts the next.
     */
    public static void youkaiBookMakesSeveralDishes(GameTestHelper helper) {
        Setup setup = board(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(helper);
        player.getInventory().setItem(0, new ItemStack(farmers("cooked_rice"), 3));
        player.getInventory().setItem(1, new ItemStack(Items.SALMON, 3));
        fill(setup, player, "youkaisfeasts:cuisine_ordered/salmon_roll", true);
        helper.assertTrue(player.getInventory().countItem(farmers("salmon_roll")) == 4, "two dishes of two went to the cook, got "
                + player.getInventory().countItem(farmers("salmon_roll")));
        helper.assertTrue(setup.board.handson$finished().is(farmers("salmon_roll")), "the third lies on the board");
        helper.assertTrue(player.getInventory().countItem(Items.SALMON) == 0, "all three salmon used");
        player.getInventory().setItem(0, new ItemStack(farmers("cooked_rice")));
        player.getInventory().setItem(1, new ItemStack(Items.SALMON));
        fill(setup, player, "youkaisfeasts:cuisine_ordered/salmon_roll", false);
        helper.assertTrue(player.getInventory().countItem(farmers("salmon_roll")) == 6, "the dish lying there went to the cook first");
        helper.assertTrue(setup.board.handson$finished().is(farmers("salmon_roll")), "and the next was made");
        helper.succeed();
    }
}
