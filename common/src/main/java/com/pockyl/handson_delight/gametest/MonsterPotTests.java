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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.CookingPotStation;
import com.pockyl.handson_delight.station.KitchenPot;
import com.pockyl.handson_delight.station.PotLogic;

import java.util.List;

/**
 * Dungeons Delight's monster pot at the station: Farmer's Delight's pot in its own textures, six ingredients, heated by a spawner or
 * its own fires, cooking its own recipes of monster food with the pot's stirring and batches. Each test passes at once where Dungeons
 * Delight is not there.
 */
public final class MonsterPotTests {
    private MonsterPotTests() {
    }

    private record Setup(PotTests.TestStation station, PotLogic logic, KitchenPot pot) {
    }

    private static Item dd(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(FarmersDelightContent.DUNGEONS_DELIGHT + ":" + path));
    }

    /** A monster pot over a spawner, which heats it, with the logic the station would make for it; null without Dungeons Delight. */
    private static Setup pot(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(FarmersDelightContent.DUNGEONS_DELIGHT)) {
            helper.succeed();
            return null;
        }
        BlockPos heat = new BlockPos(1, 1, 1);
        helper.setBlock(heat, Blocks.SPAWNER);
        helper.setBlock(heat.above(), FarmersDelightContent.MONSTER_POT.get());
        BlockPos pos = helper.absolutePos(heat.above());
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, CookingPotStation.MONSTER_TYPE);
        KitchenPot pot = KitchenPot.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(pot != null, "the monster pot is a kitchen pot");
        return new Setup(station, new PotLogic(station), pot);
    }

    /** Six slots that are the pot's own, its own recipes read as Farmer's Delight's, and a spawner under it heats it. */
    public static void theMonsterPotIsAPotStation(GameTestHelper helper) {
        Setup setup = pot(helper);
        if (setup == null) {
            return;
        }
        helper.assertTrue(CookingPotStation.MONSTER_TYPE.matches(helper.getLevel().getBlockState(setup.station.pos())), "it opens as a station");
        helper.assertTrue(setup.pot.handson$slots() == 6 && setup.pot.handson$isHeated(), "six ingredients, heated by the spawner");
        setup.station.state().setItem(SlotRef.of(CookingPotStation.INGREDIENTS, 5), new ItemStack(Items.SPIDER_EYE, 2));
        helper.assertTrue(setup.pot.handson$getItem(5).is(Items.SPIDER_EYE), "the station's slots are the pot's own");
        List<RecipeHolder<CookingPotRecipe>> recipes = setup.pot.handson$recipes(helper.getLevel());
        helper.assertTrue(!recipes.isEmpty() && recipes.stream().allMatch(recipe -> recipe.id().getNamespace()
                .equals(FarmersDelightContent.DUNGEONS_DELIGHT)), "it cooks its own recipes, got " + recipes.size());
        helper.assertTrue(setup.pot.handson$findRecipe(helper.getLevel(), List.of(new ItemStack(dd("spider_meat")),
                new ItemStack(Items.SPIDER_EYE))).filter(recipe -> recipe.value().getResultItem(helper.getLevel().registryAccess())
                .is(dd("spider_extract"))).isPresent(), "spider meat and an eye make spider extract");
        helper.succeed();
    }

    public static void aStirredMonsterPotCooksTheBatch(GameTestHelper helper) {
        Setup setup = pot(helper);
        if (setup == null) {
            return;
        }
        setup.pot.handson$setItem(0, new ItemStack(dd("spider_meat"), 2));
        setup.pot.handson$setItem(1, new ItemStack(Items.SPIDER_EYE, 2));
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        for (int tick = 0; tick < 300; tick += 20) {
            helper.runAfterDelay(tick, () -> setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_STIR, new CompoundTag()));
        }
        helper.runAfterDelay(300, () -> {
            ItemStack meal = setup.pot.handson$meal();
            helper.assertTrue(meal.is(dd("spider_extract")) && meal.getCount() == 2, "both servings come out together, got " + meal);
            helper.assertTrue(setup.pot.handson$getItem(0).isEmpty() && setup.pot.handson$getItem(1).isEmpty(), "out of all of it");
            helper.succeed();
        });
    }

    /** Food a hopper brings cooks by itself, the monster pot's own way: automation goes on working. */
    public static void aHopperFedMonsterPotCooksByItself(GameTestHelper helper) {
        Setup setup = pot(helper);
        if (setup == null) {
            return;
        }
        BlockPos above = new BlockPos(1, 3, 1);
        helper.setBlock(above, Blocks.HOPPER);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getBlockEntity(above);
        hopper.setItem(0, new ItemStack(dd("spider_meat"), 1));
        hopper.setItem(1, new ItemStack(Items.SPIDER_EYE, 1));
        helper.runAfterDelay(250, () -> {
            helper.assertTrue(setup.pot.handson$meal().is(dd("spider_extract")) && setup.pot.handson$meal().getCount() == 1,
                    "one serving with nobody stirring, got " + setup.pot.handson$meal());
            helper.succeed();
        });
    }
}
