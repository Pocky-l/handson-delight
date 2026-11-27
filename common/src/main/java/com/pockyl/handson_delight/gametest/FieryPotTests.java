package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.CookingPotStation;
import com.pockyl.handson_delight.station.KitchenPot;
import com.pockyl.handson_delight.station.PotLogic;
import com.pockyl.handson_delight.station.TwilightPot;

import java.util.List;

/**
 * Twilight's Flavor & Delight's fiery cooking pot as the pot station: Farmer's Delight's pot block entity, whose step Twilight's mod runs
 * itself for this block, heated by itself and quick with its own dishes.
 */
public final class FieryPotTests {
    /** Farmer's Delight's pot's output slot. */
    private static final int OUTPUT = 8;

    private FieryPotTests() {
    }

    private record Setup(PotTests.TestStation station, PotLogic logic, KitchenPot pot) {
    }

    /** A fiery pot on stone, nothing under it to heat it, with the logic the station would make for it; null without the addon. */
    private static Setup pot(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(FarmersDelightContent.TWILIGHT_DELIGHT)) {
            helper.succeed();
            return null;
        }
        BlockPos under = new BlockPos(1, 1, 1);
        helper.setBlock(under, Blocks.STONE);
        helper.setBlock(under.above(), FarmersDelightContent.FIERY_POT.get());
        BlockPos pos = helper.absolutePos(under.above());
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, CookingPotStation.FIERY_TYPE);
        KitchenPot pot = KitchenPot.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(pot != null, "the fiery pot is a kitchen pot");
        return new Setup(station, new PotLogic(station), pot);
    }

    private static void putIn(Setup setup, int slot, ItemStack stack) {
        SlotRef ref = SlotRef.of(CookingPotStation.INGREDIENTS, slot);
        setup.station.state().setItem(ref, stack);
        setup.logic.onSlotChanged(setup.station, ref);
    }

    /** It opens as the pot station, heats itself, holds the molten broth, and its six slots are the pot's own. */
    public static void theFieryPotIsAPotStation(GameTestHelper helper) {
        Setup setup = pot(helper);
        if (setup == null) {
            return;
        }
        helper.assertTrue(CookingPotStation.FIERY_TYPE.matches(helper.getLevel().getBlockState(setup.station.pos())), "it opens as a station");
        helper.assertTrue(!CookingPotStation.TYPE.matches(helper.getLevel().getBlockState(setup.station.pos())), "as its own, not as the cooking pot");
        helper.assertTrue(setup.pot.handson$isOurs(), "its cooking is the station's");
        helper.assertTrue(setup.pot.handson$slots() == 6 && setup.pot.handson$isHeated(), "six ingredients, heated by itself");
        setup.station.state().setItem(SlotRef.of(CookingPotStation.INGREDIENTS, 5), new ItemStack(Items.CARROT, 2));
        helper.assertTrue(setup.pot.handson$getItem(5).is(Items.CARROT), "the station's slots are the pot's own");
        setup.logic.tick(setup.station);
        helper.assertTrue(CookingPotStation.FIERY_BROTH_LIQUID.equals(setup.station.state().getTank(CookingPotStation.BROTH).main()),
                "it holds the molten broth");
        helper.succeed();
    }

    /**
     * Food a cook put in waits for the spoon though Twilight's mod runs the pot's step itself, and a stirred batch comes out whole: the
     * station's hooks in the step come before Twilight's.
     */
    public static void aStirredFieryPotWaitsAndCooksTheBatch(GameTestHelper helper) {
        Setup setup = pot(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        putIn(setup, 0, new ItemStack(Items.BEEF, 2));
        putIn(setup, 1, new ItemStack(Items.CARROT, 2));
        putIn(setup, 2, new ItemStack(Items.POTATO, 2));
        setup.logic.tick(setup.station);
        helper.runAfterDelay(40, () -> {
            setup.logic.tick(setup.station);
            helper.assertTrue(setup.station.state().getControl(CookingPotStation.PROGRESS) == 0.0F && setup.pot.handson$cookTime() == 0,
                    "left alone it waits for the spoon, got " + setup.pot.handson$cookTime() + " ticks cooked");
            for (int tick = 0; tick < 300; tick += 20) {
                helper.runAfterDelay(tick, () -> setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_STIR, new CompoundTag()));
            }
        });
        helper.runAfterDelay(340, () -> {
            ItemStack meal = setup.pot.handson$meal();
            helper.assertTrue(meal.is(BuiltInRegistries.ITEM.get(FarmersDelightContent.id("beef_stew"))) && meal.getCount() == 2,
                    "both servings come out together, got " + meal);
            helper.assertTrue(setup.pot.handson$getItem(0).isEmpty(), "out of all of it");
            helper.succeed();
        });
    }

    /** A Twilight dish of forty seconds cooks in ten here, its own way, and the book and the badge count with it. */
    public static void aLongTwilightDishCooksInTenSeconds(GameTestHelper helper) {
        Setup setup = pot(helper);
        if (setup == null) {
            return;
        }
        RecipeHolder<CookingPotRecipe> found = setup.pot.handson$recipes(helper.getLevel()).stream()
                .filter(recipe -> recipe.value().getCookTime() == 800 && recipe.id().getNamespace().equals(FarmersDelightContent.TWILIGHT_DELIGHT))
                .filter(recipe -> recipe.value().getIngredients().stream().allMatch(ingredient -> ingredient.getItems().length > 0))
                .findFirst().orElse(null);
        helper.assertTrue(found != null, "Twilight's mod has dishes of forty seconds");
        CookingPotRecipe longest = found.value();
        helper.assertTrue(TwilightPot.extraPerStep(setup.pot.handson$blockEntity(), longest, helper.getLevel().registryAccess()) == 3,
                "every step counts four times");
        List<Ingredient> ingredients = longest.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        for (int slot = 0; slot < ingredients.size(); slot++) {
            setup.pot.handson$setItem(slot, ingredients.get(slot).getItems()[0].copyWithCount(1));
        }
        ItemStack dish = longest.getResultItem(helper.getLevel().registryAccess());
        helper.runAfterDelay(240, () -> {
            // A dish without a container goes on to the pot's output slot.
            ItemStack made = setup.pot.handson$meal().isEmpty() ? setup.pot.handson$getItem(OUTPUT) : setup.pot.handson$meal();
            helper.assertTrue(ItemStack.isSameItem(made, dish), "nobody stirring, " + dish + " is done in about ten seconds, got " + made);
            helper.succeed();
        });
    }
}
