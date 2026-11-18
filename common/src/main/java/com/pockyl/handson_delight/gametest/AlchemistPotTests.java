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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.RecipeParts;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.CookingPotStation;
import com.pockyl.handson_delight.station.KitchenPot;
import com.pockyl.handson_delight.station.PotLogic;
import com.pockyl.handson_delight.station.ScrollPot;

import java.util.List;

/**
 * Farmer's Spell's alchemist pot at the station: six ingredients, its own recipes before Farmer's Delight's, an Iron's Spells scroll
 * in a slot of its own whose school some recipes need and whose fire heats the pot. Each test passes at once where Farmer's Spell is
 * not there.
 */
public final class AlchemistPotTests {
    private AlchemistPotTests() {
    }

    private record Setup(PotTests.TestStation station, PotLogic logic, KitchenPot pot, ScrollPot scrolls) {
    }

    private static Item fs(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.tryParse(FarmersDelightContent.FARMERS_SPELL + ":" + path));
    }

    /** An alchemist pot over {@code heat} (air: no fire), with the logic the station would make for it; null without Farmer's Spell. */
    private static Setup pot(GameTestHelper helper, net.minecraft.world.level.block.Block heat) {
        if (!Platform.INSTANCE.isLoaded(FarmersDelightContent.FARMERS_SPELL)) {
            helper.succeed();
            return null;
        }
        BlockPos under = new BlockPos(1, 1, 1);
        helper.setBlock(under, heat);
        helper.setBlock(under.above(), FarmersDelightContent.ALCHEMIST_POT.get());
        BlockPos pos = helper.absolutePos(under.above());
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, CookingPotStation.ALCHEMIST_TYPE);
        KitchenPot pot = KitchenPot.of(helper.getLevel().getBlockEntity(pos));
        ScrollPot scrolls = ScrollPot.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(pot != null && scrolls != null, "the alchemist pot is a kitchen pot with a scroll");
        return new Setup(station, new PotLogic(station), pot, scrolls);
    }

    private static ItemStack scroll(Setup setup, Identifier school) {
        List<ItemStack> scrolls = setup.scrolls.handson$scrollsOf(school);
        return scrolls.isEmpty() ? ItemStack.EMPTY : scrolls.get(0).copy();
    }

    private static void stir(GameTestHelper helper, Setup setup, int ticks) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        for (int tick = 0; tick < ticks; tick += 20) {
            helper.runAfterDelay(tick, () -> setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_STIR, new CompoundTag()));
        }
    }

    /** Six slots that are the pot's own, its recipes first and Farmer's Delight's after, and a campfire under it heats it. */
    public static void theAlchemistPotIsAPotStation(GameTestHelper helper) {
        Setup setup = pot(helper, Blocks.CAMPFIRE);
        if (setup == null) {
            return;
        }
        helper.assertTrue(CookingPotStation.ALCHEMIST_TYPE.matches(helper.getLevel().getBlockState(setup.station.pos())), "it opens as a station");
        helper.assertTrue(setup.pot.handson$slots() == 6 && setup.pot.handson$isHeated(), "six ingredients, heated by the campfire");
        setup.station.state().setItem(SlotRef.of(CookingPotStation.INGREDIENTS, 5), new ItemStack(Items.CARROT, 2));
        helper.assertTrue(setup.pot.handson$getItem(5).is(Items.CARROT), "the station's slots are the pot's own");
        List<CookingPotRecipe> recipes = setup.pot.handson$recipes(helper.getLevel()).stream().map(RecipeHolder::value).toList();
        helper.assertTrue(!recipes.isEmpty() && setup.scrolls.handson$isOwnRecipe(recipes.get(0))
                && recipes.stream().anyMatch(recipe -> !setup.scrolls.handson$isOwnRecipe(recipe)), "its own recipes, then Farmer's Delight's");
        helper.assertTrue(setup.pot.handson$findRecipe(helper.getLevel(), List.of(new ItemStack(fs("icy_egg"))))
                .filter(recipe -> RecipeParts.result(recipe.value()).is(fs("fired_ice_egg"))).isPresent(),
                "an icy egg fires");
        ItemStack tomatoes = new ItemStack(BuiltInRegistries.ITEM.getValue(FarmersDelightContent.id("tomato")));
        helper.assertTrue(setup.pot.handson$findRecipe(helper.getLevel(), List.of(tomatoes.copy(), tomatoes.copy())).isPresent(),
                "Farmer's Delight's tomato sauce cooks there too");
        helper.succeed();
    }

    /** A scroll on the cursor hangs over the pot, the one there before goes back to the cursor; a scroll never goes in with the food. */
    public static void aScrollHangsOverThePot(GameTestHelper helper) {
        Setup setup = pot(helper, Blocks.CAMPFIRE);
        if (setup == null) {
            return;
        }
        ItemStack ice = scroll(setup, ScrollPot.ICE);
        ItemStack holy = scroll(setup, ScrollPot.HOLY);
        helper.assertTrue(!ice.isEmpty() && !holy.isEmpty(), "Iron's Spells has ice and holy scrolls");
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(CookingPotStation.INGREDIENTS, 0), ice),
                "a scroll does not go in with the food");
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, CookingPotStation.SCROLL_SLOT, ice), "it hangs in its own place");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, CookingPotStation.SCROLL_SLOT, new ItemStack(Items.PAPER)),
                "nothing else hangs there");
        player.inventoryMenu.setCarried(ice.copy());
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_HANG, new CompoundTag());
        helper.assertTrue(ScrollPot.ICE.equals(setup.scrolls.handson$school()) && player.inventoryMenu.getCarried().isEmpty(),
                "the ice scroll hangs, the cursor is empty");
        player.inventoryMenu.setCarried(holy.copy());
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_HANG, new CompoundTag());
        helper.assertTrue(ScrollPot.HOLY.equals(setup.scrolls.handson$school()), "the holy scroll hangs instead");
        helper.assertTrue(ScrollPot.ICE.equals(setup.scrolls.handson$schoolOf(player.inventoryMenu.getCarried())),
                "and the ice scroll is back on the cursor");
        helper.succeed();
    }

    /** Wsip butter cooks only under a holy scroll: without one it waits, and hung there it cooks with the spoon. */
    public static void aSchoolRecipeWaitsForItsScroll(GameTestHelper helper) {
        Setup setup = pot(helper, Blocks.CAMPFIRE);
        if (setup == null) {
            return;
        }
        List<ItemStack> food = List.of(new ItemStack(Items.GOLD_NUGGET), new ItemStack(fs("amethyst_beetroot_seeds")),
                new ItemStack(Items.MILK_BUCKET));
        helper.assertTrue(setup.pot.handson$findRecipe(helper.getLevel(), food).isEmpty(), "nothing cooks without the holy scroll");
        helper.assertTrue(setup.scrolls.handson$waiting(helper.getLevel(), food).map(waiting -> setup.scrolls.handson$schoolFor(waiting.value()))
                .filter(ScrollPot.HOLY::equals).isPresent(), "it waits for a holy scroll");
        for (int i = 0; i < food.size(); i++) {
            setup.pot.handson$setItem(i, food.get(i).copy());
        }
        setup.scrolls.handson$setScroll(scroll(setup, ScrollPot.HOLY));
        helper.assertTrue(setup.pot.handson$findRecipe(helper.getLevel(), food).isPresent(), "with it, it cooks");
        stir(helper, setup, 200);
        helper.runAfterDelay(200, () -> {
            // Served without a container, it goes straight to the served slot.
            ItemStack served = setup.pot.handson$getItem(8);
            helper.assertTrue(served.is(fs("wsip_butter")) && served.getCount() == 4, "four wsip butters, got " + served);
            helper.assertTrue(!setup.scrolls.handson$scroll().isEmpty(), "the scroll is never used up");
            helper.succeed();
        });
    }

    /** A fire scroll heats the pot with no fire under it. */
    public static void aFireScrollHeatsThePot(GameTestHelper helper) {
        Setup setup = pot(helper, Blocks.STONE);
        if (setup == null) {
            return;
        }
        helper.assertFalse(setup.pot.handson$isHeated(), "cold on stone");
        setup.scrolls.handson$setScroll(scroll(setup, ScrollPot.FIRE));
        helper.assertTrue(setup.pot.handson$isHeated(), "hot under a fire scroll");
        helper.succeed();
    }

    /** A dish picked in the book brings a scroll of its school from the inventory, the one hanging there before goes back. */
    public static void theBookHangsTheScrollADishNeeds(GameTestHelper helper) {
        Setup setup = pot(helper, Blocks.CAMPFIRE);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        setup.scrolls.handson$setScroll(scroll(setup, ScrollPot.ICE));
        player.getInventory().add(new ItemStack(Items.GOLD_NUGGET));
        player.getInventory().add(new ItemStack(fs("amethyst_beetroot_seeds")));
        player.getInventory().add(new ItemStack(Items.MILK_BUCKET));
        player.getInventory().add(scroll(setup, ScrollPot.HOLY));
        RecipeHolder<CookingPotRecipe> recipe = setup.scrolls.handson$waiting(helper.getLevel(), List.of(new ItemStack(Items.GOLD_NUGGET),
                new ItemStack(fs("amethyst_beetroot_seeds")), new ItemStack(Items.MILK_BUCKET))).orElse(null);
        helper.assertTrue(recipe != null, "wsip butter waits for its scroll");
        CompoundTag fill = new CompoundTag();
        fill.putString(CookingPotStation.DISH, recipe.id().toString());
        setup.logic.onCustomAction(setup.station, player, CookingPotStation.ACTION_FILL, fill);
        helper.assertTrue(ScrollPot.HOLY.equals(setup.scrolls.handson$school()), "the holy scroll hangs");
        helper.assertTrue(player.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> ScrollPot.ICE.equals(setup.scrolls.handson$schoolOf(stack))),
                "the ice scroll is in the inventory");
        helper.assertTrue(setup.pot.handson$findRecipe(helper.getLevel(), List.of(setup.pot.handson$getItem(0), setup.pot.handson$getItem(1),
                setup.pot.handson$getItem(2))).isPresent(), "and the food in the pot cooks now");
        helper.succeed();
    }

    /** Food a hopper brings cooks by itself, the pot's own way: automation goes on working. */
    public static void aHopperFedAlchemistPotCooksByItself(GameTestHelper helper) {
        Setup setup = pot(helper, Blocks.CAMPFIRE);
        if (setup == null) {
            return;
        }
        BlockPos above = new BlockPos(1, 3, 1);
        helper.setBlock(above, Blocks.HOPPER);
        HopperBlockEntity hopper = helper.getBlockEntity(above, HopperBlockEntity.class);
        hopper.setItem(0, new ItemStack(fs("icy_egg"), 1));
        helper.runAfterDelay(260, () -> {
            // Served without a container, it goes straight to the served slot.
            helper.assertTrue(setup.pot.handson$meal().is(fs("fired_ice_egg")) || setup.pot.handson$getItem(8).is(fs("fired_ice_egg")),
                    "the egg fired with nobody stirring, got " + setup.pot.handson$getItem(8));
            helper.succeed();
        });
    }
}
