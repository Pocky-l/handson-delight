package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.station.EdVatLogic;
import com.pockyl.handson_delight.station.EdVatStation;
import com.pockyl.handson_delight.station.KitchenEdVat;
import com.pockyl.handson_delight.station.StationRecipes;

import java.util.List;
import java.util.Optional;

/**
 * Extra Delight's vat at the station: its liquid poured and drawn back, its things and the thing of a stage put in by hand and from
 * the book, its two stages aged with the lid off and on as each wants, the lid lifted and put back by the station, and what is made
 * drawn into its containers. Each test passes at once where Extra Delight is not there.
 */
public final class EdVatTests {
    private static final String SAUERKRAUT = "extradelight:vat/sauerkraut_item";

    private EdVatTests() {
    }

    private static Item extra(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(KitchenEdVat.MOD_ID, path));
    }

    private record Setup(PotTests.TestStation station, EdVatLogic logic, KitchenEdVat vat, BlockPos pos) {
    }

    /** A vat on stone with air above it, with the logic the station would make for it; null without Extra Delight. */
    private static Setup vat(GameTestHelper helper) {
        if (KitchenEdVat.access() == null) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, KitchenEdVat.vat());
        // The test frame lays a barrier ceiling just above: room for the lid on top.
        helper.setBlock(at.above(), Blocks.AIR);
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, EdVatStation.TYPE);
        KitchenEdVat vat = KitchenEdVat.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(vat != null, "the vat is a kitchen vat");
        return new Setup(station, new EdVatLogic(station), vat, pos);
    }

    private static Player cook(Setup setup, GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        return player;
    }

    private static void leave(Setup setup, Player player) {
        setup.logic.onClose(setup.station, player);
        setup.station.users.remove(player);
    }

    private static void action(Setup setup, Player player, String action, int from, boolean all) {
        CompoundTag data = new CompoundTag();
        data.putInt(EdVatStation.FROM, from);
        data.putBoolean(EdVatStation.ALL, all);
        setup.logic.onCustomAction(setup.station, player, action, data);
    }

    private static void fill(Setup setup, Player player, String recipe) {
        CompoundTag data = new CompoundTag();
        data.putString(EdVatStation.DISH, recipe);
        setup.logic.onCustomAction(setup.station, player, EdVatStation.ACTION_FILL, data);
    }

    /** Sets how far the stage under way has aged, as the vat would have it after that long. */
    private static void agedTo(GameTestHelper helper, Setup setup, int ticks) {
        BlockEntity entity = setup.vat.handson$entity();
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag tag = entity.saveWithoutMetadata(registries);
        tag.putInt("cookTime", ticks);
        entity.loadWithComponents(tag, registries);
    }

    private static boolean lidOn(GameTestHelper helper, Setup setup) {
        return KitchenEdVat.lidOn(helper.getLevel(), setup.pos);
    }

    /** Every aging recipe of the vat, its food dropped in or picked in the book: see {@link StationAudit}. */
    public static void everyEdAgingFromItsFoodDroppedIn(GameTestHelper helper) {
        if (KitchenEdVat.access() == null) {
            helper.succeed();
            return;
        }
        StationAudit.audit(helper, "ed_vat", StationRecipes.candidates(KitchenEdVat.recipes(helper.getLevel()), KitchenEdVat.Aging::id,
                KitchenEdVat.Aging::ingredients), recipe -> recipe.fluid(), KitchenEdVat.INGREDIENTS);
    }

    /**
     * The vat's recipes are read as Extra Delight has them: sauerkraut of two cabbages in a bottle's worth of water, salted open to the
     * air for a while, then under the lid for a week, into two bowls. Salt is a stage's thing, a bowl a container, the lid its block.
     */
    public static void theEdVatReadsItsRecipes(GameTestHelper helper) {
        Setup setup = vat(helper);
        if (setup == null) {
            return;
        }
        List<KitchenEdVat.Aging> recipes = KitchenEdVat.recipes(helper.getLevel());
        helper.assertTrue(recipes.size() >= 23, "the vat's 23 recipes are read, got " + recipes.size());
        Optional<KitchenEdVat.Aging> kraut = recipes.stream().filter(recipe -> recipe.id().toString().equals(SAUERKRAUT)).findFirst();
        helper.assertTrue(kraut.isPresent() && kraut.get().ingredients().size() == 2 && kraut.get().takes(Fluids.WATER, 250)
                && kraut.get().container().is(Items.BOWL) && kraut.get().container().getCount() == 2, "sauerkraut is read: " + kraut);
        List<KitchenEdVat.Stage> stages = kraut.get().stages();
        helper.assertTrue(stages.size() == 2 && stages.get(0).wantsItem() && !stages.get(0).lid() && stages.get(0).ticks() == 1000
                && !stages.get(1).wantsItem() && stages.get(1).lid(), "salted open, then a week under the lid: " + stages);
        helper.assertTrue(KitchenEdVat.isStageItem(recipes, new ItemStack(extra("salt"))), "salt is a stage's thing");
        helper.assertTrue(KitchenEdVat.isContainer(recipes, new ItemStack(Items.BOWL)), "a bowl is a container");
        helper.assertTrue(KitchenEdVat.isLid(new ItemStack(KitchenEdVat.lid())), "the lid is Extra Delight's");
        helper.succeed();
    }

    /**
     * Water poured from a bucket fills the tank and the bucket comes back empty; a bottle of water adds a bottle's worth; an empty
     * bucket draws a bucketful back out.
     */
    public static void liquidIsPouredAndDrawnBack(GameTestHelper helper) {
        Setup setup = vat(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(setup, helper);
        player.getInventory().setItem(0, new ItemStack(Items.WATER_BUCKET));
        action(setup, player, EdVatStation.ACTION_PUT, 0, false);
        helper.assertTrue(setup.vat.handson$fluid().isSame(Fluids.WATER) && setup.vat.handson$amount() == 1000,
                "a bucket of water in the vat, got " + setup.vat.handson$amount());
        helper.assertTrue(player.getInventory().countItem(Items.BUCKET) == 1, "the bucket came back empty");
        helper.assertTrue(setup.station.state().getTank(EdVatStation.BREW).amount() > 0, "the water shows in the vat");
        action(setup, player, EdVatStation.ACTION_SCOOP, player.getInventory().findSlotMatchingItem(new ItemStack(Items.BUCKET)), false);
        helper.assertTrue(setup.vat.handson$amount() == 0 && player.getInventory().countItem(Items.WATER_BUCKET) == 1,
                "the water drawn back into the bucket");
        helper.succeed();
    }

    /**
     * Sauerkraut picked in the book: water, two cabbages and salt go in from the inventory, no bowls. The lid that was on the vat came
     * off for the cook; the first stage ages open to the air, so the lid goes to the cook when they leave. The salt is used up at the
     * end of it; the next stage wants the lid, which the station puts on from the cook's inventory as they leave. Done, the sauerkraut
     * waits in the vat, nothing in the output, and is drawn into the bowls the cook brings.
     */
    public static void sauerkrautAgesInItsStagesAndIsDrawnIntoBowls(GameTestHelper helper) {
        Setup setup = vat(helper);
        if (setup == null) {
            return;
        }
        helper.setBlock(new BlockPos(1, 3, 1), KitchenEdVat.lid());
        Player player = cook(setup, helper);
        helper.assertTrue(!lidOn(helper, setup) && !setup.vat.handson$lidAside().isEmpty(), "the lid came off for the cook, set aside");
        player.getInventory().setItem(0, new ItemStack(Items.WATER_BUCKET));
        player.getInventory().setItem(1, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("farmersdelight:cabbage")), 2));
        player.getInventory().setItem(2, new ItemStack(extra("salt"), 3));
        fill(setup, player, SAUERKRAUT);
        helper.assertTrue(setup.vat.handson$amount() >= 250 && setup.vat.handson$item(KitchenEdVat.STAGE_SLOT).is(extra("salt"))
                && setup.vat.handson$item(KitchenEdVat.STAGE_SLOT).getCount() == 1, "water and one salt went in");
        helper.assertTrue(setup.vat.handson$item(KitchenEdVat.CONTAINER_SLOT).isEmpty(), "no bowls went in");
        leave(setup, player);
        helper.assertFalse(lidOn(helper, setup), "the salting stage ages open: the lid stays off");
        helper.assertTrue(setup.vat.handson$lidAside().isEmpty() && player.getInventory().countItem(extra("lid")) == 1,
                "the lid went to the cook");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(setup.vat.handson$time() > 0, "the first stage ages without bowls, got " + setup.vat.handson$time());
            agedTo(helper, setup, 999);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(setup.vat.handson$stage() == 1 && setup.vat.handson$item(KitchenEdVat.STAGE_SLOT).isEmpty(),
                    "the salting stage is done and its salt used, stage " + setup.vat.handson$stage());
            Player back = cook(setup, helper);
            back.getInventory().setItem(0, new ItemStack(extra("lid")));
            leave(setup, back);
            helper.assertTrue(lidOn(helper, setup) && back.getInventory().countItem(extra("lid")) == 0,
                    "the station put the cook's lid on for the stage that wants it");
        });
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(setup.vat.handson$time() > 0, "it ages under the lid");
            agedTo(helper, setup, 167999);
        });
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(setup.vat.handson$servings().is(extra("sauerkraut_item")) && setup.vat.handson$servings().getCount() == 2,
                    "the sauerkraut waits in the vat, got " + setup.vat.handson$servings());
            helper.assertTrue(setup.vat.handson$item(KitchenEdVat.OUTPUT_SLOT).isEmpty(), "nothing in the output");
            Player cook = cook(setup, helper);
            helper.assertFalse(lidOn(helper, setup), "the lid comes off for the cook: nothing ages");
            cook.getInventory().setItem(4, new ItemStack(Items.BOWL, 3));
            action(setup, cook, EdVatStation.ACTION_TAKE, -1, true);
            helper.assertTrue(cook.getInventory().countItem(extra("sauerkraut_item")) == 0, "an empty hand draws nothing");
            action(setup, cook, EdVatStation.ACTION_TAKE, 4, true);
            helper.assertTrue(cook.getInventory().countItem(extra("sauerkraut_item")) == 2
                    && cook.getInventory().countItem(Items.BOWL) == 1,
                    "both servings drawn into two of the bowls");
            leave(setup, cook);
            helper.assertTrue(lidOn(helper, setup), "the lid goes back on behind the last cook");
            helper.succeed();
        });
    }

    /**
     * Nothing goes in or comes out through the lid: a click lifts it, and a cook carrying none cannot put one on. Containers and the
     * stage's thing never lie in the places.
     */
    public static void theEdLidIsABlockOnTheVat(GameTestHelper helper) {
        Setup setup = vat(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(setup, helper);
        SlotRef first = SlotRef.of(EdVatStation.INGREDIENTS, 0);
        ItemStack cabbage = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("farmersdelight:cabbage")));
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, first, cabbage), "a cabbage goes into the open vat");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.BOWL)), "a bowl never goes in");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, new ItemStack(extra("salt"))),
                "salt goes to the stage place");
        action(setup, player, EdVatStation.ACTION_LID, -2, false);
        helper.assertFalse(lidOn(helper, setup), "no lid to put on");
        player.getInventory().setItem(3, new ItemStack(extra("lid")));
        action(setup, player, EdVatStation.ACTION_LID, -2, false);
        helper.assertTrue(lidOn(helper, setup) && player.getInventory().countItem(extra("lid")) == 0, "the cook's lid went on");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, cabbage), "nothing goes in through the lid");
        action(setup, player, EdVatStation.ACTION_LID, -2, false);
        helper.assertTrue(!lidOn(helper, setup) && !setup.vat.handson$lidAside().isEmpty(), "a click lifts it and sets it aside");
        leave(setup, player);
        helper.assertTrue(lidOn(helper, setup) && setup.vat.handson$lidAside().isEmpty(), "nothing ages: it goes back on as it was");
        helper.succeed();
    }

    /**
     * Bowls in the vat's own container slot (its menu, a hopper) age sauerkraut Extra Delight's way into its output; servings waiting
     * in the vat go into bowls put there the same way.
     */
    public static void edContainersInTheirSlotWorkAsBefore(GameTestHelper helper) {
        Setup setup = vat(helper);
        if (setup == null) {
            return;
        }
        Item cabbage = BuiltInRegistries.ITEM.get(ResourceLocation.parse("farmersdelight:cabbage"));
        setup.vat.handson$pour(new ItemStack(Items.WATER_BUCKET));
        setup.vat.handson$setItem(0, new ItemStack(cabbage));
        setup.vat.handson$setItem(1, new ItemStack(cabbage));
        setup.vat.handson$setItem(KitchenEdVat.STAGE_SLOT, new ItemStack(extra("salt")));
        setup.vat.handson$setItem(KitchenEdVat.CONTAINER_SLOT, new ItemStack(Items.BOWL, 2));
        helper.runAfterDelay(3, () -> agedTo(helper, setup, 999));
        helper.runAfterDelay(8, () -> {
            helper.setBlock(new BlockPos(1, 3, 1), KitchenEdVat.lid());
        });
        helper.runAfterDelay(12, () -> agedTo(helper, setup, 167999));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(setup.vat.handson$item(KitchenEdVat.OUTPUT_SLOT).is(extra("sauerkraut_item"))
                    && setup.vat.handson$item(KitchenEdVat.OUTPUT_SLOT).getCount() == 2 && setup.vat.handson$servings().isEmpty(),
                    "sauerkraut in the output, got " + setup.vat.handson$item(KitchenEdVat.OUTPUT_SLOT));
            setup.vat.handson$setItem(KitchenEdVat.OUTPUT_SLOT, ItemStack.EMPTY);
            setup.vat.handson$setServings(new ItemStack(extra("sauerkraut_item"), 2));
            setup.vat.handson$setItem(KitchenEdVat.CONTAINER_SLOT, new ItemStack(Items.BOWL, 2));
        });
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(setup.vat.handson$item(KitchenEdVat.OUTPUT_SLOT).getCount() == 2 && setup.vat.handson$servings().isEmpty(),
                    "the waiting sauerkraut went into the bowls put there, got " + setup.vat.handson$item(KitchenEdVat.OUTPUT_SLOT));
            helper.succeed();
        });
    }
}
