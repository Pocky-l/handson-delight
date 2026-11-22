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
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.KettleLogic;
import com.pockyl.handson_delight.station.KettleStation;
import com.pockyl.handson_delight.station.KitchenKettle;

import java.util.List;

/**
 * Youkai's Feasts' kettle at the station: its lid is the station's, off only while somebody works at it; four leaves go in one a
 * slot; the water boils over a campfire before black tea brews, as the kettle brews it; the tea is poured from the spout a bottle at
 * a time. Each test passes at once where Youkai's Feasts is not there.
 */
public final class YoukaiKettleTests {
    private static final String BLACK_TEA = "youkaisfeasts:black_tea";
    /** Its recipe: on 1.21.1 its kettle recipes lie in a folder of their own. */
    private static final String BLACK_TEA_RECIPE = "youkaisfeasts:kettle/black_tea";

    private YoukaiKettleTests() {
    }

    @SuppressWarnings("removal")
    private static Item feasts(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(KettleStation.YOUKAI_MOD_ID, path));
    }

    private record Setup(PotTests.TestStation station, KettleLogic logic, KitchenKettle kettle) {
    }

    /** A kettle over a lit campfire or on stone, with the logic the station would make for it; null without Youkai's Feasts. */
    private static Setup kettle(GameTestHelper helper, boolean heated) {
        if (!Platform.INSTANCE.isLoaded(KettleStation.YOUKAI_MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), heated ? Blocks.CAMPFIRE : Blocks.STONE);
        helper.setBlock(at, KettleStation.YOUKAI_KETTLE.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, KettleStation.YOUKAI_TYPE);
        KitchenKettle kettle = KitchenKettle.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(kettle != null, "Youkai's Feasts' kettle is a kitchen kettle");
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
     * Its lid is the station's: on at first, nothing goes in through it; off, water is poured in and leaves go in, one a slot (its
     * slots hold one each); the last cook leaving puts it back on, as the world draws the kettle.
     */
    public static void youkaiKettleLidIsTheStations(GameTestHelper helper) {
        Setup setup = kettle(helper, false);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        helper.assertFalse(setup.kettle.handson$lidInBlock() || setup.kettle.handson$isOpen(), "a kettle stands with the station's lid on");
        pourWater(setup, player);
        helper.assertTrue(setup.kettle.handson$amount() == 0 && player.inventoryMenu.getCarried().is(Items.WATER_BUCKET),
                "nothing is poured in through the lid");
        lid(setup, player, true);
        pourWater(setup, player);
        helper.assertTrue(setup.kettle.handson$fluid() == Fluids.WATER && setup.kettle.handson$amount() == 1000,
                "with the lid off the water goes in, got " + setup.kettle.handson$amount());
        helper.assertTrue(player.inventoryMenu.getCarried().is(Items.BUCKET), "the empty bucket stays in the hand");
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, SlotRef.of(KettleStation.INGREDIENTS, 0),
                new ItemStack(feasts("black_tea_leaves"))), "leaves go in");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(KettleStation.INGREDIENTS, 0),
                new ItemStack(Items.GLASS_BOTTLE)), "a bottle is filled, not dropped in");
        for (int slot = 0; slot < 4; slot++) {
            setup.station.state().setItem(SlotRef.of(KettleStation.INGREDIENTS, slot), new ItemStack(feasts("black_tea_leaves"), 2));
            setup.logic.onSlotChanged(setup.station, SlotRef.of(KettleStation.INGREDIENTS, slot));
            helper.assertTrue(setup.kettle.handson$getItem(slot).is(feasts("black_tea_leaves")) && setup.kettle.handson$getItem(slot).getCount() == 1,
                    "one leaf in slot " + slot + ", got " + setup.kettle.handson$getItem(slot));
        }
        setup.logic.tick(setup.station);
        helper.assertTrue(setup.station.state().getControl(KettleStation.LID) > 0.5F, "the station shows the lid off");
        helper.assertTrue(setup.station.state().getControl(KettleStation.BOIL) < 0.5F, "cold water on stone is far from the boil");
        setup.logic.onClose(setup.station, player);
        helper.assertFalse(setup.kettle.handson$isOpen(), "the last cook leaving puts the lid back on");
        helper.succeed();
    }

    /**
     * An empty hand at the spout of a kettle of plain water takes no bucket from the inventory: it asks for a drink, never for the
     * water back. A bucket brought on the cursor still draws it, as one served filling.
     */
    public static void youkaiKettleWaterStaysFromAnEmptyHand(GameTestHelper helper) {
        Setup setup = kettle(helper, false);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        lid(setup, player, true);
        pourWater(setup, player);
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(3, new ItemStack(Items.BUCKET));
        helper.assertTrue(KettleLogic.containerSlot(setup.kettle, player.getInventory()) < 0, "no bucket is offered to an empty hand");
        CompoundTag fromInventory = at(KettleStation.SPOUT, false);
        fromInventory.putBoolean(KettleStation.FROM_INVENTORY, true);
        setup.logic.onCustomAction(setup.station, player, KettleStation.ACTION_USE, fromInventory);
        helper.assertTrue(setup.kettle.handson$amount() == 1000 && player.getInventory().getItem(3).is(Items.BUCKET),
                "the water stays in the kettle, got " + setup.kettle.handson$amount());
        player.inventoryMenu.setCarried(player.getInventory().removeItemNoUpdate(3));
        setup.logic.onCustomAction(setup.station, player, KettleStation.ACTION_USE, at(KettleStation.SPOUT, false));
        helper.assertTrue(setup.kettle.handson$amount() == 0 && player.inventoryMenu.getCarried().is(Items.WATER_BUCKET),
                "a bucket on the cursor draws the water");
        helper.succeed();
    }

    /**
     * Black tea: the book puts four leaves in from the inventory (one set: a slot holds one) and takes the lid off; the water poured in
     * comes to the boil over the campfire before anything brews; then the kettle brews it, and the tea is poured out of the spout into
     * bottles, one a click, or every serving from the inventory with shift.
     */
    public static void youkaiBlackTeaBoilsBrewsAndIsPoured(GameTestHelper helper) {
        Setup setup = kettle(helper, true);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getInventory().setItem(0, new ItemStack(feasts("black_tea_leaves"), 9));
        CompoundTag fill = new CompoundTag();
        fill.putString(KettleStation.DISH, BLACK_TEA_RECIPE);
        fill.putBoolean(KettleStation.ALL, true);
        setup.logic.onCustomAction(setup.station, player, KettleStation.ACTION_FILL, fill);
        helper.assertTrue(setup.kettle.handson$isOpen(), "the book took the lid off");
        helper.assertTrue(player.getInventory().countItem(feasts("black_tea_leaves")) == 5, "one set of four leaves from the inventory, got "
                + (9 - player.getInventory().countItem(feasts("black_tea_leaves"))));
        pourWater(setup, player);
        helper.assertTrue(setup.kettle.handson$amount() == 1000, "water poured in after the leaves");
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(setup.kettle.handson$isHeated(), "the campfire heats the kettle");
            float boil = setup.kettle.handson$boil();
            helper.assertTrue(boil > 0.0F && boil < 1.0F, "the water warms, got " + boil);
            helper.assertTrue(setup.kettle.handson$data().get(0) == 0, "nothing brews before the boil");
            setup.logic.tick(setup.station);
            int left = setup.station.state().data().getInt(KettleStation.LEFT);
            helper.assertTrue(left > 45 * 20, "the time left counts the boil, got " + left + " ticks");
            // The rest of the boil is skipped: the kettle is told its water is that hot.
            setup.kettle.handson$data().set(2, 999);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(setup.kettle.handson$boil() >= 1.0F, "the water boils");
            helper.assertTrue(setup.kettle.handson$data().get(0) > 0, "the tea brews in boiling water");
            setup.kettle.handson$data().set(0, setup.kettle.handson$data().get(1) - 2);
        });
        helper.runAfterDelay(16, () -> {
            helper.assertTrue(BuiltInRegistries.FLUID.getKey(setup.kettle.handson$fluid()).toString().equals(BLACK_TEA),
                    "the water became black tea, got " + BuiltInRegistries.FLUID.getKey(setup.kettle.handson$fluid()));
            Item tea = setup.kettle.handson$drinkOf(setup.kettle.handson$fluid()).getItem();
            helper.assertTrue(tea != Items.AIR, "black tea has a drink");
            player.inventoryMenu.setCarried(new ItemStack(Items.GLASS_BOTTLE, 2));
            helper.assertTrue(setup.kettle.handson$canServe(player.inventoryMenu.getCarried()), "a bottle fills at the spout");
            setup.logic.onCustomAction(setup.station, player, KettleStation.ACTION_USE, at(KettleStation.SPOUT, false));
            helper.assertTrue(player.getInventory().countItem(tea) == 1, "one black tea into the inventory");
            helper.assertTrue(player.inventoryMenu.getCarried().is(Items.GLASS_BOTTLE) && player.inventoryMenu.getCarried().getCount() == 1,
                    "one bottle left in the hand");
            helper.assertTrue(setup.kettle.handson$amount() == 750, "a bottle's worth poured, got " + setup.kettle.handson$amount());
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            player.getInventory().setItem(5, new ItemStack(Items.GLASS_BOTTLE, 5));
            CompoundTag offered = at(KettleStation.SPOUT, true);
            offered.putBoolean(KettleStation.FROM_INVENTORY, true);
            setup.logic.onCustomAction(setup.station, player, KettleStation.ACTION_USE, offered);
            helper.assertTrue(setup.kettle.handson$amount() == 0, "an empty hand with shift pours the rest, got " + setup.kettle.handson$amount());
            helper.assertTrue(player.getInventory().countItem(tea) == 4, "four black teas in all");
            helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 2, "two bottles left");
            helper.succeed();
        });
    }

    /**
     * Tea mocha steeps a milk bottle whole with its leaves and cocoa: the bottle goes into a slot of the kettle of water, empty or not,
     * and is never poured in; the four make the tea once the water boils.
     */
    public static void youkaiTeaMochaTakesAMilkBottle(GameTestHelper helper) {
        Setup setup = kettle(helper, true);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack milk = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "milk_bottle")));
        helper.assertFalse(milk.isEmpty(), "Farmer's Delight's milk bottle");
        lid(setup, player, true);
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, SlotRef.of(KettleStation.INGREDIENTS, 0), milk),
                "a milk bottle goes into an empty kettle");
        helper.assertFalse(setup.kettle.handson$canPour(milk), "milk is never poured into an empty kettle");
        pourWater(setup, player);
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, SlotRef.of(KettleStation.INGREDIENTS, 0), milk),
                "a milk bottle goes into a kettle of water");
        helper.assertFalse(setup.kettle.handson$canPour(milk), "milk is never poured into the water");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(KettleStation.INGREDIENTS, 0), new ItemStack(Items.WATER_BUCKET)),
                "a water bucket is still poured, not dropped in");
        List<ItemStack> parts = List.of(new ItemStack(feasts("black_tea_leaves")), new ItemStack(feasts("black_tea_leaves")),
                new ItemStack(Items.COCOA_BEANS), milk);
        for (int slot = 0; slot < parts.size(); slot++) {
            setup.station.state().setItem(SlotRef.of(KettleStation.INGREDIENTS, slot), parts.get(slot).copy());
            setup.logic.onSlotChanged(setup.station, SlotRef.of(KettleStation.INGREDIENTS, slot));
        }
        setup.kettle.handson$data().set(2, 999);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(setup.kettle.handson$data().get(0) > 0, "tea mocha brews in boiling water");
            setup.kettle.handson$data().set(0, setup.kettle.handson$data().get(1) - 2);
        });
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(BuiltInRegistries.FLUID.getKey(setup.kettle.handson$fluid()).toString().equals("youkaisfeasts:tea_mocha"),
                    "the water became tea mocha, got " + BuiltInRegistries.FLUID.getKey(setup.kettle.handson$fluid()));
            helper.succeed();
        });
    }

    /** Its recipes are read as the kettle brews them: a full kettle of water, four of a leaf, the tea it becomes. */
    public static void youkaiKettleReadsItsRecipes(GameTestHelper helper) {
        Setup setup = kettle(helper, false);
        if (setup == null) {
            return;
        }
        KitchenKettle.Brewing black = setup.kettle.handson$recipes().stream().filter(recipe -> recipe.id().toString().equals(BLACK_TEA_RECIPE))
                .findFirst().orElse(null);
        helper.assertTrue(black != null, "black tea is among " + setup.kettle.handson$recipes().size() + " recipes");
        helper.assertTrue(black.base().isSame(Fluids.WATER) && black.baseAmount() == 1000 && black.ingredients().size() == 4,
                "a kettle of water and four leaves");
        helper.assertTrue(BuiltInRegistries.FLUID.getKey(black.result()).toString().equals(BLACK_TEA), "it makes black tea");
        // Fourteen teas, two of them only with another mod loaded.
        helper.assertTrue(setup.kettle.handson$recipes().size() >= 12, "every kettle recipe, got " + setup.kettle.handson$recipes().size());
        helper.succeed();
    }
}
