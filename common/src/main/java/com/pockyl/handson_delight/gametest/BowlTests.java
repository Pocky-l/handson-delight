package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.BowlLogic;
import com.pockyl.handson_delight.station.BowlStation;
import com.pockyl.handson_delight.station.KitchenBowl;
import com.pockyl.handson_delight.station.StationRecipes;

import java.util.List;
import java.util.Optional;

/**
 * Extra Delight's mixing bowl at the station: food laid out for its recipe, liquids poured and drawn back, eggs cracked for their
 * white, a whole bowlful stirred by circles of the spoon and given in containers from the inventory, the book filling food and
 * liquids. Each test passes at once where Extra Delight is not there.
 */
public final class BowlTests {
    private BowlTests() {
    }

    private static final String EGG_MIX = "extradelight:mixing/egg_mix";
    private static final Identifier MILK = Identifier.withDefaultNamespace("milk");
    private static final Identifier WATER = Identifier.withDefaultNamespace("water");

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    private record Setup(PotTests.TestStation station, BowlLogic logic, KitchenBowl bowl, BlockPos pos) {
    }

    /** A bowl on stone with the logic the station would make for it; null without the mod. */
    private static Setup bowl(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenBowl.MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, BowlStation.BOWL.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, BowlStation.TYPE);
        KitchenBowl bowl = KitchenBowl.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(bowl != null, "the bowl is a kitchen bowl");
        return new Setup(station, new BowlLogic(station), bowl, pos);
    }

    private static Player cook(Setup setup, GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        return player;
    }

    private static void put(Setup setup, Player player, int from, boolean one, boolean separate) {
        CompoundTag data = new CompoundTag();
        data.putInt(BowlStation.FROM, from);
        data.putBoolean(BowlStation.ONE, one);
        data.putBoolean(BowlStation.SEPARATE, separate);
        setup.logic.onCustomAction(setup.station, player, BowlStation.ACTION_PUT, data);
    }

    private static void fill(Setup setup, Player player, String recipe, boolean all) {
        CompoundTag data = new CompoundTag();
        data.putString(BowlStation.DISH, recipe);
        data.putBoolean(BowlStation.ALL, all);
        setup.logic.onCustomAction(setup.station, player, BowlStation.ACTION_FILL, data);
    }

    /** The spoon's end at an eighth of a circle further, near the wall, with the utensil in inventory slot {@code from}. */
    private static void stirStep(Setup setup, Player player, int step, int from) {
        float angle = step * Mth.TWO_PI / 8.0F;
        CompoundTag data = new CompoundTag();
        data.putFloat(BowlStation.X, 0.9F * Mth.cos(angle));
        data.putFloat(BowlStation.Z, 0.9F * Mth.sin(angle));
        data.putInt(BowlStation.FROM, from);
        setup.logic.onCustomAction(setup.station, player, BowlStation.ACTION_STIR, data);
    }

    /** Stirs round and round, two eighths a tick (a circle in four ticks, as fast as it counts), for {@code ticks}. */
    private static void stirFor(GameTestHelper helper, Setup setup, Player player, int from) {
        int[] step = {0};
        helper.onEachTick(() -> {
            stirStep(setup, player, step[0]++, from);
            stirStep(setup, player, step[0]++, from);
            setup.logic.tick(setup.station);
        });
    }

    private static int amount(KitchenBowl bowl, Identifier fluid) {
        return bowl.handson$liquids().stream().filter(liquid -> liquid.fluid().equals(fluid)).mapToInt(KitchenBowl.Liquid::amount).sum();
    }

    /** Every mixing recipe whose food is dropped in or picked in the book lies as itself: see {@link StationAudit}. */
    public static void everyMixingFromItsFoodDroppedIn(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenBowl.MOD_ID)) {
            helper.succeed();
            return;
        }
        // Recipes of the same food compete only when they want the same liquids: what was poured tells them apart.
        StationAudit.audit(helper, "mixing bowl", StationRecipes.candidates(KitchenBowl.recipes(helper.getLevel()), KitchenBowl.Mixing::id,
                KitchenBowl.Mixing::ingredients), recipe -> recipe.liquids().stream().map(KitchenBowl.LiquidNeed::fluids).toList(), KitchenBowl.PLACES);
    }

    /**
     * The bowl's recipes are read as it has them: egg mix of two eggs and milk into a bowl with a spoon, stiff peaks of egg white with a
     * whisk, lemonade with three bottles of water into four bottles. Eggs are food, milk a liquid, a spoon a utensil, a bowl a container.
     */
    public static void theBowlReadsItsRecipes(GameTestHelper helper) {
        Setup setup = bowl(helper);
        if (setup == null) {
            return;
        }
        List<KitchenBowl.Mixing> recipes = KitchenBowl.recipes(helper.getLevel());
        helper.assertTrue(recipes.size() > 90, "the bowl's recipes are read, got " + recipes.size());
        Optional<KitchenBowl.Mixing> eggMix = recipes.stream().filter(recipe -> recipe.id().toString().equals(EGG_MIX)).findFirst();
        helper.assertTrue(eggMix.isPresent() && eggMix.get().ingredients().size() == 2 && eggMix.get().liquids().size() == 1
                && eggMix.get().liquids().get(0).takes(MILK) && eggMix.get().container().is(Items.BOWL)
                && eggMix.get().utensil().test(new ItemStack(item("extradelight:wooden_spoon"))) && eggMix.get().stirs() == 8,
                "egg mix is read with two eggs, milk, a bowl and a spoon");
        helper.assertTrue(recipes.stream().anyMatch(recipe -> recipe.result().is(item("extradelight:stiff_peaks")) && recipe.ingredients().isEmpty()
                && recipe.utensil().test(new ItemStack(item("extradelight:whisk")))), "stiff peaks are whisked of egg white alone");
        helper.assertTrue(recipes.stream().anyMatch(recipe -> recipe.result().is(item("extradelight:lemonade")) && recipe.containersPer() == 4
                && recipe.liquids().stream().anyMatch(need -> need.takes(WATER) && need.amount() == 750)), "lemonade takes three bottles of water");
        List<KitchenBowl.Mixing> all = recipes;
        helper.assertTrue(BowlLogic.kind(all, setup.bowl, new ItemStack(Items.EGG)) == BowlLogic.Kind.FOOD, "an egg is food");
        helper.assertTrue(BowlLogic.kind(all, setup.bowl, new ItemStack(item("farmersdelight:milk_bottle"))) == BowlLogic.Kind.LIQUID,
                "a bottle of milk is poured");
        helper.assertTrue(BowlLogic.kind(all, setup.bowl, new ItemStack(item("extradelight:wooden_spoon"))) == BowlLogic.Kind.UTENSIL,
                "a spoon stirs");
        helper.assertTrue(BowlLogic.kind(all, setup.bowl, new ItemStack(Items.BOWL)) == BowlLogic.Kind.CONTAINER, "a bowl is a container");
        helper.succeed();
    }

    /**
     * A bucket of water on the cursor is poured: the bowl holds a bucketful, the empty bucket stays on the cursor. A glass bottle
     * draws a bottle of it back. Food never goes in as a liquid nor a spoon as food.
     */
    public static void aLiquidIsPouredAndDrawnBack(GameTestHelper helper) {
        Setup setup = bowl(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(setup, helper);
        player.inventoryMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        put(setup, player, -1, true, false);
        helper.assertTrue(amount(setup.bowl, WATER) == KitchenBowl.BUCKET, "a bucket of water is in the bowl, got " + setup.bowl.handson$liquids());
        helper.assertTrue(player.inventoryMenu.getCarried().is(Items.BUCKET), "the empty bucket stays in the hand");
        player.inventoryMenu.setCarried(new ItemStack(Items.GLASS_BOTTLE, 2));
        CompoundTag draw = new CompoundTag();
        draw.putInt(BowlStation.FROM, -1);
        setup.logic.onCustomAction(setup.station, player, BowlStation.ACTION_DRAW, draw);
        helper.assertTrue(amount(setup.bowl, WATER) == KitchenBowl.BUCKET - KitchenBowl.BOTTLE, "a bottle of water is drawn back");
        helper.assertTrue(player.getInventory().countItem(Items.POTION) == 1 && player.inventoryMenu.getCarried().getCount() == 1,
                "the water bottle went to the inventory, one empty bottle stays in the hand");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, com.pockyl.diegesis.api.state.SlotRef.of(BowlStation.INGREDIENTS, 0),
                new ItemStack(item("extradelight:wooden_spoon"))), "a spoon never lies in the bowl");
        helper.succeed();
    }

    /** An egg cracked for its white, as the bowl's own click cracks it: the white is in the bowl, the yolk with the cook. */
    public static void anEggIsCrackedForItsWhite(GameTestHelper helper) {
        Setup setup = bowl(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(setup, helper);
        player.getInventory().setItem(0, new ItemStack(Items.EGG, 2));
        put(setup, player, 0, true, true);
        helper.assertTrue(amount(setup.bowl, BowlLogic.EGG_WHITE) == KitchenBowl.BOTTLE, "an egg's white is in the bowl");
        helper.assertTrue(player.getInventory().countItem(item("extradelight:egg_yolk")) == 1, "its yolk went to the cook");
        helper.assertTrue(player.getInventory().countItem(Items.EGG) == 1, "one egg was cracked");
        put(setup, player, 0, false, false);
        helper.assertTrue(setup.bowl.handson$item(0).is(Items.EGG) || setup.bowl.handson$item(1).is(Items.EGG), "an egg put in lies whole");
        helper.succeed();
    }

    /**
     * Egg mix: two eggs put in as one stack lie in two places, milk is poured; stirred with the spoon from the inventory, eight circles
     * and the bowl gives the egg mix in a bowl from the inventory, using both eggs and the milk.
     */
    public static void eggMixIsStirredByCircles(GameTestHelper helper) {
        Setup setup = bowl(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(setup, helper);
        player.getInventory().setItem(0, new ItemStack(Items.EGG, 2));
        player.getInventory().setItem(1, new ItemStack(item("farmersdelight:milk_bottle")));
        player.getInventory().setItem(2, new ItemStack(item("extradelight:wooden_spoon")));
        player.getInventory().setItem(3, new ItemStack(Items.BOWL, 3));
        put(setup, player, 0, false, false);
        put(setup, player, 1, false, false);
        helper.assertTrue(setup.bowl.handson$item(0).getCount() == 1 && setup.bowl.handson$item(1).getCount() == 1, "the eggs lie in two places");
        helper.assertTrue(amount(setup.bowl, MILK) == KitchenBowl.BOTTLE, "the milk is poured");
        helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 1, "the empty bottle came back");
        helper.assertTrue(setup.logic.making(helper.getLevel()).map(recipe -> recipe.id().toString().equals(EGG_MIX)).orElse(false),
                "the bowl makes egg mix");
        stirFor(helper, setup, player, 2);
        helper.runAfterDelay(20, () -> helper.assertTrue(player.getInventory().countItem(item("extradelight:egg_mix")) == 0,
                "five circles are not enough"));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(player.getInventory().countItem(item("extradelight:egg_mix")) == 1, "eight circles made egg mix");
            helper.assertTrue(player.getInventory().countItem(Items.BOWL) == 2, "in a bowl from the inventory");
            helper.assertTrue(setup.bowl.handson$item(0).isEmpty() && setup.bowl.handson$item(1).isEmpty() && amount(setup.bowl, MILK) == 0,
                    "the eggs and the milk are used");
            helper.succeed();
        });
    }

    /**
     * Three sets of egg mix stirred at once: fourteen circles (eight times the root of three), not twenty-four. Without a bowl the
     * stirred bowlful waits and asks for one; given bowls, all three come at once.
     */
    public static void aBowlfulIsStirredAtOnceAndWaitsForItsBowls(GameTestHelper helper) {
        Setup setup = bowl(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(setup, helper);
        player.getInventory().setItem(0, new ItemStack(Items.EGG, 6));
        player.getInventory().setItem(1, new ItemStack(item("farmersdelight:milk_bottle"), 3));
        player.getInventory().setItem(2, new ItemStack(item("extradelight:wooden_spoon")));
        put(setup, player, 0, false, false);
        put(setup, player, 1, false, false);
        Optional<KitchenBowl.Mixing> making = setup.logic.making(helper.getLevel());
        helper.assertTrue(making.isPresent() && BowlLogic.stirsFor(making.get(), 3) == 14, "three sets take fourteen circles");
        stirFor(helper, setup, player, 2);
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(player.getInventory().countItem(item("extradelight:egg_mix")) == 0, "nothing without a bowl");
            helper.assertTrue(setup.station.state().data().getString(BowlStation.WANTS).equals("minecraft:bowl"), "the bowlful asks for bowls");
            player.getInventory().setItem(3, new ItemStack(Items.BOWL, 3));
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(player.getInventory().countItem(item("extradelight:egg_mix")) == 3, "all three were made, got "
                    + player.getInventory().countItem(item("extradelight:egg_mix")));
            helper.assertTrue(player.getInventory().countItem(Items.BOWL) == 0, "in the three bowls");
            helper.succeed();
        });
    }

    /** Whipped cream wants a whisk: a spoon goes round and round and the bowl counts nothing. */
    public static void theWrongUtensilStirsNothing(GameTestHelper helper) {
        Setup setup = bowl(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(setup, helper);
        player.getInventory().setItem(0, new ItemStack(item("farmersdelight:milk_bottle")));
        player.getInventory().setItem(1, new ItemStack(item("extradelight:wooden_spoon")));
        player.getInventory().setItem(2, new ItemStack(Items.BOWL));
        put(setup, player, 0, false, false);
        stirFor(helper, setup, player, 1);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(setup.bowl.handson$stirs() == 0 && player.getInventory().countItem(item("extradelight:whipped_cream")) == 0,
                    "a spoon does not whip cream");
            helper.succeed();
        });
    }

    /**
     * Egg mix picked in the book: two eggs go into their places and a bucket of milk is poured from the inventory. Stiff peaks picked:
     * an egg is cracked for its white, its yolk to the cook.
     */
    public static void theBookFillsFoodAndLiquids(GameTestHelper helper) {
        Setup setup = bowl(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(setup, helper);
        player.getInventory().setItem(0, new ItemStack(Items.EGG, 5));
        player.getInventory().setItem(1, new ItemStack(item("farmersdelight:milk_bottle"), 2));
        fill(setup, player, EGG_MIX, false);
        helper.assertTrue(setup.bowl.handson$item(0).is(Items.EGG) && setup.bowl.handson$item(1).is(Items.EGG), "two eggs went in");
        helper.assertTrue(amount(setup.bowl, MILK) == KitchenBowl.BOTTLE && player.getInventory().countItem(Items.GLASS_BOTTLE) == 1,
                "one bottle of milk was poured");
        helper.assertTrue(setup.station.state().data().getIntOr(BowlStation.POURED_COUNT, 0) == 1, "the pouring is told to the clients");
        Setup peaks = bowlBeside(helper, setup);
        Player other = cook(peaks, helper);
        other.getInventory().setItem(0, new ItemStack(Items.EGG, 2));
        Optional<KitchenBowl.Mixing> stiff = KitchenBowl.recipes(helper.getLevel()).stream()
                .filter(recipe -> recipe.result().is(item("extradelight:stiff_peaks"))).findFirst();
        helper.assertTrue(stiff.isPresent(), "stiff peaks are there");
        fill(peaks, other, stiff.get().id().toString(), false);
        helper.assertTrue(amount(peaks.bowl, BowlLogic.EGG_WHITE) == KitchenBowl.BOTTLE, "an egg was cracked for its white");
        helper.assertTrue(other.getInventory().countItem(item("extradelight:egg_yolk")) == 1 && other.getInventory().countItem(Items.EGG) == 1,
                "its yolk went to the cook");
        helper.succeed();
    }

    private static Setup bowlBeside(GameTestHelper helper, Setup first) {
        BlockPos at = new BlockPos(3, 2, 1);
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, BowlStation.BOWL.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, BowlStation.TYPE);
        KitchenBowl bowl = KitchenBowl.of(helper.getLevel().getBlockEntity(pos));
        return new Setup(station, new BowlLogic(station), bowl, pos);
    }
}
