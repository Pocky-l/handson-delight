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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.KegLogic;
import com.pockyl.handson_delight.station.KegStation;
import com.pockyl.handson_delight.station.KitchenKeg;

/**
 * Brewin' and Chewin's keg at the station: water poured in at the opening, beer fermenting as the keg ferments it, drinks drawn
 * at the tap. Each test passes at once where Brewin' and Chewin' is not there.
 */
public final class KegTests {
    private KegTests() {
    }

    @SuppressWarnings("removal")
    private static Item bnc(String path) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(KegStation.MOD_ID, path));
    }

    private record Setup(PotTests.TestStation station, KegLogic logic, KitchenKeg keg) {
    }

    /** A keg on the floor, with the logic the station would make for it; null without Brewin' and Chewin'. */
    private static Setup keg(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KegStation.MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, KegStation.KEG.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, KegStation.TYPE);
        KitchenKeg keg = KitchenKeg.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(keg != null, "the keg is a kitchen keg");
        return new Setup(station, new KegLogic(station), keg);
    }

    private static CompoundTag at(String where, boolean all) {
        CompoundTag data = new CompoundTag();
        data.putString(KegStation.AT, where);
        data.putBoolean(KegStation.ALL, all);
        return data;
    }

    public static void waterPoursInAndTheBucketComesBack(GameTestHelper helper) {
        Setup setup = keg(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer();
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(KegStation.INGREDIENTS, 0), new ItemStack(Items.WATER_BUCKET)),
                "a bucket is poured, not dropped in");
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, SlotRef.of(KegStation.INGREDIENTS, 0), new ItemStack(Items.WHEAT)),
                "food goes in");
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_USE, at(KegStation.MOUTH, false));
        helper.assertTrue(setup.keg.handson$fluid() == Fluids.WATER && setup.keg.handson$amount() == 1000,
                "a bucket of water in the keg, got " + setup.keg.handson$amount());
        helper.assertTrue(player.inventoryMenu.getCarried().is(Items.BUCKET), "the empty bucket stays in the hand");
        helper.succeed();
    }

    public static void beerFermentsAndIsDrawnAtTheTap(GameTestHelper helper) {
        Setup setup = keg(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer();
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_USE, at(KegStation.MOUTH, false));
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(0, new ItemStack(Items.WHEAT, 2));
        player.getInventory().setItem(1, new ItemStack(Items.WHEAT_SEEDS, 2));
        player.getInventory().setItem(2, new ItemStack(Items.BROWN_MUSHROOM, 2));
        CompoundTag fill = new CompoundTag();
        fill.putString(KegStation.DISH, "brewinandchewin:fermenting/beer_from_water");
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_FILL, fill);
        helper.assertTrue(!setup.keg.handson$getItem(0).isEmpty() && !setup.keg.handson$getItem(2).isEmpty(), "the book filled the keg");
        helper.assertTrue(player.getInventory().countItem(Items.WHEAT) == 1, "one set from the inventory");
        helper.runAfterDelay(2, () -> {
            // Nine thousand ticks of waiting are skipped: the keg is told it has fermented that long.
            setup.keg.handson$data().set(0, 9598);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(BuiltInRegistries.FLUID.getKey(setup.keg.handson$fluid()).getPath().equals("beer"),
                    "the water became beer, got " + BuiltInRegistries.FLUID.getKey(setup.keg.handson$fluid()));
            player.inventoryMenu.setCarried(new ItemStack(bnc("tankard"), 2));
            helper.assertTrue(setup.keg.handson$canTap(player.inventoryMenu.getCarried()), "a tankard fills at the tap");
            setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_USE, at(KegStation.TAP, false));
            helper.assertTrue(player.getInventory().countItem(bnc("beer")) == 1, "one beer into the inventory");
            helper.assertTrue(player.inventoryMenu.getCarried().getCount() == 1, "one tankard left in the hand");
            helper.assertTrue(setup.keg.handson$amount() == 750, "a quarter of the keg drawn, got " + setup.keg.handson$amount());
            player.getAbilities().instabuild = true;
            player.inventoryMenu.setCarried(new ItemStack(bnc("tankard"), 2));
            setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_USE, at(KegStation.TAP, false));
            player.getAbilities().instabuild = false;
            helper.assertTrue(player.getInventory().countItem(bnc("beer")) == 2 && setup.keg.handson$amount() == 500,
                    "in creative mode a tankard fills at the tap too, got " + setup.keg.handson$amount());
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            player.getInventory().setItem(5, new ItemStack(bnc("tankard"), 4));
            CompoundTag offered = at(KegStation.TAP, true);
            offered.putBoolean(KegStation.FROM_INVENTORY, true);
            setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_USE, offered);
            helper.assertTrue(setup.keg.handson$amount() == 0, "an empty hand with shift draws the rest, got " + setup.keg.handson$amount());
            helper.assertTrue(player.getInventory().countItem(bnc("beer")) == 4, "four beers in all");
            helper.succeed();
        });
    }

    /**
     * A keg stood on its end (Brewin' and Chewin's {@code vertical}) is the other station type, with its own scene: the lying keg's type
     * does not take it, and the same logic pours in and ferments in it.
     */
    public static void aStandingKegIsAStationOfItsOwn(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KegStation.MOD_ID)) {
            helper.succeed();
            return;
        }
        BlockState lying = KegStation.KEG.get().defaultBlockState();
        BlockState standing = lying;
        for (Property<?> property : lying.getProperties()) {
            if (property.getName().equals("vertical") && property instanceof BooleanProperty vertical) {
                standing = lying.setValue(vertical, true);
            }
        }
        helper.assertTrue(KegStation.isStanding(standing) && !KegStation.isStanding(lying), "the keg stands up by its vertical property");
        helper.assertTrue(KegStation.TYPE.matches(lying) && !KegStation.STANDING_TYPE.matches(lying), "a lying keg is the lying station");
        helper.assertTrue(KegStation.STANDING_TYPE.matches(standing) && !KegStation.TYPE.matches(standing), "a standing one the standing");
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, standing);
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, KegStation.STANDING_TYPE);
        KitchenKeg keg = KitchenKeg.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(keg != null, "the standing keg is a kitchen keg");
        KegLogic logic = new KegLogic(station);
        Player player = helper.makeMockPlayer();
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        logic.onCustomAction(station, player, KegStation.ACTION_USE, at(KegStation.MOUTH, false));
        helper.assertTrue(keg.handson$fluid() == Fluids.WATER && keg.handson$amount() == 1000, "water pours in at the top");
        station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 0), new ItemStack(Items.WHEAT, 2));
        helper.assertTrue(keg.handson$getItem(0).is(Items.WHEAT), "the station's slots are the keg's own");
        helper.succeed();
    }

}
