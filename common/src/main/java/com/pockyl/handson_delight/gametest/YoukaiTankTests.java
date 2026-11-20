package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.KegLogic;
import com.pockyl.handson_delight.station.KegStation;
import com.pockyl.handson_delight.station.KitchenKeg;

import java.util.List;

/**
 * Youkai's Feasts' fermentation tank at the station: its lid is its block's, things go in one a slot only with it off, the last cook
 * puts it back on; water and rice ferment into sake under the lid, the tank lifts its lid itself when it is done, and the sake is
 * dipped out a container at a time; white tea leaves, however many, turn black. Each test passes at once where Youkai's Feasts is
 * not there.
 */
public final class YoukaiTankTests {
    private static final String MIO = "youkaisfeasts:mio";
    /** Its recipe ids carry their folder on 1.21.1. */
    private static final String MIO_RECIPE = "youkaisfeasts:simple_fermentation/mio";

    private YoukaiTankTests() {
    }

    @SuppressWarnings("removal")
    private static Item feasts(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(KegStation.TANK_MOD_ID, path));
    }

    private static Item rice() {
        return BuiltInRegistries.ITEM.getValue(FarmersDelightContent.id("rice"));
    }

    private record Setup(PotTests.TestStation station, KegLogic logic, KitchenKeg tank, BlockPos pos) {
    }

    /** A tank on stone, with the logic the station would make for it; null without Youkai's Feasts. */
    private static Setup tank(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KegStation.TANK_MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, KegStation.TANK.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, KegStation.TANK_TYPE);
        KitchenKeg tank = KitchenKeg.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(tank != null, "Youkai's Feasts' fermentation tank is a kitchen keg");
        return new Setup(station, new KegLogic(station), tank, pos);
    }

    private static boolean blockLidOff(GameTestHelper helper, Setup setup) {
        return helper.getLevel().getBlockState(setup.pos).getValue(BlockStateProperties.OPEN);
    }

    private static void lid(Setup setup, Player player, boolean off) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(KegStation.OPEN, off);
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_LID, data);
    }

    private static void use(Setup setup, Player player, String where, boolean fromInventory) {
        CompoundTag data = new CompoundTag();
        data.putString(KegStation.AT, where);
        data.putBoolean(KegStation.FROM_INVENTORY, fromInventory);
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_USE, data);
    }

    /** Its recipes are read as it ferments them: sake from water and rice, black tea leaves from white ones, six natto. */
    public static void youkaiTankReadsItsRecipes(GameTestHelper helper) {
        Setup setup = tank(helper);
        if (setup == null) {
            return;
        }
        List<KitchenKeg.Fermenting> recipes = setup.tank.handson$recipes();
        helper.assertTrue(recipes.size() >= 16, "every fermenting recipe, got " + recipes.size());
        KitchenKeg.Fermenting mio = find(recipes, MIO_RECIPE);
        helper.assertTrue(mio != null && mio.base().isSame(Fluids.WATER) && mio.baseAmount() == 1000 && mio.ingredients().size() == 4,
                "mio: a tank of water and four rice");
        helper.assertTrue(BuiltInRegistries.FLUID.getKey(mio.resultFluid()).toString().equals(MIO) && mio.resultItem().isEmpty(),
                "it makes the sake mio");
        helper.assertTrue(setup.tank.handson$makes(mio, List.of(new ItemStack(rice()), new ItemStack(rice()), new ItemStack(rice()),
                new ItemStack(rice()))) && !setup.tank.handson$makes(mio, List.of(new ItemStack(rice()))), "four rice make it, one does not");
        KitchenKeg.Fermenting leaves = find(recipes, "youkaisfeasts:simple_fermentation/black_tea_leaves");
        helper.assertTrue(leaves != null && leaves.base() == Fluids.EMPTY && leaves.resultItem().is(feasts("black_tea_leaves")),
                "white tea leaves turn black without liquid");
        helper.assertTrue(setup.tank.handson$makes(leaves, List.of(new ItemStack(feasts("white_tea_leaves")),
                new ItemStack(feasts("white_tea_leaves")), new ItemStack(feasts("white_tea_leaves")))), "however many lie there");
        KitchenKeg.Fermenting natto = find(recipes, "youkaisfeasts:simple_fermentation/nattou");
        helper.assertTrue(natto != null && natto.resultItem().getCount() == 6 && natto.ingredients().size() == 7,
                "six soybeans and straw make six natto");
        helper.assertTrue(!setup.tank.handson$feelsWarmth() && !setup.tank.handson$hasOutput() && setup.tank.handson$perSlot() == 1,
                "no warmth, no output slot, one a slot");
        helper.succeed();
    }

    private static KitchenKeg.Fermenting find(List<KitchenKeg.Fermenting> recipes, String id) {
        return recipes.stream().filter(recipe -> recipe.id().toString().equals(id)).findFirst().orElse(null);
    }

    /**
     * Its lid is its block's: the station shows it as the block has it; with it on nothing goes in or comes out; off, water is poured
     * in and things go in one a slot; the last cook leaving puts it back on, and the block shows it.
     */
    public static void youkaiTankLidIsItsBlocks(GameTestHelper helper) {
        Setup setup = tank(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        setup.logic.tick(setup.station);
        helper.assertFalse(blockLidOff(helper, setup) || KegLogic.isOpen(setup.station), "a new tank stands shut");
        SlotRef first = SlotRef.of(KegStation.INGREDIENTS, 0);
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, new ItemStack(rice())),
                "nothing goes in through the lid");
        lid(setup, player, true);
        helper.assertTrue(blockLidOff(helper, setup), "a click takes the block's own lid off");
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        use(setup, player, KegStation.MOUTH, false);
        helper.assertTrue(setup.tank.handson$fluid().isSame(Fluids.WATER) && setup.tank.handson$amount() == 1000,
                "a bucket of water poured in, got " + setup.tank.handson$amount());
        helper.assertTrue(player.inventoryMenu.getCarried().is(Items.BUCKET), "the empty bucket stays in the hand");
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, first, new ItemStack(rice())),
                "rice goes in with the lid off");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.GLASS_BOTTLE)),
                "a bottle is filled, never dropped in");
        setup.station.state().setItem(first, new ItemStack(rice(), 3));
        helper.assertTrue(setup.tank.handson$getItem(0).getCount() == 1, "one a slot, got " + setup.tank.handson$getItem(0));
        lid(setup, player, false);
        helper.assertFalse(setup.logic.mayTake(setup.station, player, first), "nothing is taken out through the lid");
        // Its redstone (or its own click) lifts the lid: the station follows the block.
        helper.getLevel().setBlockAndUpdate(setup.pos, helper.getLevel().getBlockState(setup.pos).setValue(BlockStateProperties.OPEN, true));
        setup.logic.tick(setup.station);
        helper.assertTrue(KegLogic.isOpen(setup.station), "the station shows the lid the block has");
        setup.logic.onClose(setup.station, player);
        helper.assertFalse(blockLidOff(helper, setup), "the last cook leaving puts the lid back on");
        helper.succeed();
    }

    /**
     * Mio: the book puts four rice in from the inventory and takes the lid off; water is poured in; with the lid on it ferments as the
     * tank does, which lifts its own lid when it is done; the sake is dipped out a container at a time, from the hand or, with an
     * empty hand, from the inventory.
     */
    public static void youkaiMioFermentsAndIsDipped(GameTestHelper helper) {
        Setup setup = tank(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getInventory().setItem(0, new ItemStack(rice(), 9));
        CompoundTag fill = new CompoundTag();
        fill.putString(KegStation.DISH, MIO_RECIPE);
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_FILL, fill);
        helper.assertTrue(blockLidOff(helper, setup), "the book took the lid off");
        helper.assertTrue(player.getInventory().countItem(rice()) == 5, "four rice from the inventory, one a slot");
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        use(setup, player, KegStation.MOUTH, false);
        lid(setup, player, false);
        helper.runAfterDelay(4, () -> {
            int total = setup.tank.handson$data().get(1);
            helper.assertTrue(total > 0 && setup.tank.handson$data().get(0) > 0, "it ferments under the lid, total " + total);
            setup.tank.handson$data().set(0, total - 2);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(BuiltInRegistries.FLUID.getKey(setup.tank.handson$fluid()).toString().equals(MIO),
                    "the water became mio, got " + BuiltInRegistries.FLUID.getKey(setup.tank.handson$fluid()));
            helper.assertTrue(blockLidOff(helper, setup), "the tank lifted its lid itself");
            setup.logic.tick(setup.station);
            helper.assertTrue(KegLogic.isOpen(setup.station), "and the station shows it off");
            Item container = BuiltInRegistries.ITEM.stream().filter(item -> setup.tank.handson$canTap(new ItemStack(item))).findFirst()
                    .orElse(Items.AIR);
            helper.assertTrue(container != Items.AIR, "some container takes mio");
            Item drink = setup.tank.handson$drinkOf(setup.tank.handson$fluid()).getItem();
            player.inventoryMenu.setCarried(new ItemStack(container, 2));
            use(setup, player, KegStation.TAP, false);
            helper.assertTrue(player.getInventory().countItem(drink) == 1, "one mio dipped into the inventory");
            helper.assertTrue(setup.tank.handson$amount() == 750, "a serving's worth gone, got " + setup.tank.handson$amount());
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            player.getInventory().setItem(5, new ItemStack(container, 1));
            use(setup, player, KegStation.TAP, true);
            helper.assertTrue(player.getInventory().countItem(drink) == 2, "an empty hand dips one from the inventory");
            helper.succeed();
        });
    }

    /**
     * Its flask holds any liquid, a whole tank of it (GH #33): a flask of soy sauce pours in, an empty flask brought in the hand is
     * filled, and an empty hand is never offered one, which would take the whole tank.
     */
    public static void youkaiFlaskIsFilledAtTheTank(GameTestHelper helper) {
        Setup setup = tank(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        lid(setup, player, true);
        player.inventoryMenu.setCarried(new ItemStack(feasts("soy_sauce_flask")));
        use(setup, player, KegStation.MOUTH, false);
        helper.assertTrue(BuiltInRegistries.FLUID.getKey(setup.tank.handson$fluid()).toString().equals("youkaisfeasts:soy_sauce")
                && setup.tank.handson$amount() == 1000, "a flask of soy sauce pours in, got " + setup.tank.handson$amount() + " of "
                + BuiltInRegistries.FLUID.getKey(setup.tank.handson$fluid()));
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().clearContent();
        player.getInventory().setItem(3, new ItemStack(feasts("sake_bottle")));
        use(setup, player, KegStation.TAP, true);
        helper.assertTrue(setup.tank.handson$amount() == 1000, "an empty hand is not offered the flask");
        ItemStack flask = new ItemStack(feasts("sake_bottle"));
        helper.assertTrue(setup.tank.handson$canTap(flask), "an empty flask is filled with soy sauce");
        player.getInventory().clearContent();
        player.inventoryMenu.setCarried(flask);
        use(setup, player, KegStation.TAP, false);
        helper.assertTrue(setup.tank.handson$amount() == 0, "the flask took the tank, " + setup.tank.handson$amount() + " left");
        // A full flask of soy sauce is its own soy sauce flask.
        helper.assertTrue(player.getInventory().countItem(feasts("soy_sauce_flask")) + (player.inventoryMenu.getCarried()
                .is(feasts("soy_sauce_flask")) ? 1 : 0) == 1, "the cook has a flask of soy sauce");
        helper.succeed();
    }

    /** White tea leaves, three of them, all turn black under the lid, and are taken out where they lay. */
    public static void youkaiTeaLeavesTurnBlack(GameTestHelper helper) {
        Setup setup = tank(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        lid(setup, player, true);
        for (int slot = 0; slot < 3; slot++) {
            setup.station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, slot), new ItemStack(feasts("white_tea_leaves")));
        }
        lid(setup, player, false);
        helper.runAfterDelay(4, () -> {
            int total = setup.tank.handson$data().get(1);
            helper.assertTrue(total > 0, "three white tea leaves ferment");
            setup.tank.handson$data().set(0, total - 2);
        });
        helper.runAfterDelay(10, () -> {
            for (int slot = 0; slot < 3; slot++) {
                helper.assertTrue(setup.tank.handson$getItem(slot).is(feasts("black_tea_leaves")),
                        "slot " + slot + " black, got " + setup.tank.handson$getItem(slot));
            }
            setup.logic.tick(setup.station);
            helper.assertTrue(setup.logic.mayTake(setup.station, player, SlotRef.of(KegStation.INGREDIENTS, 0)),
                    "the lid lifted, they are taken out");
            helper.succeed();
        });
    }
}
