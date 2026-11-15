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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.StationRecipes;
import com.pockyl.handson_delight.station.KitchenOven;
import com.pockyl.handson_delight.station.OvenLogic;
import com.pockyl.handson_delight.station.OvenStation;

import java.util.Optional;

/**
 * Cultural Delights' oven at the station: things laid on the tray, recipes filled from the book, baking with the door shut over the
 * heat as the oven bakes, waiting while it is open. Each test passes at once where Cultural Delights is not there.
 */
public final class OvenTests {
    private OvenTests() {
    }

    private static Item farmers(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", path));
    }

    private static Item cultural(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(KitchenOven.MOD_ID, path));
    }

    private static final String PIE = "culturaldelights:baking/butterscotch_cinnamon_pie";

    private record Setup(PotTests.TestStation station, OvenLogic logic, KitchenOven oven, BlockPos pos) {
    }

    /** An oven on a campfire, Farmer's Delight's heat source, with the logic the station would make for it; null without the mod. */
    private static Setup oven(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenOven.MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CAMPFIRE);
        helper.setBlock(at, OvenStation.OVEN.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, OvenStation.TYPE);
        KitchenOven oven = KitchenOven.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(oven != null, "the oven is a kitchen oven");
        return new Setup(station, new OvenLogic(station), oven, pos);
    }

    private static void door(Setup setup, Player player, boolean open) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(OvenStation.OPEN, open);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_DOOR, data);
    }

    private static void fill(Setup setup, Player player, String recipe, boolean all) {
        CompoundTag data = new CompoundTag();
        data.putString(OvenStation.DISH, recipe);
        data.putBoolean(OvenStation.ALL, all);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_FILL, data);
    }

    /** Every recipe of no grid the oven has, its food laid on the tray or picked in the book: see {@link StationAudit}. */
    public static void everyLooseBakingFromItsFoodLaidOn(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenOven.MOD_ID)) {
            helper.succeed();
            return;
        }
        java.util.List<KitchenOven.Baking> loose = KitchenOven.recipes(helper.getLevel()).stream().filter(recipe -> !recipe.isShaped()).toList();
        StationAudit.audit(helper, "oven", StationRecipes.candidates(loose, KitchenOven.Baking::id, KitchenOven.Baking::ingredients),
                recipe -> null, KitchenOven.INGREDIENTS);
    }

    /**
     * The oven's recipes are read as it has them: its own pie of five things in some time. On NeoForge it bakes Farmer's Delight's
     * pies too, nine things in its grid (the Fabric build leaves those to Farmer's Delight's crafting).
     */
    public static void theOvenReadsItsRecipes(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Optional<KitchenOven.Baking> own = KitchenOven.recipes(helper.getLevel()).stream()
                .filter(recipe -> recipe.id().toString().equals(PIE)).findFirst();
        helper.assertTrue(own.isPresent() && own.get().needed().size() == 5 && own.get().ticks() > 0
                && own.get().result().is(cultural("butterscotch_cinnamon_pie")), "its own pie is read with five things and a time");
        KitchenOven.recipes(helper.getLevel()).stream().filter(recipe -> recipe.result().is(farmers("apple_pie"))).findFirst()
                .ifPresent(pie -> helper.assertTrue(pie.needed().size() == 9, "the apple pie takes nine things, got " + pie.needed().size()));
        // The oven sends what lies in it to clients (OvenMixin): its update carries its inventory.
        setup.oven.setItem(0, new ItemStack(Items.WHEAT));
        helper.assertTrue(setup.oven.blockEntity().getUpdateTag(helper.getLevel().registryAccess()).contains("inventory"),
                "the oven's update carries what lies in it");
        helper.succeed();
    }

    /**
     * The cook comes: the door opens on an oven that bakes nothing. Things go on and come off the tray only with it open; shut, nothing
     * does. Left alone, the oven shuts itself, and the block's own {@code open} follows the door.
     */
    public static void theDoorKeepsTheTrayShut(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        SlotRef first = SlotRef.of(OvenStation.INGREDIENTS, 0);
        helper.assertTrue(OvenLogic.isOpen(setup.station), "the door opens for the cook");
        helper.assertTrue(helper.getLevel().getBlockState(setup.pos).getValue(BlockStateProperties.OPEN), "the block shows it open");
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.WHEAT)), "food goes on the open tray");
        door(setup, player, false);
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.WHEAT)), "nothing goes in through the door");
        helper.assertFalse(setup.logic.mayTake(setup.station, player, first), "nothing comes out through the door");
        door(setup, player, true);
        setup.logic.onClose(setup.station, player);
        helper.assertFalse(OvenLogic.isOpen(setup.station), "the last cook to leave shuts the door");
        door(setup, player, true);
        setup.station.users.clear();
        setup.logic.tick(setup.station);
        helper.assertFalse(OvenLogic.isOpen(setup.station), "an oven left alone is shut");
        helper.assertFalse(helper.getLevel().getBlockState(setup.pos).getValue(BlockStateProperties.OPEN), "the block shows it shut");
        helper.succeed();
    }

    /**
     * Bread from wheat picked in the book: the wheat goes on the tray from the inventory; with the door open it does not bake; shut over
     * the campfire it bakes in the oven's own time into bread in the output, which the cook takes.
     */
    public static void breadBakesWithTheDoorShut(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        player.getInventory().setItem(0, new ItemStack(Items.WHEAT, 5));
        fill(setup, player, "farmersdelight:bread_from_wheat", false);
        helper.assertTrue(setup.oven.getItem(0).is(Items.WHEAT) && setup.oven.getItem(2).is(Items.WHEAT), "the book laid the wheat");
        helper.assertTrue(player.getInventory().countItem(Items.WHEAT) == 2, "one set of three from the inventory");
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(setup.oven.progress() <= 1, "nothing bakes with the door open, got " + setup.oven.progress());
            door(setup, player, false);
        });
        helper.runAfterDelay(60, () -> helper.assertTrue(setup.oven.progress() > 0, "it bakes with the door shut"));
        helper.runAfterDelay(300, () -> {
            ItemStack served = setup.station.state().getItem(OvenStation.SERVED_SLOT);
            helper.assertTrue(served.is(Items.BREAD), "bread is baked, got " + served);
            helper.assertTrue(setup.oven.getItem(0).isEmpty(), "the wheat went into it");
            helper.assertFalse(setup.logic.mayTake(setup.station, player, OvenStation.SERVED_SLOT), "the bread waits behind the door");
            door(setup, player, true);
            helper.assertTrue(setup.logic.mayTake(setup.station, player, OvenStation.SERVED_SLOT), "the open door gives it");
            helper.succeed();
        });
    }

    /**
     * A pie with shift: as many sets as the inventory has, each thing in its own place of the grid; a stack laid from the inventory
     * goes onto the same thing on the tray.
     */
    public static void aPieIsLaidOutFromTheBook(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        player.getInventory().setItem(0, new ItemStack(cultural("cinnamon"), 7));
        player.getInventory().setItem(1, new ItemStack(cultural("butter"), 2));
        player.getInventory().setItem(2, new ItemStack(farmers("pie_crust"), 3));
        fill(setup, player, PIE, true);
        int[] counts = new int[KitchenOven.INGREDIENTS];
        for (int slot = 0; slot < counts.length; slot++) {
            counts[slot] = setup.oven.getItem(slot).getCount();
        }
        helper.assertTrue(java.util.Arrays.equals(counts, new int[] {2, 2, 2, 2, 2, 0, 0, 0, 0}), "two sets in five places, got "
                + java.util.Arrays.toString(counts));
        helper.assertTrue(player.getInventory().countItem(cultural("cinnamon")) == 1 && player.getInventory().countItem(cultural("butter")) == 0,
                "the inventory gave two sets");
        Optional<KitchenOven.Baking> making = KitchenOven.matching(KitchenOven.recipes(helper.getLevel()),
                setup.station.state().getItems(OvenStation.INGREDIENTS));
        helper.assertTrue(making.isPresent() && making.get().id().toString().equals(PIE), "the tray makes the pie");
        CompoundTag lay = new CompoundTag();
        lay.putInt(OvenStation.FROM, 0);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, lay);
        helper.assertTrue(setup.oven.getItem(0).is(cultural("cinnamon")) && setup.oven.getItem(0).getCount() == 3,
                "the last cinnamon went onto the cinnamon, got " + setup.oven.getItem(0));
        helper.succeed();
    }

    /** Three cinnamon sticks laid as one stack with the butter and the crust are shared out over three places: the pie is on the tray. */
    public static void aStackIsSharedOutForItsRecipe(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        player.getInventory().setItem(0, new ItemStack(cultural("cinnamon"), 3));
        player.getInventory().setItem(1, new ItemStack(cultural("butter"), 1));
        player.getInventory().setItem(2, new ItemStack(farmers("pie_crust"), 1));
        for (int from = 0; from < 3; from++) {
            CompoundTag lay = new CompoundTag();
            lay.putInt(OvenStation.FROM, from);
            setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, lay);
        }
        Optional<KitchenOven.Baking> making = KitchenOven.matching(KitchenOven.recipes(helper.getLevel()),
                setup.station.state().getItems(OvenStation.INGREDIENTS));
        helper.assertTrue(making.isPresent() && making.get().id().toString().equals(PIE), "the stack was shared out into the pie");
        int sticks = 0;
        for (int slot = 0; slot < KitchenOven.INGREDIENTS; slot++) {
            ItemStack there = setup.oven.getItem(slot);
            sticks += there.is(cultural("cinnamon")) && there.getCount() == 1 ? 1 : 0;
        }
        // The stack keeps its place and the sticks go to free places: what lies on the tray does not move about.
        helper.assertTrue(sticks == 3, "one stick a place, three places, got " + sticks);
        helper.succeed();
    }
}
