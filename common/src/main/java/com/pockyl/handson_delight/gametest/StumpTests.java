package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.BoardLogic;
import com.pockyl.handson_delight.station.ChoppingBlockStation;
import com.pockyl.handson_delight.station.CuttingBoardStation;
import com.pockyl.handson_delight.station.KitchenStump;

/**
 * Sable Trees' chopping block, a tree's stump, at the station (-Paddons): food cut along its marks through the stump's own cut, its
 * count of food cuts and its limit, a log split into planks with an axe. Each test passes at once where Sable Trees is not there; one
 * checks that the station then matches nothing.
 */
public final class StumpTests {
    private static final String SABLE_TREES = "sabletrees";
    /** Sable Trees' stump takes no more food after this many cuts of food. */
    private static final int MOST_FOOD_CUTS = 32;

    private StumpTests() {
    }

    @SuppressWarnings("removal")
    private static Item fd(String path) {
        return BuiltInRegistries.ITEM.get(FarmersDelightContent.id(path));
    }

    private record Stump(PotTests.TestStation station, BoardLogic logic, KitchenStump stump, ServerPlayer cook) {
        void carry(ItemStack stack) {
            cook.inventoryMenu.setCarried(stack);
        }

        void act(String action, CompoundTag data) {
            logic.onCustomAction(station, cook, action, data);
        }

        void place() {
            act(CuttingBoardStation.ACTION_PLACE, new CompoundTag());
        }

        /** A click with the tool on the cursor on the next mark. */
        void chop(ItemStack tool) {
            carry(tool);
            CompoundTag at = new CompoundTag();
            at.putInt(CuttingBoardStation.MARK, 0);
            at.putFloat(CuttingBoardStation.AT, 8.0F);
            act(CuttingBoardStation.ACTION_CHOP, at);
        }

        int count(Item item) {
            return cook.getInventory().countItem(item);
        }
    }

