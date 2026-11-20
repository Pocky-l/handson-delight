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
import net.minecraft.world.level.material.Fluids;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.KegLogic;
import com.pockyl.handson_delight.station.KegStation;
import com.pockyl.handson_delight.station.KitchenKeg;

import java.util.List;

/**
 * Hearth and Harvest's keg at the station: its two slots read in any order, several of a kind from one; what is poured in ferments a
 * portion at a time into a tank of its own, which the tap draws; an empty container at the opening takes back what has not fermented;
 * what ferments into a thing waits in its result slot. Each test passes at once where Hearth and Harvest is not there.
 */
public final class HearthKegTests {
    private HearthKegTests() {
    }

    @SuppressWarnings("removal")
    private static Item hearth(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(KegStation.HEARTH_MOD_ID, path));
    }

    /** Its fermenting recipe that makes {@code path}. */
    @SuppressWarnings("removal")
    private static String recipe(String path) {
        return Identifier.fromNamespaceAndPath(KegStation.HEARTH_MOD_ID, "fermenting/" + path).toString();
    }

    @SuppressWarnings("removal")
    private static String fluid(String path) {
        return Identifier.fromNamespaceAndPath(KegStation.HEARTH_MOD_ID, path).toString();
    }

    private record Setup(PotTests.TestStation station, KegLogic logic, KitchenKeg keg, BlockPos pos) {
    }

    /** A keg on stone, on its own, with the logic the station would make for it; null without Hearth and Harvest. */
    private static Setup keg(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KegStation.HEARTH_MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, KegStation.HEARTH_KEG.get());
        BlockPos pos = helper.absolutePos(at);
        helper.assertTrue(KegStation.isSingle(helper.getLevel().getBlockState(pos)), "a keg on its own is a station");
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, KegStation.HEARTH_TYPE);
        KitchenKeg keg = KitchenKeg.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(keg != null, "Hearth and Harvest's keg is a kitchen keg");
        return new Setup(station, new KegLogic(station), keg, pos);
    }

    private static void lid(Setup setup, Player player, boolean off) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(KegStation.OPEN, off);
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_LID, data);
    }

    private static void use(Setup setup, Player player, String where, boolean all) {
        CompoundTag data = new CompoundTag();
        data.putString(KegStation.AT, where);
        data.putBoolean(KegStation.ALL, all);
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_USE, data);
    }

    private static String id(net.minecraft.world.level.material.Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }

    /** Every few ticks the portion fermenting is brought near its end, so that a test sees the keg's own crafts without waiting a minute each. */
    private static void hurry(GameTestHelper helper, KitchenKeg keg, int from, int to) {
        for (int tick = from; tick < to; tick += 3) {
            helper.runAfterDelay(tick, () -> {
                int total = keg.handson$data().get(1);
                if (total > 2 && keg.handson$data().get(0) > 0) {
                    keg.handson$data().set(0, total - 2);
                }
            });
        }
    }

    /** Its recipes are read as it ferments them: a beer of two wheat and a hops in a portion of water, a wine of its juice alone. */
    public static void hearthKegReadsItsRecipes(GameTestHelper helper) {
        Setup setup = keg(helper);
        if (setup == null) {
            return;
        }
        List<KitchenKeg.Fermenting> recipes = setup.keg.handson$recipes();
        helper.assertTrue(recipes.size() >= 21, "every fermenting recipe, got " + recipes.size());
        KitchenKeg.Fermenting beer = find(recipes, recipe("beer"));
        helper.assertTrue(beer != null && beer.base().isSame(Fluids.WATER) && beer.baseAmount() == 250 && beer.ingredients().size() == 3
                && id(beer.resultFluid()).equals(fluid("beer")), "beer: a portion of water, two wheat and a hops");
        helper.assertTrue(setup.keg.handson$makes(beer, List.of(new ItemStack(Items.WHEAT, 2), new ItemStack(hearth("hops"))))
                && setup.keg.handson$makes(beer, List.of(new ItemStack(hearth("hops"), 5), new ItemStack(Items.WHEAT, 9)))
                && !setup.keg.handson$makes(beer, List.of(new ItemStack(Items.WHEAT), new ItemStack(hearth("hops")))),
                "two wheat from one slot and a hops from the other, in either order; one wheat is not enough");
        KitchenKeg.Fermenting wine = find(recipes, recipe("red_grape_wine"));
        helper.assertTrue(wine != null && wine.ingredients().isEmpty() && id(wine.base()).equals(fluid("red_grape_juice")),
                "red grape wine from its juice alone");
        helper.assertTrue(!setup.keg.handson$feelsWarmth() && !setup.keg.handson$laysOut() && setup.keg.handson$keepsDrinkApart()
                && setup.keg.handson$ingredients() == 2, "no warmth, two slots as they lie, the drink in a tank of its own");
        helper.succeed();
    }

    private static KitchenKeg.Fermenting find(List<KitchenKeg.Fermenting> recipes, String id) {
        return recipes.stream().filter(recipe -> recipe.id().toString().equals(id)).findFirst().orElse(null);
    }

    /**
     * Wine: four bottles of red grape juice pour in at the opening, the glass bottles back; under the lid the keg ferments them a portion
     * at a time into its drink's tank, as it does; with the lid off it waits. Its wine bottle holds four glasses: a glass bottle at the
     * tap takes the whole tank.
     */
    public static void hearthWineFermentsAndIsTapped(GameTestHelper helper) {
        Setup setup = keg(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = false;
        // The cook stays at it: the station keeps the lid off only for somebody there.
        setup.station.users.add(player);
        lid(setup, player, true);
        player.inventoryMenu.setCarried(new ItemStack(hearth("red_grape_juice"), 4));
        use(setup, player, KegStation.MOUTH, true);
        helper.assertTrue(id(setup.keg.handson$fluid()).equals(fluid("red_grape_juice")) && setup.keg.handson$amount() == 1000,
                "four bottles of juice poured in, got " + setup.keg.handson$amount());
        helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) + player.inventoryMenu.getCarried().getCount() == 4,
                "the four glass bottles back");
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().clearContent();
        // The station ticks while somebody is at it: with the lid off, the keg's count is held.
        setup.logic.tick(setup.station);
        for (int tick = 1; tick <= 4; tick++) {
            helper.runAfterDelay(tick, () -> setup.logic.tick(setup.station));
        }
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(setup.keg.handson$data().get(0) <= 1, "with the lid off nothing ferments, got "
                    + setup.keg.handson$data().get(0));
            lid(setup, player, false);
        });
        hurry(helper, setup.keg, 8, 60);
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(setup.keg.handson$drawnAmount() == 1000 && id(setup.keg.handson$drawn()).equals(fluid("red_grape_wine")),
                    "the juice became wine in its own tank, got " + setup.keg.handson$drawnAmount() + " of " + id(setup.keg.handson$drawn()));
            helper.assertTrue(setup.keg.handson$shownAmount() == 1000 && id(setup.keg.handson$fluid()).equals(fluid("red_grape_wine")),
                    "no juice left: the keg holds wine");
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE, 2);
            helper.assertTrue(setup.keg.handson$canTap(bottle), "a glass bottle fills at the tap");
            player.inventoryMenu.setCarried(bottle);
            use(setup, player, KegStation.TAP, false);
            helper.assertTrue(player.getInventory().countItem(hearth("red_grape_wine")) == 1 && setup.keg.handson$drawnAmount() == 0,
                    "one bottle of wine took the tank, " + setup.keg.handson$drawnAmount() + " left");
            helper.succeed();
        });
    }

    /**
     * Beer from the book: as many of each as a set takes go in from the inventory, two sets while there is wheat for two, wheat in one
     * slot and hops in the other; the book takes the lid off. A bucket of water poured in, then an empty bucket at the opening takes it
     * back, as its menu's third way does.
     */
    public static void hearthBookFillsCountsAndWaterGoesBack(GameTestHelper helper) {
        Setup setup = keg(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = false;
        player.getInventory().setItem(0, new ItemStack(Items.WHEAT, 5));
        player.getInventory().setItem(1, new ItemStack(hearth("hops"), 3));
        CompoundTag fill = new CompoundTag();
        fill.putString(KegStation.DISH, recipe("beer"));
        fill.putBoolean(KegStation.ALL, true);
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_FILL, fill);
        helper.assertTrue(KegLogic.isOpen(setup.station), "the book took the lid off");
        ItemStack wheat = setup.keg.handson$getItem(0).is(Items.WHEAT) ? setup.keg.handson$getItem(0) : setup.keg.handson$getItem(1);
        ItemStack hops = setup.keg.handson$getItem(0).is(Items.WHEAT) ? setup.keg.handson$getItem(1) : setup.keg.handson$getItem(0);
        helper.assertTrue(wheat.is(Items.WHEAT) && wheat.getCount() == 4 && hops.is(hearth("hops")) && hops.getCount() == 2,
                "two sets: four wheat and two hops, got " + setup.keg.handson$getItem(0) + ", " + setup.keg.handson$getItem(1));
        helper.assertTrue(player.getInventory().countItem(Items.WHEAT) == 1 && player.getInventory().countItem(hearth("hops")) == 1,
                "taken from the inventory");
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        use(setup, player, KegStation.MOUTH, false);
        helper.assertTrue(setup.keg.handson$fluid().isSame(Fluids.WATER) && setup.keg.handson$amount() == 1000,
                "a bucket of water poured in, got " + setup.keg.handson$amount());
        helper.assertTrue(player.inventoryMenu.getCarried().is(Items.BUCKET), "the empty bucket stays in the hand");
        helper.assertTrue(setup.keg.handson$canDrawBack(player.inventoryMenu.getCarried()) && !setup.keg.handson$canTap(new ItemStack(Items.BUCKET)),
                "an empty bucket takes the water back at the opening, and nothing at the tap");
        use(setup, player, KegStation.MOUTH, false);
        helper.assertTrue(setup.keg.handson$shownAmount() == 0 && player.inventoryMenu.getCarried().is(Items.WATER_BUCKET),
                "the water back in the bucket, " + setup.keg.handson$shownAmount() + " left");
        helper.succeed();
    }

    /**
     * Eight kegs facing east built into a big keg: none of them is a keg station on its own; every one opens the big keg's station,
     * which lives at the block low at the front on the right of a cook at its tap and hides the other seven; its tanks hold 8000 mB,
     * and water poured in there goes into the big keg's controller.
     */
    public static void hearthBigKegIsOneStation(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KegStation.HEARTH_MOD_ID)) {
            helper.succeed();
            return;
        }
        net.minecraft.world.level.block.state.BlockState facingEast = KegStation.HEARTH_KEG.get().defaultBlockState();
        for (net.minecraft.world.level.block.state.properties.Property<?> property : facingEast.getProperties()) {
            if (property.getName().equals("facing") && property.getValueClass() == net.minecraft.core.Direction.class) {
                @SuppressWarnings("unchecked")
                net.minecraft.world.level.block.state.properties.Property<net.minecraft.core.Direction> facing =
                        (net.minecraft.world.level.block.state.properties.Property<net.minecraft.core.Direction>) property;
                facingEast = facingEast.setValue(facing, net.minecraft.core.Direction.EAST);
            }
        }
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                helper.setBlock(new BlockPos(1 + dx, 1, 1 + dz), Blocks.STONE);
            }
        }
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    helper.setBlock(new BlockPos(1 + dx, 2 + dy, 1 + dz), facingEast);
                }
            }
        }
        helper.runAfterDelay(3, () -> {
            BlockPos corner = helper.absolutePos(new BlockPos(1, 2, 1));
            // Facing east, the cook stands east of it: the front's low block on the cook's right is the north-eastern one.
            BlockPos anchor = corner.offset(1, 0, 0);
            for (int dy = 0; dy <= 1; dy++) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        BlockPos cell = corner.offset(dx, dy, dz);
                        net.minecraft.world.level.block.state.BlockState state = helper.getLevel().getBlockState(cell);
                        helper.assertFalse(KegStation.HEARTH_TYPE.matches(state), "a keg of the big keg is no keg station of its own");
                        helper.assertTrue(KegStation.BIG_KEG_TYPE.matches(state), "it opens the big keg's station");
                        BlockPos resolved = KegStation.BIG_KEG_TYPE.anchor().resolve(helper.getLevel(), cell, state);
                        helper.assertTrue(resolved.equals(anchor), "every block leads to one station, " + cell + " to " + resolved);
                    }
                }
            }
            java.util.Collection<BlockPos> hidden = KegStation.BIG_KEG_TYPE.hiddenBlocks(helper.getLevel(), anchor);
            helper.assertTrue(hidden.size() == 8 && hidden.contains(anchor) && hidden.contains(corner) && hidden.contains(corner.offset(1, 1, 1)),
                    "all eight are hidden while it is used, got " + hidden);
            KitchenKeg keg = KitchenKeg.of(helper.getLevel().getBlockEntity(anchor));
            helper.assertTrue(keg != null && keg.handson$capacity() == 8000 && keg.handson$home().equals(corner),
                    "its tanks are the controller's, 8000 mB");
            PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), anchor, KegStation.BIG_KEG_TYPE);
            KegLogic logic = new KegLogic(station);
            Player player = helper.makeMockPlayer(GameType.CREATIVE);
            player.getAbilities().instabuild = false;
            station.users.add(player);
            lid(new Setup(station, logic, keg, anchor), player, true);
            player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
            use(new Setup(station, logic, keg, anchor), player, KegStation.MOUTH, false);
            KitchenKeg controller = KitchenKeg.of(helper.getLevel().getBlockEntity(corner));
            helper.assertTrue(controller != null && controller.handson$amount() == 1000 && keg.handson$amount() == 1000,
                    "the water went into the big keg, got " + keg.handson$amount());
            helper.succeed();
        });
    }

    /**
     * Beer in a big keg: a bucket of water, four wheat and two hops; under the lid it ferments two portions, 500 mB of beer beside 500
     * of water; a glass bottle dipped at the opening takes the beer, a bottle of two glasses of its four, and the next takes the water
     * back, as no beer is left.
     */
    public static void hearthBigKegBrewsBeer(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KegStation.HEARTH_MOD_ID)) {
            helper.succeed();
            return;
        }
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                helper.setBlock(new BlockPos(1 + dx, 1, 1 + dz), Blocks.STONE);
            }
        }
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    helper.setBlock(new BlockPos(1 + dx, 2 + dy, 1 + dz), KegStation.HEARTH_KEG.get());
                }
            }
        }
        BlockPos corner = helper.absolutePos(new BlockPos(1, 2, 1));
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = false;
        Setup[] holder = new Setup[1];
        helper.runAfterDelay(2, () -> {
            KitchenKeg keg = KitchenKeg.of(helper.getLevel().getBlockEntity(corner));
            helper.assertTrue(keg != null && keg.handson$capacity() == 8000, "a big keg");
            PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), corner, KegStation.BIG_KEG_TYPE);
            Setup setup = new Setup(station, new KegLogic(station), keg, corner);
            holder[0] = setup;
            station.users.add(player);
            lid(setup, player, true);
            player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
            use(setup, player, KegStation.MOUTH, false);
            station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 0), new ItemStack(Items.WHEAT, 4));
            station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 1), new ItemStack(hearth("hops"), 2));
            lid(setup, player, false);
        });
        for (int tick = 6; tick < 80; tick += 3) {
            helper.runAfterDelay(tick, () -> {
                KitchenKeg keg = holder[0].keg;
                int total = keg.handson$data().get(1);
                if (total > 2 && keg.handson$data().get(0) > 0) {
                    keg.handson$data().set(0, total - 2);
                }
            });
        }
        helper.runAfterDelay(90, () -> {
            Setup setup = holder[0];
            helper.assertTrue(setup.keg.handson$drawnAmount() == 500 && id(setup.keg.handson$drawn()).equals(fluid("beer"))
                    && setup.keg.handson$shownAmount() == 1000, "two portions of beer beside the water left, got "
                    + setup.keg.handson$drawnAmount() + " of " + id(setup.keg.handson$drawn()));
            helper.assertTrue(KegLogic.shownFluid(setup.keg) == setup.keg.handson$drawn(), "the keg looks full of beer");
            lid(setup, player, true);
            player.inventoryMenu.setCarried(new ItemStack(Items.GLASS_BOTTLE, 2));
            use(setup, player, KegStation.MOUTH, false);
            ItemStack beer = player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(hearth("beer"))).findFirst().orElse(ItemStack.EMPTY);
            helper.assertTrue(!beer.isEmpty() && setup.keg.handson$drawnAmount() == 0, "a bottle dipped in took the beer, got " + beer);
            helper.assertTrue(!beer.getComponentsPatch().isEmpty(), "an opened bottle: two glasses of four, got " + beer.getComponentsPatch());
            use(setup, player, KegStation.MOUTH, false);
            helper.assertTrue(setup.keg.handson$amount() == 250, "the next bottle took water back, " + setup.keg.handson$amount() + " left");
            helper.succeed();
        });
    }

    /** Pickles: two beetroots and a jar in brine ferment into a jar of pickled beetroots, which waits in the served slot and is taken. */
    public static void hearthPicklesWaitToBeTaken(GameTestHelper helper) {
        Setup setup = keg(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = false;
        lid(setup, player, true);
        player.inventoryMenu.setCarried(new ItemStack(hearth("brine_bottle")));
        use(setup, player, KegStation.MOUTH, false);
        helper.assertTrue(id(setup.keg.handson$fluid()).equals(fluid("brine")) && setup.keg.handson$amount() == 250,
                "a bottle of brine poured in, got " + setup.keg.handson$amount());
        setup.station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 0), new ItemStack(Items.BEETROOT, 2));
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(KegStation.INGREDIENTS, 1), new ItemStack(Items.GLASS_BOTTLE)),
                "a bottle is filled, never dropped in");
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, SlotRef.of(KegStation.INGREDIENTS, 1), new ItemStack(hearth("jar"))),
                "the jar goes in, as an ingredient");
        setup.station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 1), new ItemStack(hearth("jar")));
        lid(setup, player, false);
        hurry(helper, setup.keg, 4, 20);
        helper.runAfterDelay(26, () -> {
            ItemStack served = setup.station.state().getItem(KegStation.SERVED_SLOT);
            helper.assertTrue(served.is(hearth("pickled_beetroots")), "a jar of pickled beetroots waits, got " + served);
            helper.assertTrue(setup.keg.handson$getItem(0).isEmpty() && setup.keg.handson$getItem(1).isEmpty()
                    && setup.keg.handson$shownAmount() == 0, "the beetroots, the jar and the brine used");
            helper.assertTrue(setup.logic.mayTake(setup.station, player, KegStation.SERVED_SLOT), "it is taken");
            setup.station.state().setItem(KegStation.SERVED_SLOT, ItemStack.EMPTY);
            helper.assertTrue(setup.keg.handson$getItem(KitchenKeg.OUTPUT_SLOT).isEmpty(), "taken out of the keg's result slot");
            helper.succeed();
        });
    }
}
