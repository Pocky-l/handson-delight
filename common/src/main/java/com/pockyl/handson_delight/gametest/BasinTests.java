package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.BasinLogic;
import com.pockyl.handson_delight.station.BasinStation;
import com.pockyl.handson_delight.station.KitchenBasin;
import com.pockyl.handson_delight.station.KitchenGrill;

/**
 * Barbeque's Delight's basin at the station: food into its compartments, a stick skewering the food of one with the side of the
 * other, a recipe from the book. Each test passes at once where Barbeque's Delight is not there.
 */
public final class BasinTests {
    private BasinTests() {
    }

    private static final java.util.function.Supplier<Item> CABBAGE = () -> BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("farmersdelight", "cabbage"));

    private static Item barbeque(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(KitchenGrill.MOD_ID, path));
    }

    private record Setup(PotTests.TestStation station, BasinLogic logic, KitchenBasin basin, BlockPos pos) {
    }

    private static Setup basin(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenGrill.MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, BasinStation.BASIN.get().defaultBlockState());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, BasinStation.TYPE);
        KitchenBasin basin = KitchenBasin.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(basin != null, "the basin is a kitchen basin");
        return new Setup(station, new BasinLogic(station), basin, pos);
    }

    private static Player cook(Setup setup, GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        return player;
    }

    private static void act(Setup setup, Player player, String action, int place, int from, boolean all) {
        CompoundTag data = new CompoundTag();
        if (place >= 0) {
            data.putInt(BasinStation.PLACE, place);
        }
        if (from >= 0) {
            data.putInt(BasinStation.FROM, from);
        }
        data.putBoolean(BasinStation.ALL, all);
        setup.logic.onCustomAction(setup.station, player, action, data);
    }

    /** Ten skewering recipes; the beef one takes a stick, two beef and two cabbage. */
    public static void theBasinReadsItsRecipes(GameTestHelper helper) {
        Setup setup = basin(helper);
        if (setup == null) {
            return;
        }
        var recipes = KitchenBasin.recipes(helper.getLevel());
        helper.assertTrue(recipes.size() >= 10, "ten skewering recipes, got " + recipes.size());
        KitchenBasin.Skewering beef = recipes.stream().filter(it -> it.result().is(barbeque("raw_beef_skewer"))).findFirst().orElse(null);
        helper.assertTrue(beef != null && beef.tool().test(new ItemStack(Items.STICK)) && beef.foodCount() == 2 && beef.sideCount() == 2,
                "a beef skewer is a stick, two beef and two of a side, got " + beef);
        helper.succeed();
    }

    /**
     * Beef and cabbage into the two compartments; a stick from the inventory skewers them, the side from the other compartment and not
     * the off hand; shift makes as many as there is for.
     */
    public static void aStickSkewersTheFoodOfOneCompartmentWithTheSideOfTheOther(GameTestHelper helper) {
        Setup setup = basin(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(setup, helper);
        player.getInventory().setItem(0, new ItemStack(Items.BEEF, 5));
        player.getInventory().setItem(1, new ItemStack(CABBAGE.get(), 4));
        player.getInventory().setItem(2, new ItemStack(Items.STICK, 3));
        act(setup, player, BasinStation.ACTION_PUT, -1, 0, true);
        act(setup, player, BasinStation.ACTION_PUT, -1, 1, false);
        helper.assertTrue(setup.basin.getItem(0).is(Items.BEEF) && setup.basin.getItem(0).getCount() == 5, "all the beef went into one");
        helper.assertTrue(setup.basin.getItem(1).getCount() == 1 && player.getInventory().getItem(1).getCount() == 3,
                "one cabbage went into the other");
        act(setup, player, BasinStation.ACTION_SKEWER, -1, -1, false);
        helper.assertTrue(player.getInventory().countItem(barbeque("raw_beef_skewer")) == 0, "one cabbage is not enough");
        act(setup, player, BasinStation.ACTION_PUT, 1, 1, true);
        act(setup, player, BasinStation.ACTION_SKEWER, -1, -1, false);
        helper.assertTrue(player.getInventory().countItem(barbeque("raw_beef_skewer")) == 1, "one skewer made");
        helper.assertTrue(setup.basin.getItem(0).getCount() == 3 && setup.basin.getItem(1).getCount() == 2
                && player.getInventory().countItem(Items.STICK) == 2, "of two beef, two cabbage and a stick");
        act(setup, player, BasinStation.ACTION_SKEWER, -1, -1, true);
        helper.assertTrue(player.getInventory().countItem(barbeque("raw_beef_skewer")) == 2, "shift made the one more there was for");
        helper.succeed();
    }

    /** A recipe picked in the book brings its food and side from the inventory and skewers one with a stick from it. */
    public static void aRecipeFromTheBookFillsAndSkewers(GameTestHelper helper) {
        Setup setup = basin(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(setup, helper);
        player.getInventory().setItem(0, new ItemStack(Items.BEEF, 4));
        player.getInventory().setItem(1, new ItemStack(CABBAGE.get(), 4));
        player.getInventory().setItem(2, new ItemStack(Items.STICK, 1));
        KitchenBasin.Skewering beef = KitchenBasin.recipes(helper.getLevel()).stream()
                .filter(it -> it.result().is(barbeque("raw_beef_skewer"))).findFirst().orElseThrow();
        CompoundTag data = new CompoundTag();
        data.putString(BasinStation.DISH, beef.id().toString());
        setup.logic.onCustomAction(setup.station, player, BasinStation.ACTION_FILL, data);
        helper.assertTrue(player.getInventory().countItem(barbeque("raw_beef_skewer")) == 1, "the book made a skewer, basin " + setup.basin.getItem(0) + " " + setup.basin.getItem(1) + " inv "
                + player.getInventory().items.stream().filter(it -> !it.isEmpty()).toList());
        helper.assertTrue(player.getInventory().countItem(Items.BEEF) == 2 && player.getInventory().countItem(CABBAGE.get()) == 2,
                "of just what one takes");
        helper.succeed();
    }
}