    /** A stump with the logic the station would make for it, and a cook who uses things up; null without Sable Trees. */
    private static Stump stump(GameTestHelper helper, BlockPos at) {
        if (!Platform.INSTANCE.isLoaded(SABLE_TREES)) {
            helper.succeed();
            return null;
        }
        @SuppressWarnings("removal")
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(SABLE_TREES, "chopping_block"));
        helper.assertTrue(ChoppingBlockStation.TYPE.matches(block.defaultBlockState()), "the chopping block opens as the stump's station");
        helper.setBlock(at, block);
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, ChoppingBlockStation.TYPE);
        KitchenStump stump = KitchenStump.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(stump != null, "Sable Trees' chopping block is a kitchen stump");
        // A server player: Sable Trees awards Farmer's Delight's board advancement to one.
        ServerPlayer cook = TestPlayers.serverPlayer(helper, "cook");
        cook.setGameMode(GameType.SURVIVAL);
        return new Stump(station, new BoardLogic(station), stump, cook);
    }

    /** Nothing lies on the ground round the stump: what it made went to the cook. */
    private static void nothingDropped(GameTestHelper helper, BlockPos at) {
        BlockPos pos = helper.absolutePos(at);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0)).isEmpty(),
                "nothing is dropped at the stump");
    }

    /**
     * Cabbages laid on the stump are cut along their mark with a knife, as on the board: sixteen at one cut, through the stump's own cut
     * (each counted as food cut there), the leaves to the cook, nothing on the ground. A dish is never put together on it.
     */
    public static void stumpCutsFoodLikeTheBoard(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        Stump setup = stump(helper, at);
        if (setup == null) {
            return;
        }
        setup.carry(new ItemStack(fd("cabbage"), 20));
        setup.place();
        helper.assertTrue(setup.stump().handson$stored().getCount() == 20, "the whole stack lies on the stump");
        setup.chop(new ItemStack(fd("iron_knife")));
        helper.assertTrue(setup.stump().handson$stored().getCount() == 4, "one cut goes through sixteen of them");
        helper.assertTrue(setup.count(fd("cabbage_leaf")) == 32, "the leaves go to the cook");
        helper.assertTrue(setup.stump().handson$foodCuts() == 16, "the stump counts every cabbage cut on it");
        nothingDropped(helper, at);
        setup.carry(new ItemStack(Items.BREAD));
        setup.place();
        helper.assertTrue(setup.cook().inventoryMenu.getCarried().is(Items.BREAD) && BoardLogic.layers(setup.station().state()).isEmpty(),
                "no dish is started on the stump");
        helper.succeed();
    }

    /**
     * After 32 food cuts the stump is too dirty for food, as Sable Trees has it: a cut stops there with the rest still lying, the next
     * is refused, and food is no longer laid on it. What lies there can still be taken back.
     */
    public static void stumpTakesNoFoodAfterThirtyTwoCuts(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        Stump setup = stump(helper, at);
        if (setup == null) {
            return;
        }
        ItemStack knife = new ItemStack(fd("iron_knife"));
        setup.carry(new ItemStack(fd("cabbage"), 24));
        setup.place();
        setup.chop(knife);
        helper.runAfterDelay(4, () -> setup.chop(knife));
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(setup.stump().handson$foodCuts() == 24 && setup.stump().handson$stored().isEmpty(),
                    "twenty-four cabbages are cut in two cuts");
            setup.carry(new ItemStack(fd("cabbage"), 16));
            setup.place();
            setup.chop(knife);
        });
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(setup.stump().handson$foodCuts() == MOST_FOOD_CUTS && setup.stump().handson$isDirty(),
                    "the cut stops at thirty-two: the stump is too dirty");
            helper.assertTrue(setup.stump().handson$stored().getCount() == 8, "the rest still lies there");
            setup.chop(knife);
        });
        helper.runAfterDelay(16, () -> {
            helper.assertTrue(setup.stump().handson$stored().getCount() == 8, "no more food is cut");
            ItemStack more = new ItemStack(fd("cabbage"), 3);
            setup.carry(more);
            setup.place();
            helper.assertTrue(setup.cook().inventoryMenu.getCarried().getCount() == 3, "no more food is laid on it");
            setup.carry(ItemStack.EMPTY);
            setup.act(CuttingBoardStation.ACTION_TAKE, new CompoundTag());
            helper.assertTrue(setup.stump().handson$stored().isEmpty() && setup.count(fd("cabbage")) == 8, "the rest is taken back");
            helper.succeed();
        });
    }

    /**
     * A log lies on the stump one at a time; an axe from the inventory splits it in three blows on its marks, the last through Sable
     * Trees' own stroke: its planks and half as many again (six oak planks) go to the cook, and the axe is worn once.
     */
    public static void stumpSplitsALogWithTheAxe(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        Stump setup = stump(helper, at);
        if (setup == null) {
            return;
        }
        // A shovel on a stump that still has its roots digs them out, as Sable Trees has it: the station does not open for it.
        BlockState rooted = helper.getLevel().getBlockState(setup.station().pos());
        setup.cook().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
        helper.assertFalse(ChoppingBlockStation.opens(setup.cook(), setup.station().pos(), rooted), "a shovel digs the roots out");
        setup.cook().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(ChoppingBlockStation.opens(setup.cook(), setup.station().pos(), rooted), "an empty hand opens the station");
        setup.carry(new ItemStack(Items.OAK_LOG, 3));
        setup.place();
        helper.assertTrue(setup.stump().handson$stored().is(Items.OAK_LOG) && setup.stump().handson$stored().getCount() == 1,
                "one log stands on the stump");
        helper.assertTrue(setup.cook().inventoryMenu.getCarried().getCount() == 2, "the rest stays in the hand");
        setup.carry(ItemStack.EMPTY);
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        setup.cook().getInventory().setItem(5, axe);
        helper.assertTrue(setup.logic().plan().marks() == 3, "a log is split along three marks");
        blow(setup, 0);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(setup.stump().handson$stored().is(Items.OAK_LOG) && setup.count(Items.OAK_PLANKS) == 0,
                    "the first blow only cuts the log");
            // A blow on a mark already cut, not the next, is refused.
            blow(setup, 0);
        });
        helper.runAfterDelay(8, () -> blow(setup, 1));
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(setup.stump().handson$stored().is(Items.OAK_LOG) && setup.count(Items.OAK_PLANKS) == 0,
                    "the second blow only cuts the log");
            blow(setup, 2);
        });
        helper.runAfterDelay(16, () -> {
            helper.assertTrue(setup.stump().handson$stored().isEmpty(), "the third blow splits the log");
            helper.assertTrue(setup.count(Items.OAK_PLANKS) == 6, "six planks go to the cook, got " + setup.count(Items.OAK_PLANKS));
            helper.assertTrue(setup.cook().getInventory().getItem(5).getDamageValue() == 1, "the axe is worn once, by the split");
            helper.assertTrue(setup.stump().handson$foodCuts() == 0, "a log is no food");
            nothingDropped(helper, at);
            helper.succeed();
        });
    }

    /** A blow of the axe offered from the inventory on mark {@code mark}. */
    private static void blow(Stump setup, int mark) {
        CompoundTag at = new CompoundTag();
        at.putInt(CuttingBoardStation.MARK, mark);
        at.putFloat(CuttingBoardStation.AT, 8.0F);
        setup.act(CuttingBoardStation.ACTION_CHOP, at);
    }

    /** Without Sable Trees the stump's station matches no block, and its tag is empty: nothing of it is loaded. */
    public static void stumpStationIsQuietWithoutSableTrees(GameTestHelper helper) {
        if (Platform.INSTANCE.isLoaded(SABLE_TREES)) {
            helper.succeed();
            return;
        }
        helper.assertTrue(BuiltInRegistries.BLOCK.getTag(ChoppingBlockStation.STUMPS).map(tag -> tag.size() == 0).orElse(true),
                "no block is a stump");
        helper.assertFalse(ChoppingBlockStation.TYPE.matches(Blocks.OAK_LOG.defaultBlockState()), "a log is no stump");
        helper.assertFalse(ChoppingBlockStation.TYPE.matches(FarmersDelightContent.CUTTING_BOARD.get().defaultBlockState()),
                "the board stays the board's station");
        helper.succeed();
    }
}
