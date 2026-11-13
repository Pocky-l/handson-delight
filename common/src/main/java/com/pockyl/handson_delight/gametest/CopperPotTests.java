package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.CookingPotStation;
import com.pockyl.handson_delight.station.KitchenPot;
import com.pockyl.handson_delight.station.PotLogic;

/**
 * Miner's Delight's copper pot at the station: four ingredients, the same stirring and batches as Farmer's Delight's pot, soups in
 * copper cups, two to a serving. Each test passes at once where Miner's Delight is not there.
 */
public final class CopperPotTests {
    @SuppressWarnings("removal")
    private static final ResourceLocation CUP = new ResourceLocation(FarmersDelightContent.MINERS_DELIGHT, "copper_cup");

    private CopperPotTests() {
    }

    private record Setup(PotTests.TestStation station, PotLogic logic, KitchenPot pot) {
    }

    /** A copper pot on a lit campfire, with the logic the station would make for it; null without Miner's Delight. */
    private static Setup pot(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(FarmersDelightContent.MINERS_DELIGHT)) {
            helper.succeed();
            return null;
        }
        BlockPos fire = new BlockPos(1, 1, 1);
        helper.setBlock(fire, Blocks.CAMPFIRE);
        helper.setBlock(fire.above(), FarmersDelightContent.COPPER_POT.get());
        BlockPos pos = helper.absolutePos(fire.above());
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, CookingPotStation.COPPER_TYPE);
        KitchenPot pot = KitchenPot.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(pot != null, "the copper pot is a kitchen pot");
        return new Setup(station, new PotLogic(station), pot);
    }

    public static void theCopperPotHoldsFourIngredients(GameTestHelper helper) {
        Setup setup = pot(helper);
        if (setup == null) {
            return;
        }
        helper.assertTrue(setup.pot.handson$slots() == 4, "four ingredient slots");
        setup.station.state().setItem(SlotRef.of(CookingPotStation.INGREDIENTS, 3), new ItemStack(Items.CARROT, 2));
        helper.assertTrue(setup.pot.handson$getItem(3).is(Items.CARROT), "the station's slots are the pot's own");
        setup.pot.handson$setItem(6, new ItemStack(Items.BOWL));
        helper.assertTrue(setup.station.state().getItem(CookingPotStation.SERVED_SLOT).is(Items.BOWL), "served is the pot's output slot");
        helper.assertFalse(setup.logic.mayPlace(setup.station, helper.makeMockPlayer(), SlotRef.of(CookingPotStation.INGREDIENTS, 0),
                new ItemStack(BuiltInRegistries.ITEM.get(CUP))), "a copper cup does not go into the soup");
        helper.succeed();
    }

    public static void aStirredCopperPotCooksTheBatchInCups(GameTestHelper helper) {
        Setup setup = pot(helper);
        if (setup == null) {
            return;
        }
        setup.pot.handson$setItem(0, new ItemStack(Items.BEEF, 2));
        setup.pot.handson$setItem(1, new ItemStack(Items.CARROT, 2));
        setup.pot.handson$setItem(2, new ItemStack(Items.POTATO, 2));
        Player player = helper.makeMockPlayer();
        for (int tick = 0; tick < 300; tick += 20) {
            helper.runAfterDelay(tick, () -> setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_STIR, new CompoundTag()));
        }
        helper.runAfterDelay(300, () -> {
            ItemStack meal = setup.pot.handson$meal();
            helper.assertTrue(meal.getCount() == 4, "both servings come out together, two cups each, got " + meal);
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(meal.getItem()).getPath().endsWith("_cup"), "a stew in a copper cup, got " + meal);
            helper.assertTrue(setup.pot.handson$getItem(0).isEmpty(), "out of all the ingredients");
            helper.assertTrue(setup.pot.handson$isContainerValid(new ItemStack(BuiltInRegistries.ITEM.get(CUP))), "served in copper cups");
            helper.succeed();
        });
    }

    /** Food a hopper brings cooks by itself, the copper pot's own way: automation goes on working. */
    public static void aHopperFedCopperPotCooksByItself(GameTestHelper helper) {
        Setup setup = pot(helper);
        if (setup == null) {
            return;
        }
        BlockPos above = new BlockPos(1, 3, 1);
        helper.setBlock(above, Blocks.HOPPER);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getBlockEntity(above);
        hopper.setItem(0, new ItemStack(Items.BEEF, 1));
        hopper.setItem(1, new ItemStack(Items.CARROT, 1));
        hopper.setItem(2, new ItemStack(Items.POTATO, 1));
        helper.runAfterDelay(270, () -> {
            helper.assertTrue(setup.pot.handson$meal().getCount() == 2, "one serving, two cups, with nobody stirring, got "
                    + setup.pot.handson$meal());
            helper.succeed();
        });
    }

    /** A dish of five or six ingredients does not fit: picked in the book, nothing leaves the inventory. */
    public static void aDishOfMoreThanFourIsNotFilledIn(GameTestHelper helper) {
        Setup setup = pot(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer();
        var big = Platform.INSTANCE.cookingRecipes(helper.getLevel()).stream()
                .filter(recipe -> recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).count() > 4).findFirst();
        if (big.isEmpty()) {
            helper.succeed();
            return;
        }
        int slot = 0;
        for (var ingredient : big.get().getIngredients()) {
            if (!ingredient.isEmpty() && ingredient.getItems().length > 0) {
                player.getInventory().setItem(slot++, ingredient.getItems()[0].copyWithCount(4));
            }
        }
        CompoundTag fill = new CompoundTag();
        fill.putString(CookingPotStation.DISH, big.get().getId().toString());
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_FILL, fill);
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(setup.pot.handson$getItem(i).isEmpty(), big.get().getId() + " does not go into a pot of four");
        }
        helper.succeed();
    }
}
