package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.KettleLogic;
import com.pockyl.handson_delight.station.KettleStation;
import com.pockyl.handson_delight.station.KitchenKettle;

/**
 * Farmer's Respite's kettle at the station: its lid off and on as the block's own state, water poured in at the opening, green tea
 * brewed over a campfire as the kettle brews it, drinks poured from the spout. Each test passes at once where Farmer's Respite is not
 * there.
 */
public final class KettleTests {
    private static final String GREEN_TEA = "farmersrespite:brewing/green_tea";

    private KettleTests() {
    }

    @SuppressWarnings("removal")
    private static Item respite(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(KettleStation.MOD_ID, path));
    }

    private record Setup(PotTests.TestStation station, KettleLogic logic, KitchenKettle kettle) {
    }

    /** A kettle over a lit campfire, with the logic the station would make for it; null without Farmer's Respite. */
    private static Setup kettle(GameTestHelper helper, boolean heated) {
        if (!Platform.INSTANCE.isLoaded(KettleStation.MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), heated ? Blocks.CAMPFIRE : Blocks.STONE);
        helper.setBlock(at, KettleStation.KETTLE.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, KettleStation.TYPE);
        KitchenKettle kettle = KitchenKettle.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(kettle != null, "the kettle is a kitchen kettle");
        return new Setup(station, new KettleLogic(station), kettle);
    }

    private static CompoundTag at(String where, boolean all) {
        CompoundTag data = new CompoundTag();
        data.putString(KettleStation.AT, where);
        data.putBoolean(KettleStation.ALL, all);
        return data;
    }

    private static void lid(Setup setup, Player player, boolean open) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(KettleStation.OPEN, open);
        setup.logic.onCustomAction(setup.station, player, KettleStation.ACTION_LID, data);
    }

    private static void pourWater(Setup setup, Player player) {
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        setup.logic.onCustomAction(setup.station, player, KettleStation.ACTION_USE, at(KettleStation.MOUTH, false));
    }

    /**
     * The lid is the kettle block's own: on, nothing is poured or dropped in at the top; taken off at the station, the block stands
     * open, and water and leaves go in.
     */
    public static void theLidIsTheBlocksOwn(GameTestHelper helper) {
        Setup setup = kettle(helper, false);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        helper.assertFalse(KettleStation.isOpen(helper.getLevel().getBlockState(setup.station.pos())), "a kettle is placed with its lid on");
        pourWater(setup, player);
        helper.assertTrue(setup.kettle.handson$amount() == 0 && player.inventoryMenu.getCarried().is(Items.WATER_BUCKET),
                "nothing is poured in through the lid");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(KettleStation.INGREDIENTS, 0),
                new ItemStack(respite("green_tea_leaves"))), "nor dropped in");
        lid(setup, player, true);
        helper.assertTrue(KettleStation.isOpen(helper.getLevel().getBlockState(setup.station.pos())), "the block stands open");
        setup.logic.onCustomAction(setup.station, player, KettleStation.ACTION_USE, at(KettleStation.MOUTH, false));
        helper.assertTrue(setup.kettle.handson$fluid() == Fluids.WATER && setup.kettle.handson$amount() == 1000,
                "with the lid off the water goes in, got " + setup.kettle.handson$amount());
        helper.assertTrue(player.inventoryMenu.getCarried().is(Items.BUCKET), "the empty bucket stays in the hand");
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, SlotRef.of(KettleStation.INGREDIENTS, 0),
                new ItemStack(respite("green_tea_leaves"))), "leaves go in");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(KettleStation.INGREDIENTS, 0),
                new ItemStack(Items.GLASS_BOTTLE)), "a bottle is filled, not dropped in");
        setup.logic.tick(setup.station);
        helper.assertTrue(setup.station.state().getControl(KettleStation.LID) > 0.5F, "the station shows the lid off");
        helper.assertTrue(BuiltInRegistries.FLUID.getKey(Fluids.WATER).equals(setup.station.state().getTank(KettleStation.DRINK).main()),
                "the tank shows water");
        lid(setup, player, false);
        helper.assertFalse(KettleStation.isOpen(helper.getLevel().getBlockState(setup.station.pos())), "the lid goes back on");
        helper.succeed();
    }

    /**
     * Green tea: the book fills the leaves from the inventory and takes the lid off, the kettle brews it over the campfire, and the
     * tea is poured out of the spout into bottles, one a click, or every serving from the inventory with shift.
     */
    public static void greenTeaBrewsAndIsPouredFromTheSpout(GameTestHelper helper) {
        Setup setup = kettle(helper, true);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getInventory().setItem(0, new ItemStack(respite("green_tea_leaves"), 3));
        CompoundTag fill = new CompoundTag();
        fill.putString(KettleStation.DISH, GREEN_TEA);
        setup.logic.onCustomAction(setup.station, player, KettleStation.ACTION_FILL, fill);
        helper.assertTrue(KettleStation.isOpen(helper.getLevel().getBlockState(setup.station.pos())), "the book took the lid off");
        helper.assertTrue(setup.kettle.handson$getItem(0).is(respite("green_tea_leaves"))
                && setup.kettle.handson$getItem(1).is(respite("green_tea_leaves")), "the book put two leaves in");
        helper.assertTrue(player.getInventory().countItem(respite("green_tea_leaves")) == 1, "one set from the inventory");
        pourWater(setup, player);
        helper.assertTrue(setup.kettle.handson$amount() == 1000, "water poured in after the leaves");
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(setup.kettle.handson$isHeated(), "the campfire heats the kettle");
            helper.assertTrue(setup.kettle.handson$data().get(0) > 0, "leaves put in before the water still brew");
            // The rest of the brew is skipped: the kettle is told it has brewed that long.
            setup.kettle.handson$data().set(0, setup.kettle.handson$data().get(1) - 2);
        });
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(BuiltInRegistries.FLUID.getKey(setup.kettle.handson$fluid()).getPath().equals("green_tea"),
                    "the water became green tea, got " + BuiltInRegistries.FLUID.getKey(setup.kettle.handson$fluid()));
            player.inventoryMenu.setCarried(new ItemStack(Items.GLASS_BOTTLE, 2));
            helper.assertTrue(setup.kettle.handson$canServe(player.inventoryMenu.getCarried()), "a bottle fills at the spout");
            setup.logic.onCustomAction(setup.station, player, KettleStation.ACTION_USE, at(KettleStation.SPOUT, false));
            helper.assertTrue(player.getInventory().countItem(respite("green_tea")) == 1, "one green tea into the inventory");
            helper.assertTrue(player.inventoryMenu.getCarried().is(Items.GLASS_BOTTLE) && player.inventoryMenu.getCarried().getCount() == 1,
                    "one bottle left in the hand");
            helper.assertTrue(setup.kettle.handson$amount() == 750, "a quarter of the kettle poured, got " + setup.kettle.handson$amount());
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            player.getInventory().setItem(5, new ItemStack(Items.GLASS_BOTTLE, 5));
            CompoundTag offered = at(KettleStation.SPOUT, true);
            offered.putBoolean(KettleStation.FROM_INVENTORY, true);
            setup.logic.onCustomAction(setup.station, player, KettleStation.ACTION_USE, offered);
            helper.assertTrue(setup.kettle.handson$amount() == 0, "an empty hand with shift pours the rest, got " + setup.kettle.handson$amount());
            helper.assertTrue(player.getInventory().countItem(respite("green_tea")) == 4, "four green teas in all");
            helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 2, "two bottles left");
            helper.succeed();
        });
    }

    /**
     * Two leaves dropped into the kettle one after the other stack in one slot; Farmer's Respite's green tea wants one in each of its
     * two, so the kettle lays them out that way, and a single milk for a long tea stays as it lies.
     */
    public static void leavesDroppedInLieAsTheRecipeWants(GameTestHelper helper) {
        Setup setup = kettle(helper, false);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        lid(setup, player, true);
        pourWater(setup, player);
        setup.station.state().setItem(SlotRef.of(KettleStation.INGREDIENTS, 0), new ItemStack(respite("green_tea_leaves"), 2));
        setup.logic.onSlotChanged(setup.station, SlotRef.of(KettleStation.INGREDIENTS, 0));
        helper.assertTrue(setup.kettle.handson$getItem(0).getCount() == 1 && setup.kettle.handson$getItem(1).is(respite("green_tea_leaves")),
                "one leaf in each slot, got " + setup.kettle.handson$getItem(0) + " and " + setup.kettle.handson$getItem(1));
        setup.kettle.handson$setItem(0, new ItemStack(Items.STICK, 2));
        setup.kettle.handson$setItem(1, ItemStack.EMPTY);
        setup.logic.onSlotChanged(setup.station, SlotRef.of(KettleStation.INGREDIENTS, 0));
        helper.assertTrue(setup.kettle.handson$getItem(0).getCount() == 2, "what makes nothing stays as it lies");
        helper.succeed();
    }

    /** The config's kettle_time makes a brew take longer, whether the station or a hopper put it in. */
    public static void kettleTimeScalesTheBrew(GameTestHelper helper) {
        Setup setup = kettle(helper, true);
        if (setup == null) {
            return;
        }
        HandsOnConfig.setKettleTime(2.0F);
        setup.kettle.handson$setItem(0, new ItemStack(respite("green_tea_leaves")));
        setup.kettle.handson$setItem(1, new ItemStack(respite("green_tea_leaves")));
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        lid(setup, player, true);
        pourWater(setup, player);
        int usual = setup.kettle.handson$recipes().stream().filter(recipe -> recipe.id().toString().equals(GREEN_TEA)).findFirst()
                .orElseThrow().ticks();
        helper.runAfterDelay(3, () -> {
            int total = setup.kettle.handson$data().get(1);
            HandsOnConfig.setKettleTime(1.0F);
            helper.assertTrue(total == 2 * usual, "green tea takes twice its " + usual + " ticks, got " + total);
            helper.succeed();
        });
    }
}
