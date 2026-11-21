package com.pockyl.handson_delight.gametest;

import com.lance5057.extradelight.workstations.chiller.ChillerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.ChillerLogic;
import com.pockyl.handson_delight.station.ChillerStation;
import com.pockyl.handson_delight.station.KitchenChiller;
import com.pockyl.handson_delight.station.StationRecipes;

import java.util.Arrays;
import java.util.List;

/**
 * Extra Delight's chiller at the station: what the cook brings goes where it belongs, a recipe sets by hand and from the book, its
 * liquid goes in and comes back out, and a full drip pan stops the cold until it is emptied. Each test passes at once where Extra
 * Delight is not there.
 */
public final class ChillerTests {
    private ChillerTests() {
    }

    private static Item extra(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(KitchenChiller.MOD_ID, path));
    }

    private static Item delight(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", path));
    }

    private static Fluid fluid(String path) {
        return BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath(KitchenChiller.MOD_ID, path));
    }

    /** A chiller in the test's frame, its station and a cook at it. */
    private record Setup(PotTests.TestStation station, KitchenChiller chiller, ChillerLogic logic, Player player) {
        void put(int from, boolean all) {
            CompoundTag how = new CompoundTag();
            how.putInt(ChillerStation.FROM, from);
            how.putBoolean(ChillerStation.ALL, all);
            logic.onCustomAction(station, player, ChillerStation.ACTION_PUT, how);
        }

        void pick(KitchenChiller.Setting recipe, boolean all) {
            CompoundTag how = new CompoundTag();
            how.putString(ChillerStation.DISH, recipe.id().toString());
            how.putBoolean(ChillerStation.ALL, all);
            logic.onCustomAction(station, player, ChillerStation.ACTION_FILL, how);
        }

        void pan(int from) {
            CompoundTag how = new CompoundTag();
            how.putInt(ChillerStation.FROM, from);
            logic.onCustomAction(station, player, ChillerStation.ACTION_PAN, how);
        }

        ItemStack rack() {
            return chiller.handson$item(KitchenChiller.CONTAINER_SLOT);
        }

        ItemStack made() {
            return chiller.handson$item(KitchenChiller.OUTPUT_SLOT);
        }

        int has(Item item) {
            return player.getInventory().countItem(item);
        }

        void fillPan(int amount) {
            ((ChillerBlockEntity) chiller.handson$entity()).getDripTray().fill(new FluidStack(Fluids.WATER, amount),
                    IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private static Setup chiller(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenChiller.MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, KitchenChiller.block());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, ChillerStation.TYPE);
        KitchenChiller chiller = KitchenChiller.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(chiller != null, "the chiller is a kitchen chiller");
        ChillerLogic logic = new ChillerLogic(station);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        station.users.add(player);
        logic.onOpen(station, player);
        return new Setup(station, chiller, logic, player);
    }

    private static KitchenChiller.Setting recipeOf(GameTestHelper helper, Item result, Item container) {
        return KitchenChiller.recipes(helper.getLevel()).stream()
                .filter(recipe -> recipe.result().is(result) && recipe.container().is(container)).findFirst()
                .orElseThrow(() -> new IllegalStateException("no chilling recipe for " + result));
    }

    /** Every recipe with things is laid out as itself from its things thrown in and from the book, among those of its container and liquid. */
    public static void everyChillingFromItsThingsLaidIn(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenChiller.MOD_ID)) {
            helper.succeed();
            return;
        }
        StationAudit.audit(helper, "extra delight chiller", StationRecipes.candidates(KitchenChiller.recipes(helper.getLevel()),
                KitchenChiller.Setting::id, KitchenChiller.Setting::ingredients), recipe -> List.of(recipe.container().getItem(), recipe.fluid()),
                KitchenChiller.PLACES);
    }

    /**
     * Its recipes are read with their container and liquid: a bar sets in a mold that stays, from a bottle of syrup; ice cream uses
     * its bowl up. Ice lasts as long as its kind does, and what the clients get of the chiller carries its cold and its pan.
     */
    public static void theChillerReadsItsRecipes(GameTestHelper helper) {
        Setup setup = chiller(helper);
        if (setup == null) {
            return;
        }
        List<KitchenChiller.Setting> recipes = KitchenChiller.recipes(helper.getLevel());
        helper.assertTrue(recipes.size() >= 70, "its recipes are read, got " + recipes.size());
        KitchenChiller.Setting bar = recipeOf(helper, extra("milk_chocolate_bar"), extra("bar_mold"));
        helper.assertTrue(bar.ingredients().isEmpty() && bar.fluid() == fluid("milk_chocolate_syrup_fluid") && bar.amount() == 250
                && !bar.usesUp() && bar.ticks() == 400, "a bar: a mold that stays, 250 mB of syrup, 400 ticks");
        KitchenChiller.Setting iceCream = recipeOf(helper, extra("ice_cream"), Items.BOWL);
        helper.assertTrue(iceCream.usesUp() && iceCream.ingredients().size() == 1 && iceCream.amount() == 250 && iceCream.ticks() == 800,
                "ice cream: a bowl used up, one thing, 250 mB of milk, 800 ticks");
        KitchenChiller.Access access = KitchenChiller.access();
        helper.assertTrue(access.chillOf(new ItemStack(Items.ICE)) == 100 && access.chillOf(new ItemStack(Items.PACKED_ICE)) == 1000
                && access.chillOf(new ItemStack(Items.SUGAR)) == 0, "ice lasts 100 ticks, packed ice 1000, sugar is no ice");
        helper.assertTrue(access.ices().size() >= 5 && access.ices().get(0).is(Items.BLUE_ICE), "blue ice lasts longest of "
                + access.ices().size() + " kinds");
        CompoundTag update = setup.chiller.handson$entity().getUpdateTag(helper.getLevel().registryAccess());
        helper.assertTrue(update.contains("chilltime") && update.contains("driptray") && update.contains("tank") && update.contains("cooktime"),
                "its update carries its cold, its pan, its tank and how far it has set");
        helper.succeed();
    }

    /**
     * A bar set by hand: the mold goes onto the rack, the syrup into the tank with its bottle back to the cook, the ice into the
     * chest; it sets, the mold stays, and the melt water has dripped into the pan.
     */
    public static void aBarSetsByHand(GameTestHelper helper) {
        Setup setup = chiller(helper);
        if (setup == null) {
            return;
        }
        setup.player.getInventory().setItem(0, new ItemStack(extra("bar_mold")));
        setup.player.getInventory().setItem(1, new ItemStack(extra("milk_chocolate_syrup_bottle"), 2));
        setup.player.getInventory().setItem(2, new ItemStack(Items.PACKED_ICE, 2));
        setup.put(0, true);
        helper.assertTrue(setup.rack().is(extra("bar_mold")) && setup.player.getInventory().getItem(0).isEmpty(), "the mold stands on the rack");
        helper.assertTrue(setup.station.state().data().getString(ChillerStation.MAKING).isEmpty(), "a mold alone sets nothing up");
        setup.put(1, false);
        helper.assertTrue(setup.station.state().data().getString(ChillerStation.MAKING).endsWith("milk_chocolate_bar"),
                "with its syrup the bar is set up, got " + setup.station.state().data().getString(ChillerStation.MAKING));
        helper.assertTrue(setup.chiller.handson$amount() == 250 && setup.chiller.handson$fluid() == fluid("milk_chocolate_syrup_fluid"),
                "one bottle of syrup is poured, got " + setup.chiller.handson$amount());
        helper.assertTrue(setup.has(extra("milk_chocolate_syrup_bottle")) == 1 && setup.has(Items.GLASS_BOTTLE) == 1,
                "its bottle goes back to the cook");
        setup.put(2, true);
        helper.assertTrue(setup.chiller.handson$item(KitchenChiller.ICE_SLOT).getCount() == 2, "the ice goes into the chest");
        helper.assertTrue(setup.station.state().getTank(ChillerStation.LIQUID).amount() == 100, "the mold shows full");
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(setup.chiller.handson$chill() > 0 && setup.station.state().getControl(ChillerStation.PROGRESS) > 0.0F,
                    "it sets while the ice melts");
            helper.assertTrue(setup.station.state().data().getBoolean(ChillerStation.SETS)
                    && setup.station.state().data().getInt(ChillerStation.CHILL) > 0, "the station tells its clients so");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(setup.made().is(extra("milk_chocolate_bar")), "the bar has set");
            helper.assertTrue(setup.rack().is(extra("bar_mold")) && setup.chiller.handson$amount() == 0, "the mold stays, the syrup is used");
            helper.assertTrue(setup.chiller.handson$pan() >= 400, "the melt water is in the pan, got " + setup.chiller.handson$pan());
        });
    }

    /**
     * Ice cream picked in the book: a bowl onto the rack, a bottle of milk into the tank, sugar into a place and ice into the chest,
     * all from the inventory; it sets and uses the bowl up. With shift, as many sets as the tank's milk makes.
     */
    public static void iceCreamSetsFromTheBook(GameTestHelper helper) {
        Setup setup = chiller(helper);
        if (setup == null) {
            return;
        }
        KitchenChiller.Setting iceCream = recipeOf(helper, extra("ice_cream"), Items.BOWL);
        Item sweetener = Arrays.stream(iceCream.ingredients().get(0).getItems()).map(ItemStack::getItem).findFirst().orElseThrow();
        setup.player.getInventory().setItem(0, new ItemStack(Items.BOWL, 8));
        setup.player.getInventory().setItem(1, new ItemStack(delight("milk_bottle"), 8));
        setup.player.getInventory().setItem(2, new ItemStack(sweetener, 8));
        setup.player.getInventory().setItem(3, new ItemStack(Items.SNOWBALL, 16));
        setup.player.getInventory().setItem(4, new ItemStack(Items.PACKED_ICE, 8));
        setup.pick(iceCream, false);
        helper.assertTrue(setup.rack().is(Items.BOWL) && setup.rack().getCount() == 1, "one bowl stands on the rack");
        helper.assertTrue(setup.chiller.handson$amount() == 250 && setup.has(Items.GLASS_BOTTLE) == 1, "one bottle of milk is poured");
        helper.assertTrue(setup.chiller.handson$item(0).is(sweetener) && setup.chiller.handson$item(0).getCount() == 1, "one sweetener lies in");
        // 800 ticks of cold: one piece of packed ice, where snowballs would take sixteen.
        helper.assertTrue(setup.chiller.handson$item(KitchenChiller.ICE_SLOT).is(Items.PACKED_ICE)
                && setup.chiller.handson$item(KitchenChiller.ICE_SLOT).getCount() == 1, "the ice that needs the fewest pieces goes in");
        setup.pick(iceCream, true);
        helper.assertTrue(setup.chiller.handson$amount() == 1000 && setup.rack().getCount() == 4 && setup.chiller.handson$item(0).getCount() == 4,
                "with shift four sets go in, as many as the tank holds milk for: " + setup.chiller.handson$amount() + " mB, "
                        + setup.rack().getCount() + " bowls, " + setup.chiller.handson$item(0).getCount() + " things");
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        helper.succeedWhen(() -> {
            helper.assertTrue(setup.made().is(extra("ice_cream")), "ice cream has set");
            helper.assertTrue(setup.rack().getCount() == 3 && setup.chiller.handson$amount() == 750, "it used a bowl and a bottle of milk up");
        });
    }

    /**
     * Where a container goes. An empty bottle draws the syrup back out of the tank; with a mousse set up in cream it stands on the
     * rack as its container. A container laid after another sends that one back to the cook.
     */
    public static void aContainerGoesWhereWhatIsSetUpWantsIt(GameTestHelper helper) {
        Setup setup = chiller(helper);
        if (setup == null) {
            return;
        }
        Item syrup = extra("milk_chocolate_syrup_bottle");
        setup.player.getInventory().setItem(0, new ItemStack(syrup));
        setup.player.getInventory().setItem(1, new ItemStack(Items.GLASS_BOTTLE, 4));
        setup.put(0, false);
        setup.put(1, true);
        helper.assertTrue(setup.rack().isEmpty() && setup.chiller.handson$amount() == 0 && setup.has(syrup) == 1,
                "an empty bottle draws the syrup back, and does not stand on the rack");

        KitchenChiller.Setting mousse = recipeOf(helper, extra("chocolate_mousse"), Items.GLASS_BOTTLE);
        for (int place = 0; place < mousse.ingredients().size(); place++) {
            Ingredient ingredient = mousse.ingredients().get(place);
            setup.chiller.handson$setItem(place, ingredient.getItems()[0].copyWithCount(1));
        }
        setup.player.getInventory().setItem(2, new ItemStack(extra("whipped_cream")));
        setup.put(2, false);
        helper.assertTrue(setup.chiller.handson$amount() == 250 && setup.chiller.handson$fluid() == mousse.fluid(), "the cream is poured");
        int bottles = setup.player.getInventory().findSlotMatchingItem(new ItemStack(Items.GLASS_BOTTLE));
        setup.put(bottles, true);
        helper.assertTrue(setup.rack().is(Items.GLASS_BOTTLE) && setup.rack().getCount() >= 2 && setup.chiller.handson$amount() == 250,
                "bottles stand on the rack for the mousse, the cream stays in");

        setup.player.getInventory().setItem(5, new ItemStack(extra("tray"), 3));
        setup.put(5, true);
        helper.assertTrue(setup.rack().is(extra("tray")) && setup.rack().getCount() == 3 && setup.has(Items.GLASS_BOTTLE) >= 2,
                "the trays take the rack and the bottles go back to the cook");
        helper.succeed();
    }

    /** A bottle of milk is a liquid for a bowl's ice cream and a thing for a tray's fudge. */
    public static void milkIsPouredForABowlAndLaidInForATray(GameTestHelper helper) {
        Setup setup = chiller(helper);
        if (setup == null) {
            return;
        }
        Item milk = delight("milk_bottle");
        setup.player.getInventory().setItem(0, new ItemStack(extra("tray")));
        setup.player.getInventory().setItem(1, new ItemStack(milk, 2));
        setup.put(0, true);
        setup.put(1, false);
        helper.assertTrue(setup.rack().is(extra("tray")) && setup.chiller.handson$amount() == 0 && setup.chiller.handson$item(0).is(milk),
                "with a tray on the rack the milk is laid in as a thing");
        setup.chiller.handson$setItem(0, ItemStack.EMPTY);
        setup.player.getInventory().setItem(2, new ItemStack(Items.BOWL));
        setup.put(2, true);
        setup.put(1, false);
        helper.assertTrue(setup.rack().is(Items.BOWL) && setup.chiller.handson$amount() == 250 && setup.chiller.handson$item(0).isEmpty(),
                "with a bowl on the rack the milk is poured");
        helper.succeed();
    }

    /**
     * Liquid in and out: a bucket fills the tank and comes back empty, a second liquid is turned away, a bottle draws a bottleful
     * back and a bucket only a whole bucketful.
     */
    public static void aLiquidGoesIntoTheTankAndBackOut(GameTestHelper helper) {
        Setup setup = chiller(helper);
        if (setup == null) {
            return;
        }
        setup.player.getInventory().setItem(0, new ItemStack(Items.MILK_BUCKET));
        setup.player.getInventory().setItem(1, new ItemStack(extra("milk_chocolate_syrup_bottle")));
        setup.put(0, false);
        helper.assertTrue(setup.chiller.handson$amount() == 1000 && setup.player.getInventory().getItem(0).is(Items.BUCKET),
                "the bucket of milk fills the tank and stays in its place, empty");
        setup.put(1, false);
        helper.assertTrue(setup.chiller.handson$amount() == 1000 && setup.has(extra("milk_chocolate_syrup_bottle")) == 1,
                "another liquid is not poured onto the milk");
        setup.player.getInventory().setItem(2, new ItemStack(Items.GLASS_BOTTLE, 2));
        setup.put(2, false);
        helper.assertTrue(setup.chiller.handson$amount() == 750 && setup.has(delight("milk_bottle")) == 1 && setup.has(Items.GLASS_BOTTLE) == 1,
                "a bottle draws a bottleful back");
        setup.put(0, false);
        helper.assertTrue(setup.chiller.handson$amount() == 750 && setup.has(Items.BUCKET) == 1, "a bucket draws nothing from less than a bucketful");
        setup.put(2, true);
        helper.assertTrue(setup.chiller.handson$amount() == 500 && setup.has(delight("milk_bottle")) == 2, "the last bottle draws another");
        helper.succeed();
    }

    /**
     * A full drip pan stops the cold: nothing sets while the ice goes on melting. A bucket from the cook's inventory takes the full
     * pan, and it sets again. A pan not full is tipped out by an empty hand, and a bottle draws a bottleful from it.
     */
    public static void aFullPanStopsTheColdUntilItIsEmptied(GameTestHelper helper) {
        Setup setup = chiller(helper);
        if (setup == null) {
            return;
        }
        setup.player.getInventory().setItem(0, new ItemStack(extra("bar_mold")));
        setup.player.getInventory().setItem(1, new ItemStack(extra("milk_chocolate_syrup_bottle")));
        setup.player.getInventory().setItem(2, new ItemStack(Items.PACKED_ICE, 3));
        setup.player.getInventory().setItem(3, new ItemStack(Items.BUCKET));
        setup.put(0, true);
        setup.put(1, false);
        setup.put(2, true);
        setup.fillPan(KitchenChiller.PAN);
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(setup.chiller.handson$time() == 0 && setup.made().isEmpty(), "with a full pan nothing sets");
            helper.assertTrue(setup.station.state().data().getInt(ChillerStation.PAN) == KitchenChiller.PAN, "the station tells the pan is full");
            setup.pan(-2);
            helper.assertTrue(setup.chiller.handson$pan() == 0 && setup.has(Items.WATER_BUCKET) == 1 && setup.has(Items.BUCKET) == 0,
                    "a bucket from the inventory takes the full pan");
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(setup.chiller.handson$time() > 0, "emptied, it sets again");
            int water = setup.chiller.handson$pan();
            helper.assertTrue(water > 0 && water < KitchenChiller.PAN, "and drips again, got " + water);
            setup.fillPan(300);
            setup.player.getInventory().setItem(4, new ItemStack(Items.GLASS_BOTTLE));
            setup.pan(4);
            helper.assertTrue(setup.chiller.handson$pan() == water + 300 - KitchenChiller.BOTTLE && setup.has(Items.POTION) == 1,
                    "a bottle draws a bottleful of water");
            int tipped = setup.station.state().data().getInt(ChillerStation.TIPPED);
            setup.pan(-2);
            helper.assertTrue(setup.chiller.handson$pan() == 0 && setup.station.state().data().getInt(ChillerStation.TIPPED) == tipped + 1,
                    "an empty hand tips out a pan that is not full");
        });
        helper.succeedWhen(() -> helper.assertTrue(setup.made().is(extra("milk_chocolate_bar")), "the bar sets in the end"));
    }
}
