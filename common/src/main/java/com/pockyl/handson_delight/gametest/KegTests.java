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
import net.minecraft.world.level.block.Block;
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
        lid(setup.station, setup.logic, player, true);
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(KegStation.INGREDIENTS, 0), new ItemStack(Items.WATER_BUCKET)),
                "a bucket is poured, not dropped in");
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, SlotRef.of(KegStation.INGREDIENTS, 0), new ItemStack(Items.WHEAT)),
                "food goes in");
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_USE, at(KegStation.MOUTH, false));
        helper.assertTrue(setup.keg.handson$fluid() == Fluids.WATER && setup.keg.handson$amount() == 1000,
                "a bucket of water in the keg, got " + setup.keg.handson$amount());
        helper.assertTrue(player.inventoryMenu.getCarried().is(Items.BUCKET), "the empty bucket stays in the hand");
        // The station's tank holds the keg's own liquid, so that clients draw it as what it is.
        setup.logic.tick(setup.station);
        helper.assertTrue(BuiltInRegistries.FLUID.getKey(Fluids.WATER).equals(setup.station.state().getTank(KegStation.DRINK).main()),
                "the tank shows water, got " + setup.station.state().getTank(KegStation.DRINK).main());
        helper.succeed();
    }

    public static void beerFermentsAndIsDrawnAtTheTap(GameTestHelper helper) {
        Setup setup = keg(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer();
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        lid(setup.station, setup.logic, player, true);
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
        lid(station, logic, player, true);
        logic.onCustomAction(station, player, KegStation.ACTION_USE, at(KegStation.MOUTH, false));
        helper.assertTrue(keg.handson$fluid() == Fluids.WATER && keg.handson$amount() == 1000, "water pours in at the top");
        station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 0), new ItemStack(Items.WHEAT, 2));
        helper.assertTrue(keg.handson$getItem(0).is(Items.WHEAT), "the station's slots are the keg's own");
        helper.succeed();
    }


    /** The cook takes the lid off ({@code open}) or puts it back, with a click on it. */
    private static void lid(PotTests.TestStation station, KegLogic logic, Player player, boolean open) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(KegStation.OPEN, open);
        logic.onCustomAction(station, player, KegStation.ACTION_LID, data);
    }

    /**
     * The lid: with it on, nothing is poured or dropped in at the top; with it off, the keg takes them but its brew waits, and goes
     * on once the lid is back; the last cook to leave puts it back.
     */
    public static void theLidKeepsThingsOutAndTheBrewWaitsUnderIt(GameTestHelper helper) {
        Setup setup = keg(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer();
        setup.station.users.add(player);
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_USE, at(KegStation.MOUTH, false));
        helper.assertTrue(setup.keg.handson$amount() == 0 && player.inventoryMenu.getCarried().is(Items.WATER_BUCKET),
                "nothing is poured in through the lid");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(KegStation.INGREDIENTS, 0), new ItemStack(Items.WHEAT)),
                "nor dropped in");
        lid(setup.station, setup.logic, player, true);
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_USE, at(KegStation.MOUTH, false));
        helper.assertTrue(setup.keg.handson$amount() == 1000, "with the lid off the water goes in, got " + setup.keg.handson$amount());
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, SlotRef.of(KegStation.INGREDIENTS, 0), new ItemStack(Items.WHEAT)),
                "and food may be dropped in");
        setup.station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 0), new ItemStack(Items.WHEAT));
        setup.station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 1), new ItemStack(Items.WHEAT_SEEDS));
        setup.station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 2), new ItemStack(Items.BROWN_MUSHROOM));
        setup.keg.handson$feelTemperature();
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(setup.keg.handson$data().get(0) <= 1, "with the lid off the beer waits, got " + setup.keg.handson$data().get(0));
            lid(setup.station, setup.logic, player, false);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(setup.keg.handson$data().get(0) > 20, "under its lid it ferments, got " + setup.keg.handson$data().get(0));
            lid(setup.station, setup.logic, player, true);
            setup.station.users.clear();
        });
        helper.runAfterDelay(64, () -> {
            helper.assertFalse(KegLogic.isOpen(setup.station), "the last cook to leave puts the lid back");
            helper.succeed();
        });
    }

    /** Places around the keg (within the two blocks it feels warmth and cold from), one after the other. */
    private static final BlockPos[] AROUND = {new BlockPos(3, 2, 1), new BlockPos(1, 2, 3), new BlockPos(3, 2, 3), new BlockPos(2, 2, 3),
            new BlockPos(3, 2, 2), new BlockPos(0, 2, 3), new BlockPos(3, 2, 0), new BlockPos(0, 2, 2)};

    /**
     * Sets {@code source} round the keg until the keg feels as warm or as cool as the recipe in it wants, as a player brings fire
     * or ice; the number of blocks it took.
     */
    private static int warmTo(GameTestHelper helper, Setup setup, Block source) {
        int wanted = setup.keg.handson$wantedTemperature();
        for (int i = 0; i < AROUND.length; i++) {
            setup.keg.handson$feelTemperature();
            if (KegLogic.fitsWarmth(setup.keg.handson$data().get(2), wanted)) {
                return i;
            }
            helper.setBlock(AROUND[i], source);
        }
        setup.keg.handson$feelTemperature();
        return AROUND.length;
    }

    /** The keg is told it has fermented all but the last two ticks of its recipe, which it then finishes by itself. */
    private static void skipToTheEnd(GameTestHelper helper, Setup setup, String what) {
        int total = setup.keg.handson$data().get(1);
        helper.assertTrue(total > 0, what + " started fermenting");
        setup.keg.handson$data().set(0, total - 2);
    }

    private static String fluid(KitchenKeg keg) {
        return BuiltInRegistries.FLUID.getKey(keg.handson$fluid()).getPath();
    }

    private static int count(Player player, Item item) {
        return player.getInventory().countItem(item) + (player.inventoryMenu.getCarried().is(item) ? player.inventoryMenu.getCarried().getCount() : 0);
    }

    /**
     * Vodka: a bucket of water poured in at the opening, a potato, wheat and wheat seeds put in by a click on its page in the book,
     * fermented at the warmth of an ordinary room; four tankards drawn at the tap with Shift.
     */
    public static void vodkaFermentsFromWaterAndPotatoes(GameTestHelper helper) {
        Setup setup = keg(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer();
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        lid(setup.station, setup.logic, player, true);
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_USE, at(KegStation.MOUTH, false));
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(0, new ItemStack(Items.POTATO));
        player.getInventory().setItem(1, new ItemStack(Items.WHEAT));
        player.getInventory().setItem(2, new ItemStack(Items.WHEAT_SEEDS));
        CompoundTag fill = new CompoundTag();
        fill.putString(KegStation.DISH, "brewinandchewin:fermenting/vodka_from_water");
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_FILL, fill);
        helper.assertTrue(player.getInventory().countItem(Items.POTATO) == 0 && player.getInventory().countItem(Items.WHEAT_SEEDS) == 0,
                "the book took the potato, the wheat and the seeds");
        setup.keg.handson$feelTemperature();
        setup.logic.tick(setup.station);
        helper.assertTrue(Math.round(setup.station.state().getControl(KegStation.WANTED)) == 3,
                "the badge asks for an ordinary warmth, got " + setup.station.state().getControl(KegStation.WANTED));
        helper.runAfterDelay(2, () -> skipToTheEnd(helper, setup, "vodka"));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(fluid(setup.keg).equals("vodka"), "the water became vodka, got " + fluid(setup.keg));
            helper.assertTrue(setup.keg.handson$getItem(0).isEmpty(), "the ingredients are used up");
            player.inventoryMenu.setCarried(new ItemStack(bnc("tankard"), 4));
            setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_USE, at(KegStation.TAP, true));
            helper.assertTrue(count(player, bnc("vodka")) == 4, "four tankards of vodka with Shift, got " + count(player, bnc("vodka")));
            helper.assertTrue(setup.keg.handson$amount() == 0, "the keg is empty, got " + setup.keg.handson$amount());
            helper.succeed();
        });
    }

    /**
     * Mead: four bottles of honey poured in with Shift make its base, wheat, seeds and sweet berries put in from the book; it
     * ferments at an ordinary warmth.
     */
    public static void meadFermentsFromHoneyBottles(GameTestHelper helper) {
        Setup setup = keg(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer();
        player.inventoryMenu.setCarried(new ItemStack(Items.HONEY_BOTTLE, 4));
        lid(setup.station, setup.logic, player, true);
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_USE, at(KegStation.MOUTH, true));
        helper.assertTrue(fluid(setup.keg).equals("honey") && setup.keg.handson$amount() == 1000,
                "four bottles make a keg of honey, got " + setup.keg.handson$amount() + " of " + fluid(setup.keg));
        helper.assertTrue(count(player, Items.GLASS_BOTTLE) == 4, "the empty bottles come back, got " + count(player, Items.GLASS_BOTTLE));
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.WHEAT));
        player.getInventory().setItem(1, new ItemStack(Items.WHEAT_SEEDS));
        player.getInventory().setItem(2, new ItemStack(Items.SWEET_BERRIES));
        CompoundTag fill = new CompoundTag();
        fill.putString(KegStation.DISH, "brewinandchewin:fermenting/mead_from_honey");
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_FILL, fill);
        helper.runAfterDelay(2, () -> skipToTheEnd(helper, setup, "mead"));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(fluid(setup.keg).equals("mead"), "the honey became mead, got " + fluid(setup.keg));
            helper.succeed();
        });
    }

    /**
     * Salty folly: its base is vodka, poured back in from four tankards; sea pickle, dried kelp and seagrass dropped in by hand. It
     * wants a cool keg: at an ordinary warmth it waits, and it starts once ice stands beside the keg.
     */
    public static void saltyFollyWantsACoolKeg(GameTestHelper helper) {
        Setup setup = keg(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer();
        player.inventoryMenu.setCarried(new ItemStack(bnc("vodka"), 4));
        lid(setup.station, setup.logic, player, true);
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_USE, at(KegStation.MOUTH, true));
        helper.assertTrue(fluid(setup.keg).equals("vodka") && setup.keg.handson$amount() == 1000,
                "four tankards of vodka poured back in, got " + setup.keg.handson$amount() + " of " + fluid(setup.keg));
        setup.station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 0), new ItemStack(Items.SEA_PICKLE));
        setup.station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 1), new ItemStack(Items.DRIED_KELP));
        setup.station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 2), new ItemStack(Items.SEAGRASS));
        setup.keg.handson$feelTemperature();
        setup.logic.tick(setup.station);
        int wanted = Math.round(setup.station.state().getControl(KegStation.WANTED));
        helper.assertTrue(wanted == 2, "the badge asks for a cool keg, got " + wanted);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(setup.keg.handson$data().get(0) == 0,
                    "at a warmth of " + setup.keg.handson$data().get(2) + " it waits, got " + setup.keg.handson$data().get(0) + " ticks");
            int ice = warmTo(helper, setup, Blocks.ICE);
            helper.assertTrue(KegLogic.fitsWarmth(setup.keg.handson$data().get(2), wanted),
                    "ice beside the keg cools it, got " + setup.keg.handson$data().get(2) + " with " + ice + " blocks");
        });
        helper.runAfterDelay(26, () -> skipToTheEnd(helper, setup, "salty folly"));
        helper.runAfterDelay(34, () -> {
            helper.assertTrue(fluid(setup.keg).equals("salty_folly"), "the vodka became salty folly, got " + fluid(setup.keg));
            helper.succeed();
        });
    }

    /**
     * Jerky: no liquid at all, three raw meats dropped in; it wants a warm keg, which magma beside it gives. It comes out as three
     * pieces in the keg's output, which the station serves.
     */
    public static void jerkyDriesInAWarmKeg(GameTestHelper helper) {
        Setup setup = keg(helper);
        if (setup == null) {
            return;
        }
        setup.station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 0), new ItemStack(Items.BEEF));
        setup.station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 1), new ItemStack(Items.PORKCHOP));
        setup.station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 2), new ItemStack(Items.MUTTON));
        setup.keg.handson$feelTemperature();
        setup.logic.tick(setup.station);
        int wanted = Math.round(setup.station.state().getControl(KegStation.WANTED));
        helper.assertTrue(wanted == 4, "the badge asks for a warm keg, got " + wanted);
        int magma = warmTo(helper, setup, Blocks.MAGMA_BLOCK);
        helper.assertTrue(KegLogic.fitsWarmth(setup.keg.handson$data().get(2), wanted),
                "magma beside the keg warms it, got " + setup.keg.handson$data().get(2) + " with " + magma + " blocks");
        helper.runAfterDelay(2, () -> skipToTheEnd(helper, setup, "jerky"));
        helper.runAfterDelay(10, () -> {
            setup.logic.tick(setup.station);
            ItemStack served = setup.station.state().getItem(KegStation.SERVED_SLOT);
            helper.assertTrue(served.is(bnc("jerky")) && served.getCount() == 3, "three jerky to take at the station, got " + served);
            helper.assertTrue(setup.keg.handson$getItem(0).isEmpty(), "the meat is used up");
            helper.succeed();
        });
    }
}
