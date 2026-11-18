package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.EdOven;
import com.pockyl.handson_delight.station.KitchenOven;
import com.pockyl.handson_delight.station.OvenLogic;
import com.pockyl.handson_delight.station.OvenStation;
import com.pockyl.handson_delight.station.StationRecipes;

import java.util.Optional;

/**
 * Extra Delight's oven at the station: its bakeware stands in it and comes back to the cook, a recipe picked in the book brings its
 * bakeware and food from the inventory, the set bakes over the heat with the bakeware pushed in and waits while it is out. Each test
 * passes at once where Extra Delight is not there.
 */
public final class EdOvenTests {
    private static final SlotRef SERVED_SLOT = SlotRef.of(OvenStation.SERVED, 0);
    private static final String COOKIES = "extradelight:cooking/oven/apple_cookie";

    private EdOvenTests() {
    }

    private static Item extra(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(EdOven.MOD_ID, path));
    }

    private record Setup(PotTests.TestStation station, OvenLogic logic, EdOven oven, BlockPos pos) {
    }

    /** An oven on a campfire, Farmer's Delight's heat source, with the logic the station would make for it; null without the mod. */
    private static Setup oven(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(EdOven.MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CAMPFIRE);
        helper.setBlock(at, OvenStation.ED_OVEN.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, OvenStation.ED_TYPE);
        EdOven oven = EdOven.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(oven != null, "the oven is a kitchen oven");
        return new Setup(station, new OvenLogic(station), oven, pos);
    }

    private static void door(Setup setup, Player player, boolean open) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(OvenStation.OPEN, open);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_DOOR, data);
    }

    private static void lay(Setup setup, Player player, int from) {
        CompoundTag data = new CompoundTag();
        data.putInt(OvenStation.FROM, from);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, data);
    }

    /**
     * Every recipe the oven has, its food laid in or picked in the book: see {@link StationAudit}. Recipes of the same food compete only
     * in the same bakeware: the bakeware standing in the oven tells them apart.
     */
    public static void everyEdBakingFromItsFoodLaidIn(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(EdOven.MOD_ID)) {
            helper.succeed();
            return;
        }
        StationAudit.audit(helper, "extra delight oven", StationRecipes.candidates(EdOven.recipesOf(helper.getLevel()), KitchenOven.Baking::id,
                KitchenOven.Baking::ingredients), recipe -> recipe.bakeware().getItem(), EdOven.PLACES);
    }

    /**
     * Its recipes are read with their bakeware: apple cookies on a sheet in 400 ticks, eight of them; the sheet is bakeware, no food;
     * its update to the clients carries how far it has baked ({@code EdOvenMixin}).
     */
    public static void theEdOvenReadsItsRecipes(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        helper.assertTrue(EdOven.recipesOf(helper.getLevel()).size() >= 500, "its recipes are read, got "
                + EdOven.recipesOf(helper.getLevel()).size());
        Optional<KitchenOven.Baking> cookies = EdOven.recipesOf(helper.getLevel()).stream()
                .filter(recipe -> recipe.id().toString().equals(COOKIES)).findFirst();
        helper.assertTrue(cookies.isPresent() && cookies.get().bakeware().is(extra("sheet")) && cookies.get().ticks() == 400
                && cookies.get().result().getCount() == 8 && !cookies.get().usesUp(), "apple cookies: on a sheet, 400 ticks, eight");
        helper.assertTrue(setup.oven.isBakeware(new ItemStack(extra("sheet"))) && !setup.oven.takes(new ItemStack(extra("sheet"))),
                "a sheet is bakeware, not food");
        helper.assertTrue(setup.oven.takes(new ItemStack(extra("apple_cookie_dough"))), "dough is food");
        helper.assertTrue(setup.oven.blockEntity().getUpdateTag(helper.getLevel().registryAccess()).contains("CookTime"),
                "its update carries how far it has baked");
        helper.succeed();
    }

    /**
     * Bakeware let go over the oven stands in it, one of the stack; other bakeware laid after it goes back to the cook. It goes in and
     * comes out only with the bakeware pulled out of the oven.
     */
    public static void bakewareStandsInTheOvenAndComesBack(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        helper.assertTrue(OvenLogic.isOpen(setup.station), "the bakeware comes out for the cook");
        player.getInventory().setItem(0, new ItemStack(extra("sheet"), 2));
        player.getInventory().setItem(1, new ItemStack(extra("pie_dish")));
        lay(setup, player, 0);
        helper.assertTrue(setup.oven.bakeware().is(extra("sheet")) && setup.oven.bakeware().getCount() == 1
                && player.getInventory().countItem(extra("sheet")) == 1, "one sheet stands in the oven");
        lay(setup, player, 1);
        helper.assertTrue(setup.oven.bakeware().is(extra("pie_dish")) && player.getInventory().countItem(extra("sheet")) == 2,
                "the pie dish stands there, the sheet came back");
        helper.assertTrue(setup.logic.mayTake(setup.station, player, OvenStation.BAKEWARE_SLOT), "the cook takes it out");
        door(setup, player, false);
        helper.assertFalse(setup.logic.mayTake(setup.station, player, OvenStation.BAKEWARE_SLOT), "not while it is in the oven");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, OvenStation.BAKEWARE_SLOT, new ItemStack(extra("sheet"))),
                "nothing goes in while it is in the oven");
        helper.succeed();
    }

    /** Dough laid with no sheet in the oven makes nothing yet: the station tells the clients the sheet it waits for. */
    public static void doughWithoutItsSheetWaitsForIt(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        player.getInventory().setItem(0, new ItemStack(extra("apple_cookie_dough")));
        lay(setup, player, 0);
        setup.logic.tick(setup.station);
        helper.assertTrue(setup.station.state().data().getString(OvenStation.WANTS).equals("extradelight:sheet"),
                "it waits for a sheet, got " + setup.station.state().data().getString(OvenStation.WANTS));
        player.getInventory().setItem(1, new ItemStack(extra("sheet")));
        lay(setup, player, 1);
        setup.logic.tick(setup.station);
        helper.assertFalse(setup.station.state().data().contains(OvenStation.WANTS), "the sheet is there");
        helper.succeed();
    }

    /**
     * A right click with a stack lays one of it, as into a crafting grid; a left click the stack. Dough laid with no sheet in the oven
     * brings the cook's sheet from the inventory, as a pick in the book does.
     */
    public static void aRightClickLaysOneAndTheDoughBringsItsSheet(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        player.getInventory().setItem(5, new ItemStack(extra("sheet")));
        player.inventoryMenu.setCarried(new ItemStack(extra("apple_cookie_dough"), 3));
        CompoundTag one = new CompoundTag();
        one.putBoolean(OvenStation.ONE, true);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, one);
        int laid = 0;
        for (int place = 0; place < setup.oven.places(); place++) {
            laid += setup.oven.place(place).getCount();
        }
        helper.assertTrue(laid == 1 && player.inventoryMenu.getCarried().getCount() == 2, "one dough laid, two kept, got " + laid);
        helper.assertTrue(setup.oven.bakeware().is(extra("sheet")) && player.getInventory().countItem(extra("sheet")) == 0,
                "the dough brought its sheet from the inventory");
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, new CompoundTag());
        helper.assertTrue(player.inventoryMenu.getCarried().isEmpty(), "a left click lays the rest");
        helper.succeed();
    }

    /**
     * Apple cookies picked in the book: the sheet and the dough go in from the inventory; the sheet stays out a moment, then the oven
     * takes it in by itself; over the campfire the dough bakes in the recipe's time into eight cookies, which wait in the oven until it is
     * pulled out. Opened by hand with a set still there, it stays open.
     */
    public static void cookiesBakeWithTheSheetPushedIn(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        player.getInventory().setItem(0, new ItemStack(extra("apple_cookie_dough"), 3));
        player.getInventory().setItem(5, new ItemStack(extra("sheet")));
        CompoundTag fill = new CompoundTag();
        fill.putString(OvenStation.DISH, COOKIES);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_FILL, fill);
        helper.assertTrue(setup.oven.bakeware().is(extra("sheet")) && player.getInventory().countItem(extra("sheet")) == 0,
                "the book stood the sheet in the oven");
        helper.assertTrue(player.getInventory().countItem(extra("apple_cookie_dough")) == 2, "one dough went in");
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        helper.runAfterDelay(10, () -> {
            float baked = setup.oven.progress(0);
            helper.assertTrue(OvenLogic.isOpen(setup.station) && baked < 0.01F, "a moment out, nothing bakes, got " + baked);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(OvenLogic.isOpen(setup.station), "the oven took the sheet in by itself");
            helper.assertTrue(setup.station.state().getControl(OvenStation.PROGRESS) > 0.0F, "it bakes with the sheet in");
        });
        helper.runAfterDelay(500, () -> {
            ItemStack served = setup.station.state().getItem(SERVED_SLOT);
            helper.assertTrue(served.is(extra("apple_cookie")) && served.getCount() == 8, "eight cookies are baked, got " + served);
            helper.assertTrue(setup.oven.bakeware().is(extra("sheet")), "the sheet stays in the oven");
            helper.assertFalse(setup.logic.mayTake(setup.station, player, SERVED_SLOT), "the cookies wait in the oven");
            door(setup, player, true);
            helper.assertTrue(setup.logic.mayTake(setup.station, player, SERVED_SLOT), "pulled out, they are given");
            helper.succeed();
        });
    }
}
