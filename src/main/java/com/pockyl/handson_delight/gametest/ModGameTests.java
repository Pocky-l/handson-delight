package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.common.registry.ModItems;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.station.CookingPotStation;
import com.pockyl.handson_delight.station.PotLogic;

import java.util.Collection;
import java.util.List;

/** The station's rules against a real pot of Farmer's Delight. */
@GameTestHolder(HandsOnDelight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModGameTests {
    private ModGameTests() {
    }

    /** A pot on a lit campfire, with the logic the station would make for it. */
    private record Setup(TestStation station, PotLogic logic, CookingPotBlockEntity pot) {
    }

    private static Setup pot(GameTestHelper helper) {
        BlockPos fire = new BlockPos(1, 1, 1);
        helper.setBlock(fire, Blocks.CAMPFIRE);
        helper.setBlock(fire.above(), ModBlocks.COOKING_POT.get());
        BlockPos pos = helper.absolutePos(fire.above());
        TestStation station = new TestStation(helper.getLevel(), pos);
        CookingPotBlockEntity pot = (CookingPotBlockEntity) helper.getLevel().getBlockEntity(pos);
        return new Setup(station, new PotLogic(station), pot);
    }

    @GameTest(template = "empty")
    public static void slotsAreThePotsOwn(GameTestHelper helper) {
        Setup setup = pot(helper);
        SlotRef first = SlotRef.of(CookingPotStation.INGREDIENTS, 0);
        setup.station.state().setItem(first, new ItemStack(Items.CARROT, 3));
        helper.assertTrue(setup.pot.getInventory().getStackInSlot(0).is(Items.CARROT), "the slot writes into the pot");
        setup.pot.getInventory().setStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT, new ItemStack(Items.BOWL, 2));
        helper.assertTrue(setup.station.state().getItem(CookingPotStation.SERVED_SLOT).is(Items.BOWL), "and reads from it");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theMealStaysInThePot(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        helper.assertFalse(setup.logic.mayTake(setup.station, player, CookingPotStation.MEAL_SLOT), "the meal is only served");
        helper.assertTrue(setup.logic.mayTake(setup.station, player, CookingPotStation.SERVED_SLOT), "servings are taken");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aBowlClickedOnTheSoupFills(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        player.inventoryMenu.setCarried(new ItemStack(Items.BOWL, 3));
        // No meal: nothing to scoop, the bowls stay on the cursor.
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_SCOOP, new CompoundTag());
        helper.assertTrue(player.inventoryMenu.getCarried().getCount() == 3, "nothing happens without a meal");

        setup.pot.getInventory().setStackInSlot(CookingPotBlockEntity.MEAL_DISPLAY_SLOT, new ItemStack(ModItems.BEEF_STEW.get(), 2));
        // What a finished recipe leaves in the pot: it knows its container only from the recipe or from its save.
        CompoundTag saved = setup.pot.saveWithoutMetadata();
        saved.put("Container", new ItemStack(Items.BOWL).save(new CompoundTag()));
        setup.pot.load(saved);
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(CookingPotStation.INGREDIENTS, 0), new ItemStack(Items.BOWL)),
                "a bowl for the soup does not go in");
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_SCOOP, new CompoundTag());
        helper.assertTrue(player.inventoryMenu.getCarried().getCount() == 2, "one bowl fills");
        helper.assertTrue(player.getInventory().contains(new ItemStack(ModItems.BEEF_STEW.get())), "and goes to the inventory");
        helper.assertTrue(setup.pot.getMeal().getCount() == 1, "one serving left");

        player.inventoryMenu.setCarried(new ItemStack(Items.BOWL));
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_SCOOP, new CompoundTag());
        helper.assertTrue(player.inventoryMenu.getCarried().is(ModItems.BEEF_STEW.get()), "the last bowl in the hand stays in it, full");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aStackIsSharedOutForARecipeOfTwoAlike(GameTestHelper helper) {
        Setup setup = pot(helper);
        SlotRef first = SlotRef.of(CookingPotStation.INGREDIENTS, 0);
        setup.pot.getInventory().setStackInSlot(0, new ItemStack(ModItems.TOMATO.get(), 5));
        setup.logic.onSlotChanged(setup.station, first);
        setup.logic.tick(setup.station);
        helper.assertTrue(setup.pot.getInventory().getStackInSlot(0).getCount() == 3, "tomatoes are shared out");
        helper.assertTrue(setup.pot.getInventory().getStackInSlot(1).is(ModItems.TOMATO.get()), "over two slots, as tomato sauce wants");
        helper.assertTrue(setup.pot.getInventory().getStackInSlot(1).getCount() == 2, "and none get lost");

        setup.pot.getInventory().setStackInSlot(0, new ItemStack(Items.CARROT, 6));
        setup.pot.getInventory().setStackInSlot(1, ItemStack.EMPTY);
        setup.logic.onSlotChanged(setup.station, first);
        setup.logic.tick(setup.station);
        helper.assertTrue(setup.pot.getInventory().getStackInSlot(0).getCount() == 6, "what makes nothing stays as it is");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dishesStayOutOfTheWater(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        SlotRef first = SlotRef.of(CookingPotStation.INGREDIENTS, 0);
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.BOWL)), "no bowl in an empty pot");
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.CARROT)), "food goes in");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftFillsTheWholeStack(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        setup.pot.getInventory().setStackInSlot(CookingPotBlockEntity.MEAL_DISPLAY_SLOT, new ItemStack(ModItems.BEEF_STEW.get(), 2));
        CompoundTag saved = setup.pot.saveWithoutMetadata();
        saved.put("Container", new ItemStack(Items.BOWL).save(new CompoundTag()));
        setup.pot.load(saved);
        player.inventoryMenu.setCarried(new ItemStack(Items.BOWL, 5));
        CompoundTag all = new CompoundTag();
        all.putBoolean(CookingPotStation.SCOOP_ALL, true);
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_SCOOP, all);
        helper.assertTrue(setup.pot.getMeal().isEmpty(), "the soup is gone");
        helper.assertTrue(player.inventoryMenu.getCarried().getCount() == 3, "three bowls are left");
        helper.assertTrue(player.getInventory().countItem(ModItems.BEEF_STEW.get()) == 2, "two are full");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void manyServingsCookFaster(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        setup.pot.getInventory().setStackInSlot(0, new ItemStack(Items.BEEF, 20));
        setup.pot.getInventory().setStackInSlot(1, new ItemStack(Items.CARROT, 20));
        setup.pot.getInventory().setStackInSlot(2, new ItemStack(Items.POTATO, 20));
        setup.logic.tick(setup.station);
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_STIR, new CompoundTag());
        helper.runAfterDelay(30, () -> setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_STIR, new CompoundTag()));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(setup.pot.getMeal().getCount() == 1, "twenty servings go about four times as fast: one in about two seconds, not ten");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nothingCooksWithoutStirring(GameTestHelper helper) {
        Setup setup = pot(helper);
        Player player = helper.makeMockPlayer();
        setup.pot.getInventory().setStackInSlot(0, new ItemStack(Items.BEEF));
        setup.pot.getInventory().setStackInSlot(1, new ItemStack(Items.CARROT));
        setup.pot.getInventory().setStackInSlot(2, new ItemStack(Items.POTATO));
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

    @GameTest(template = "empty")
    public static void theStationReadsHeat(GameTestHelper helper) {
        Setup setup = pot(helper);
        setup.logic.tick(setup.station);
        helper.assertTrue(setup.station.state().getControl(CookingPotStation.HEATED) > 0.5F, "a lit campfire heats the pot");
        int water = setup.station.state().getTank(CookingPotStation.BROTH).amount();
        helper.assertTrue(water > 0, "there is water in an empty pot");
        setup.pot.getInventory().setStackInSlot(0, new ItemStack(Items.CARROT));
        setup.logic.tick(setup.station);
        helper.assertTrue(setup.station.state().getTank(CookingPotStation.BROTH).amount() > water, "ingredients raise it");
        helper.succeed();
    }

    private static final class TestStation implements StationContext {
        private final Level level;
        private final BlockPos pos;
        private final StationState state = new StationState(CookingPotStation.SCHEMA);

        TestStation(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        @Override
        public StationType type() {
            return CookingPotStation.TYPE;
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
            return List.of();
        }

        @Override
        public void effect(ResourceLocation effect, String node, CompoundTag data) {
        }
    }
}
