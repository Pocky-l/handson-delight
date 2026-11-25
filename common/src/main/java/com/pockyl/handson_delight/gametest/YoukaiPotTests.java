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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.KitchenYoukaiPot;
import com.pockyl.handson_delight.station.YoukaiPotLogic;
import com.pockyl.handson_delight.station.YoukaiPotStation;

import java.util.function.Supplier;

/**
 * Youkai's Feasts' pots at the station: their recipes read with the pot each needs, a feast cooked from what the cook puts in (what
 * no recipe of the pot takes refused, the last thing taken back out), a soup from the book in the small pot. Every step goes through
 * the pot's own code. Each test passes at once where Youkai's Feasts is not there.
 */
public final class YoukaiPotTests {
    /** Its pot recipes cook 200 ticks; a little more for the heat and the last tick. */
    static final int COOKS = 230;

    private YoukaiPotTests() {
    }

    @SuppressWarnings("removal")
    private static Item feasts(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(KitchenYoukaiPot.MOD_ID, path));
    }

    private record Setup(PotTests.TestStation station, YoukaiPotLogic logic, KitchenYoukaiPot pot, BlockPos at) {
    }

    /** A pot over a lit campfire, with the logic the station would make for it; null without Youkai's Feasts. */
    private static Setup pot(GameTestHelper helper, Supplier<Block> block, StationType type) {
        if (!Platform.INSTANCE.isLoaded(KitchenYoukaiPot.MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CAMPFIRE);
        helper.setBlock(at, block.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, type);
        KitchenYoukaiPot pot = KitchenYoukaiPot.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(pot != null, "Youkai's Feasts' pot is a kitchen pot");
        return new Setup(station, new YoukaiPotLogic(station), pot, at);
    }

    /** A player at the pot who uses things up, as one in survival does: the game test's mock player builds as in creative. */
    private static Player cook(GameTestHelper helper, BlockPos at) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = false;
        BlockPos stands = helper.absolutePos(at.north(2));
        player.setPos(stands.getX() + 0.5, stands.getY(), stands.getZ() + 0.5);
        return player;
    }

    private static void act(Setup setup, Player player, String action, boolean all) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(YoukaiPotStation.ALL, all);
        setup.logic.onCustomAction(setup.station, player, action, data);
    }

    private static void opened(Setup setup, Player player) {
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
    }

    /**
     * An empty stockpot is the station too: it opens with an empty hand, not with a water bucket in the hand (its
     * own click keeps that); a water bucket let go over it at the station goes through its own click, the stockpot with water stands
     * there, the station stays on it and takes food.
     */
    public static void anEmptyYoukaiPotTakesItsWaterAtTheStation(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenYoukaiPot.MOD_ID)) {
            helper.succeed();
            return;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CAMPFIRE);
        helper.setBlock(at, YoukaiPotStation.EMPTY_STOCKPOT.get());
        BlockPos pos = helper.absolutePos(at);
        helper.assertTrue(YoukaiPotStation.STOCKPOT_TYPE.matches(helper.getLevel().getBlockState(pos)), "the empty stockpot is the station");
        Player player = cook(helper, at);
        helper.assertTrue(YoukaiPotStation.opens(player, pos, helper.getLevel().getBlockState(pos)), "an empty hand opens it");
        helper.assertFalse(YoukaiPotStation.dishes(helper.getLevel(), helper.getLevel().getBlockState(pos)).isEmpty(),
                "its book lists the stockpot's dishes while it is empty");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        helper.assertFalse(YoukaiPotStation.opens(player, pos, helper.getLevel().getBlockState(pos)), "water in the hand keeps its own click");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, YoukaiPotStation.STOCKPOT_TYPE);
        YoukaiPotLogic logic = new YoukaiPotLogic(station);
        station.users.add(player);
        logic.onOpen(station, player);
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        logic.onCustomAction(station, player, YoukaiPotStation.ACTION_PUT, new CompoundTag());
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(YoukaiPotStation.STOCKPOT.get()), "the water made the stockpot with water");
        helper.assertTrue(YoukaiPotStation.STOCKPOT_TYPE.matches(helper.getLevel().getBlockState(pos)), "the station stays on it");
        helper.assertTrue(player.inventoryMenu.getCarried().is(Items.BUCKET), "the empty bucket comes back to the hand");
        KitchenYoukaiPot pot = KitchenYoukaiPot.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(pot != null, "and it is a kitchen pot now");
        helper.succeed();
    }

    /** Its recipes are read with what goes in and the pot each needs, the pot its dish is served in. */
    public static void youkaiPotsReadTheirRecipes(GameTestHelper helper) {
        Setup setup = pot(helper, YoukaiPotStation.SHORT, YoukaiPotStation.SHORT_TYPE);
        if (setup == null) {
            return;
        }
        KitchenYoukaiPot.Dish crab = setup.pot.handson$dishes().stream()
                .filter(dish -> dish.id().toString().equals("youkaisfeasts:unordered_cooking/pot_of_tofu_crab_stew")).findFirst().orElse(null);
        helper.assertTrue(crab != null && crab.ingredients().size() == 3 && crab.ticks() > 0, "the tofu crab stew and its three");
        helper.assertTrue(crab.pot() == setup.pot.handson$pot(), "it cooks in the short iron pot");
        helper.assertTrue(YoukaiPotStation.dishes(setup.pot).stream().noneMatch(dish -> dish.id().toString()
                .equals("youkaisfeasts:unordered_cooking/pot_of_mushroom_soup")), "the mushroom soup is the stockpot's, not this pot's");
        // 13 pot recipes in its 0.5.2 (1.20.1) and in 1.1.2.
        helper.assertTrue(setup.pot.handson$dishes().size() >= 13, "every pot recipe, got " + setup.pot.handson$dishes().size());
        helper.succeed();
    }

    /**
     * A tofu crab stew at the short pot: tofu and a crab from the cursor, a stick refused, the crab
     * taken back out and put in again, the crab meat with shift; once all three are in it cooks over the campfire and the pot of the
     * stew stands there.
     */
    public static void youkaiShortPotCooksAFeast(GameTestHelper helper) {
        Setup setup = pot(helper, YoukaiPotStation.SHORT, YoukaiPotStation.SHORT_TYPE);
        if (setup == null) {
            return;
        }
        Player player = cook(helper, setup.at);
        opened(setup, player);
        player.inventoryMenu.setCarried(new ItemStack(feasts("tofu"), 2));
        act(setup, player, YoukaiPotStation.ACTION_PUT, false);
        helper.assertTrue(setup.pot.handson$contents().size() == 1 && player.inventoryMenu.getCarried().getCount() == 1,
                "one tofu went in, one stays in the hand");
        player.inventoryMenu.setCarried(new ItemStack(Items.STICK));
        act(setup, player, YoukaiPotStation.ACTION_PUT, false);
        helper.assertTrue(setup.pot.handson$contents().size() == 1 && player.inventoryMenu.getCarried().is(Items.STICK),
                "no recipe of this pot takes a stick: refused, it stays in the hand");
        player.inventoryMenu.setCarried(new ItemStack(feasts("crab")));
        act(setup, player, YoukaiPotStation.ACTION_PUT, false);
        helper.assertTrue(setup.pot.handson$contents().size() == 2 && player.inventoryMenu.getCarried().isEmpty(), "the crab went in");
        act(setup, player, YoukaiPotStation.ACTION_BACK, false);
        helper.assertTrue(setup.pot.handson$contents().size() == 1 && player.getInventory().countItem(feasts("crab")) == 1,
                "the crab came back out to the cook");
        player.inventoryMenu.setCarried(player.getInventory().removeItem(player.getInventory().findSlotMatchingItem(
                new ItemStack(feasts("crab"))), 1));
        act(setup, player, YoukaiPotStation.ACTION_PUT, false);
        player.inventoryMenu.setCarried(new ItemStack(feasts("crab_meat"), 3));
        act(setup, player, YoukaiPotStation.ACTION_PUT, true);
        helper.assertTrue(setup.pot.handson$contents().size() == 3 && player.inventoryMenu.getCarried().getCount() == 2,
                "with shift only as many crab meat went in as the pot takes: one");
        helper.runAfterDelay(5, () -> helper.assertTrue(setup.pot.handson$cooking() != null && setup.pot.handson$cookTime() > 0,
                "all three in: it cooks"));
        helper.runAfterDelay(COOKS, () -> {
            helper.assertBlockPresent(BuiltInRegistries.BLOCK.get(feastsId("pot_of_tofu_crab_stew")),
                    setup.at);
            helper.succeed();
        });
    }

    /**
     * Higan soup from the book at the small pot: soul sand and a crop go in from the inventory, what is in the pot already counting
     * and something of another dish refused; done, the bowl of it stands where the pot stood, as the pot serves it.
     */
    public static void youkaiSmallPotCooksFromTheBook(GameTestHelper helper) {
        Setup setup = pot(helper, YoukaiPotStation.SMALL, YoukaiPotStation.SMALL_TYPE);
        if (setup == null) {
            return;
        }
        Player player = cook(helper, setup.at);
        opened(setup, player);
        player.getInventory().setItem(0, new ItemStack(Items.SOUL_SAND, 2));
        player.getInventory().setItem(1, new ItemStack(Items.WHEAT, 2));
        helper.assertTrue(setup.pot.handson$put(player, player.getInventory().getItem(0)), "soul sand goes in by hand");
        CompoundTag fill = new CompoundTag();
        fill.putString(YoukaiPotStation.DISH, "youkaisfeasts:unordered_cooking/higan_soup");
        setup.logic.onCustomAction(setup.station, player, YoukaiPotStation.ACTION_FILL, fill);
        helper.assertTrue(setup.pot.handson$contents().size() == 2 && player.getInventory().countItem(Items.SOUL_SAND) == 1
                && player.getInventory().countItem(Items.WHEAT) == 1, "the soul sand in it counted, the wheat came from the "
                + "inventory, got " + setup.pot.handson$contents());
        helper.runAfterDelay(5, () -> helper.assertTrue(setup.pot.handson$cooking() != null, "it cooks the higan soup: "
                + setup.pot.handson$cooked() + "/" + setup.pot.handson$cookTime() + ", heated " + setup.pot.handson$isHeated()));
        helper.runAfterDelay(COOKS, () -> {
            helper.assertBlockPresent(BuiltInRegistries.BLOCK.get(feastsId("higan_soup")), setup.at);
            helper.succeed();
        });
    }

    /**
     * Mushroom soup in the stockpot, the cook gone before it is done (Discord, 2026-11-24: "the recipes go to 1 second and
     * stop"): put in at the station, left alone over the campfire, the pot of the soup stands there when its time is up.
     */
    public static void youkaiStockpotFinishesWithNobodyAtIt(GameTestHelper helper) {
        Setup setup = pot(helper, YoukaiPotStation.STOCKPOT, YoukaiPotStation.STOCKPOT_TYPE);
        if (setup == null) {
            return;
        }
        KitchenYoukaiPot.Dish soup = setup.pot.handson$dishes().stream()
                .filter(dish -> dish.id().toString().equals("youkaisfeasts:unordered_cooking/pot_of_mushroom_soup")).findFirst().orElse(null);
        helper.assertTrue(soup != null, "the stockpot's mushroom soup is read");
        Player player = cook(helper, setup.at);
        opened(setup, player);
        for (net.minecraft.world.item.crafting.Ingredient ingredient : soup.ingredients()) {
            ItemStack[] items = ingredient.getItems();
            helper.assertTrue(items.length > 0, "every ingredient names an item");
            helper.assertTrue(setup.pot.handson$put(player, items[0].copyWithCount(1)), "it takes " + items[0]);
        }
        setup.station.users.remove(player);
        setup.logic.onClose(setup.station, player);
        helper.runAfterDelay(5, () -> helper.assertTrue(setup.pot.handson$cooking() != null, "everything in: it cooks, "
                + setup.pot.handson$cooked() + "/" + setup.pot.handson$cookTime()));
        helper.runAfterDelay(soup.ticks() + 40, () -> {
            helper.assertBlockPresent(BuiltInRegistries.BLOCK.get(feastsId("pot_of_mushroom_soup")), setup.at);
            helper.succeed();
        });
    }

    @SuppressWarnings("removal")
    private static ResourceLocation feastsId(String path) {
        return ResourceLocation.fromNamespaceAndPath(KitchenYoukaiPot.MOD_ID, path);
    }
}
