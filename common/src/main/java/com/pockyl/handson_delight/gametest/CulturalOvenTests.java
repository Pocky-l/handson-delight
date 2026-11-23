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
import com.pockyl.handson_delight.station.CulturalOven;
import com.pockyl.handson_delight.station.KitchenOven;
import com.pockyl.handson_delight.station.OvenLogic;
import com.pockyl.handson_delight.station.OvenStation;

import java.util.Optional;

/**
 * Cultural Delights' oven at the station: things laid on the tray, recipes filled from the book, baking with the door shut over the
 * heat as the oven bakes, waiting while it is open. Each test passes at once where Cultural Delights is not there.
 */
public final class CulturalOvenTests {
    /** What is baked waits in the oven's one output slot. */
    private static final SlotRef SERVED_SLOT = SlotRef.of(OvenStation.SERVED, 0);

    private CulturalOvenTests() {
    }

    private static Item farmers(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", path));
    }

    private static Item cultural(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(CulturalOven.MOD_ID, path));
    }

    private static final String PIE = "culturaldelights:baking/butterscotch_cinnamon_pie";
    private static final String SAUSAGES_AND_MASH = "culturaldelights:baking/sausages_and_mash";

    private record Setup(PotTests.TestStation station, OvenLogic logic, CulturalOven oven, BlockPos pos) {
    }

    /** An oven on a campfire, Farmer's Delight's heat source, with the logic the station would make for it; null without the mod. */
    private static Setup oven(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(CulturalOven.MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CAMPFIRE);
        helper.setBlock(at, OvenStation.OVEN.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, OvenStation.TYPE);
        CulturalOven oven = CulturalOven.of(helper.getLevel().getBlockEntity(pos));
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
        if (!Platform.INSTANCE.isLoaded(CulturalOven.MOD_ID)) {
            helper.succeed();
            return;
        }
        java.util.List<KitchenOven.Baking> loose = CulturalOven.recipesOf(helper.getLevel()).stream().filter(recipe -> !recipe.isShaped()).toList();
        StationAudit.audit(helper, "oven", StationRecipes.candidates(loose, KitchenOven.Baking::id, KitchenOven.Baking::ingredients),
                recipe -> null, CulturalOven.PLACES);
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
        Optional<KitchenOven.Baking> own = CulturalOven.recipesOf(helper.getLevel()).stream()
                .filter(recipe -> recipe.id().toString().equals(PIE)).findFirst();
        helper.assertTrue(own.isPresent() && own.get().needed().size() == 5 && own.get().ticks() > 0
                && own.get().result().is(cultural("butterscotch_cinnamon_pie")), "its own pie is read with five things and a time");
        CulturalOven.recipesOf(helper.getLevel()).stream().filter(recipe -> recipe.result().is(farmers("apple_pie"))).findFirst()
                .ifPresent(pie -> helper.assertTrue(pie.needed().size() == 9, "the apple pie takes nine things, got " + pie.needed().size()));
        // The oven sends what lies in it to clients (OvenMixin): its update carries its inventory.
        setup.oven.setPlace(0, new ItemStack(Items.WHEAT));
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
     * Bread from wheat picked in the book: the wheat goes on the tray from the inventory; with the door open it does not bake; a moment
     * later the oven shuts it by itself, and over the campfire it bakes in the oven's own time into bread in the output, which the cook
     * takes.
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
        helper.assertTrue(setup.oven.item(0).is(Items.WHEAT) && setup.oven.item(2).is(Items.WHEAT), "the book laid the wheat");
        helper.assertTrue(player.getInventory().countItem(Items.WHEAT) == 2, "one set of three from the inventory");
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        helper.runAfterDelay(10, () -> helper.assertTrue(OvenLogic.isOpen(setup.station) && setup.oven.ticks() <= 1,
                "a moment open, nothing bakes, got " + setup.oven.ticks()));
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(OvenLogic.isOpen(setup.station), "the oven shut its door by itself");
            helper.assertTrue(setup.oven.ticks() > 0, "it bakes with the door shut");
        });
        helper.runAfterDelay(300, () -> {
            ItemStack served = setup.station.state().getItem(SERVED_SLOT);
            helper.assertTrue(served.is(Items.BREAD), "bread is baked, got " + served);
            helper.assertTrue(setup.oven.item(0).isEmpty(), "the wheat went into it");
            helper.assertFalse(setup.logic.mayTake(setup.station, player, SERVED_SLOT), "the bread waits behind the door");
            door(setup, player, true);
            helper.assertTrue(setup.logic.mayTake(setup.station, player, SERVED_SLOT), "the open door gives it");
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
        int[] counts = new int[CulturalOven.PLACES];
        for (int slot = 0; slot < counts.length; slot++) {
            counts[slot] = setup.oven.item(slot).getCount();
        }
        helper.assertTrue(java.util.Arrays.equals(counts, new int[] {2, 2, 2, 2, 2, 0, 0, 0, 0}), "two sets in five places, got "
                + java.util.Arrays.toString(counts));
        helper.assertTrue(player.getInventory().countItem(cultural("cinnamon")) == 1 && player.getInventory().countItem(cultural("butter")) == 0,
                "the inventory gave two sets");
        Optional<KitchenOven.Baking> making = KitchenOven.matching(CulturalOven.recipesOf(helper.getLevel()),
                setup.station.state().getItems(OvenStation.INGREDIENTS));
        helper.assertTrue(making.isPresent() && making.get().id().toString().equals(PIE), "the tray makes the pie");
        CompoundTag lay = new CompoundTag();
        lay.putInt(OvenStation.FROM, 0);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, lay);
        helper.assertTrue(setup.oven.item(0).is(cultural("cinnamon")) && setup.oven.item(0).getCount() == 3,
                "the last cinnamon went onto the cinnamon, got " + setup.oven.item(0));
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
        Optional<KitchenOven.Baking> making = KitchenOven.matching(CulturalOven.recipesOf(helper.getLevel()),
                setup.station.state().getItems(OvenStation.INGREDIENTS));
        helper.assertTrue(making.isPresent() && making.get().id().toString().equals(PIE), "the stack was shared out into the pie");
        int sticks = 0;
        for (int slot = 0; slot < CulturalOven.PLACES; slot++) {
            ItemStack there = setup.oven.item(slot);
            sticks += there.is(cultural("cinnamon")) && there.getCount() == 1 ? 1 : 0;
        }
        // The stack keeps its place and the sticks go to free places: what lies on the tray does not move about.
        helper.assertTrue(sticks == 3, "one stick a place, three places, got " + sticks);
        helper.succeed();
    }

    /**
     * Sausages and mash laid by hand, the stacks as they come from the inventory (two sausages, two potatoes as one stack each): the
     * oven itself bakes the dish once the door shuts, with no screen to lay things out in.
     */
    public static void sausagesAndMashLaidByHandBake(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        player.getInventory().setItem(0, new ItemStack(cultural("raw_sausage"), 2));
        player.getInventory().setItem(1, new ItemStack(Items.POTATO, 2));
        player.getInventory().setItem(2, new ItemStack(Items.BOWL, 1));
        player.getInventory().setItem(3, new ItemStack(cultural("bean_pod"), 1));
        for (int from = 0; from < 4; from++) {
            CompoundTag lay = new CompoundTag();
            lay.putInt(OvenStation.FROM, from);
            setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, lay);
        }
        StringBuilder tray = new StringBuilder();
        for (int slot = 0; slot < CulturalOven.PLACES; slot++) {
            tray.append(setup.oven.item(slot)).append(' ');
        }
        helper.assertTrue(player.getInventory().countItem(cultural("raw_sausage")) == 0 && player.getInventory().countItem(Items.BOWL) == 0,
                "everything went on the tray: " + tray);
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        helper.runAfterDelay(300, () -> {
            ItemStack served = setup.station.state().getItem(SERVED_SLOT);
            helper.assertTrue(served.is(cultural("sausages_and_mash")), "sausages and mash are baked, got " + served + "; tray was " + tray
                    + "; door open " + OvenLogic.isOpen(setup.station) + ", ticks " + setup.oven.ticks());
            helper.succeed();
        });
    }

    /**
     * Two sets of sausages and mash laid by hand with a potato over: the stacks are shared out so that the oven bakes both, one after
     * the other, without the cook laying them out again.
     */
    public static void twoSetsLaidByHandBakeBoth(GameTestHelper helper) {
        twoSetsBake(helper, false);
    }

    /** The same two sets picked in the book with Shift: both lie on the tray and both are baked. */
    public static void twoSetsFromTheBookBakeBoth(GameTestHelper helper) {
        twoSetsBake(helper, true);
    }

    private static void twoSetsBake(GameTestHelper helper, boolean fromBook) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        player.getInventory().setItem(0, new ItemStack(cultural("raw_sausage"), 4));
        player.getInventory().setItem(1, new ItemStack(Items.POTATO, 5));
        player.getInventory().setItem(2, new ItemStack(Items.BOWL, 2));
        player.getInventory().setItem(3, new ItemStack(cultural("bean_pod"), 2));
        if (fromBook) {
            fill(setup, player, SAUSAGES_AND_MASH, true);
        } else {
            for (int from = 0; from < 4; from++) {
                CompoundTag lay = new CompoundTag();
                lay.putInt(OvenStation.FROM, from);
                setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, lay);
            }
        }
        StringBuilder tray = new StringBuilder();
        for (int slot = 0; slot < CulturalOven.PLACES; slot++) {
            tray.append(setup.oven.item(slot)).append(' ');
        }
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        helper.runAfterDelay(560, () -> {
            ItemStack served = setup.station.state().getItem(SERVED_SLOT);
            StringBuilder left = new StringBuilder();
            for (int slot = 0; slot < CulturalOven.PLACES; slot++) {
                left.append(setup.oven.item(slot)).append(' ');
            }
            helper.assertTrue(served.is(cultural("sausages_and_mash")) && served.getCount() == 2, "both sets are baked, got " + served
                    + "; tray was " + tray + "; left " + left);
            helper.succeed();
        });
    }

    /**
     * Bread from an earlier bake still waits in the output when sausages and mash are laid: the oven could not bake them next to it, so
     * the cook is handed the bread as the door shuts, and the new dish bakes.
     */
    public static void aWaitingDishMakesWayForTheNext(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        setup.oven.setOutput(0, new ItemStack(Items.BREAD, 3));
        player.getInventory().setItem(0, new ItemStack(cultural("raw_sausage"), 2));
        player.getInventory().setItem(1, new ItemStack(Items.POTATO, 2));
        player.getInventory().setItem(2, new ItemStack(Items.BOWL, 1));
        player.getInventory().setItem(3, new ItemStack(cultural("bean_pod"), 1));
        for (int from = 0; from < 4; from++) {
            CompoundTag lay = new CompoundTag();
            lay.putInt(OvenStation.FROM, from);
            setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, lay);
        }
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(OvenLogic.isOpen(setup.station), "the oven shut its door by itself");
            helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 3, "the cook was handed the bread, has "
                    + player.getInventory().countItem(Items.BREAD));
        });
        helper.runAfterDelay(300, () -> {
            ItemStack served = setup.station.state().getItem(SERVED_SLOT);
            helper.assertTrue(served.is(cultural("sausages_and_mash")), "sausages and mash are baked, got " + served);
            helper.succeed();
        });
    }

    /** The cook lays the set and leaves at once while bread waits in the output: they take the bread with them, and the set bakes. */
    public static void aWaitingDishGoesWithTheCookWhoLeaves(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        setup.oven.setOutput(0, new ItemStack(Items.BREAD, 3));
        player.getInventory().setItem(0, new ItemStack(farmers("wheat_dough"), 3));
        CompoundTag lay = new CompoundTag();
        lay.putInt(OvenStation.FROM, 0);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, lay);
        // Bread on bread has room: it stays. Cookies would not fit beside it.
        helper.assertTrue(setup.oven.output(0).is(Items.BREAD), "bread waits beside bread to come");
        setup.oven.setOutput(0, new ItemStack(Items.COOKIE, 5));
        setup.logic.onClose(setup.station, player);
        setup.station.users.clear();
        helper.assertTrue(player.getInventory().countItem(Items.COOKIE) == 5, "the cook took the cookies along");
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        helper.runAfterDelay(300, () -> {
            ItemStack served = setup.station.state().getItem(SERVED_SLOT);
            helper.assertTrue(served.is(Items.BREAD), "the bread is baked, got " + served);
            helper.succeed();
        });
    }

    /**
     * Sausages and mash laid by hand in every order, from stacks of any size: whatever the order, the tray ends laid out for the
     * dish as the oven reads it.
     */
    public static void sausagesAndMashInAnyOrder(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        java.util.List<String> failures = new java.util.ArrayList<>();
        int[][] orders = permutations(4);
        for (int potatoes : new int[] {2, 3, 5, 10}) {
            for (int sausages : new int[] {2, 3, 4}) {
                for (boolean one : new boolean[] {false, true}) {
                    for (int[] order : orders) {
                        for (int slot = 0; slot <= CulturalOven.OUTPUT_SLOT; slot++) {
                            setup.oven.setPlace(slot, ItemStack.EMPTY);
                        }
                        ItemStack[] stacks = {new ItemStack(cultural("raw_sausage"), sausages), new ItemStack(Items.POTATO, potatoes),
                                new ItemStack(Items.BOWL, 1), new ItemStack(cultural("bean_pod"), 1)};
                        for (int i = 0; i < 4; i++) {
                            player.getInventory().setItem(i, stacks[i]);
                        }
                        for (int from : order) {
                            // Laid whole, or one at a time with right clicks as long as some is left.
                            do {
                                CompoundTag lay = new CompoundTag();
                                lay.putInt(OvenStation.FROM, from);
                                lay.putBoolean(OvenStation.ONE, one);
                                setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, lay);
                            } while (one && !player.getInventory().getItem(from).isEmpty());
                        }
                        Optional<KitchenOven.Baking> making = KitchenOven.matching(CulturalOven.recipesOf(helper.getLevel()),
                                setup.station.state().getItems(OvenStation.INGREDIENTS));
                        if (making.isEmpty() || !making.get().id().toString().equals(SAUSAGES_AND_MASH)) {
                            StringBuilder tray = new StringBuilder();
                            for (int slot = 0; slot < CulturalOven.PLACES; slot++) {
                                tray.append(setup.oven.item(slot)).append(' ');
                            }
                            failures.add(potatoes + " potatoes, " + sausages + " sausages, one " + one + ", order "
                                    + java.util.Arrays.toString(order) + " -> " + making.map(KitchenOven.Baking::id).orElse(null) + ": " + tray);
                        }
                    }
                }
            }
        }
        helper.assertTrue(failures.isEmpty(), failures.size() + " layouts make no sausages and mash, first: "
                + (failures.isEmpty() ? "" : String.join(" | ", failures.subList(0, Math.min(4, failures.size())))));
        helper.succeed();
    }

    /**
     * Every recipe of no grid the oven has, its food laid by hand as it comes from the inventory (alike things as one stack, one or
     * two sets, in order and reversed, whole or one at a time): the tray ends laid out for a dish of those things, as the oven reads it.
     */
    public static void everyLooseBakingLaidByHand(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        java.util.List<KitchenOven.Baking> recipes = CulturalOven.recipesOf(helper.getLevel());
        java.util.List<String> failures = new java.util.ArrayList<>();
        int tried = 0;
        for (KitchenOven.Baking recipe : recipes) {
            if (recipe.isShaped()) {
                continue;
            }
            java.util.List<ItemStack> kinds = new java.util.ArrayList<>();
            boolean cookable = true;
            for (net.minecraft.world.item.crafting.Ingredient ingredient : recipe.ingredients()) {
                ItemStack item = java.util.Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty() && !stack.is(Items.BARRIER))
                        .findFirst().orElse(ItemStack.EMPTY);
                if (item.isEmpty()) {
                    cookable = false;
                    break;
                }
                StationRecipes.add(kinds, item.copyWithCount(1));
            }
            if (!cookable) {
                continue;
            }
            // Two sets of a dish with an unstacking thing (a milk bucket) are more stacks than one set has places for: one set only.
            int most = kinds.stream().anyMatch(kind -> kind.getMaxStackSize() < 2) ? 1 : 2;
            for (int sets = 1; sets <= most; sets++) {
                for (boolean reversed : new boolean[] {false, true}) {
                    for (boolean one : new boolean[] {false, true}) {
                        tried++;
                        for (int slot = 0; slot <= CulturalOven.OUTPUT_SLOT; slot++) {
                            setup.oven.setPlace(slot, ItemStack.EMPTY);
                        }
                        player.getInventory().clearContent();
                        java.util.List<ItemStack> given = new java.util.ArrayList<>();
                        for (ItemStack kind : kinds) {
                            int left = kind.getCount() * sets;
                            while (left > 0) {
                                int part = Math.min(left, kind.getMaxStackSize());
                                given.add(kind.copyWithCount(part));
                                left -= part;
                            }
                        }
                        if (reversed) {
                            java.util.Collections.reverse(given);
                        }
                        for (int i = 0; i < given.size(); i++) {
                            player.getInventory().setItem(i, given.get(i));
                        }
                        for (int from = 0; from < given.size(); from++) {
                            int guard = 0;
                            do {
                                CompoundTag lay = new CompoundTag();
                                lay.putInt(OvenStation.FROM, from);
                                lay.putBoolean(OvenStation.ONE, one);
                                setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, lay);
                            } while (one && !player.getInventory().getItem(from).isEmpty() && ++guard < 64);
                        }
                        StringBuilder tray = new StringBuilder();
                        for (int slot = 0; slot < CulturalOven.PLACES; slot++) {
                            ItemStack there = setup.oven.item(slot);
                            tray.append(there.isEmpty() ? "-" : there.getCount() + " " + BuiltInRegistries.ITEM.getKey(there.getItem()).getPath()).append(", ");
                        }
                        boolean allIn = true;
                        for (int i = 0; i < given.size(); i++) {
                            allIn &= player.getInventory().getItem(i).isEmpty();
                        }
                        Optional<KitchenOven.Baking> making = KitchenOven.matching(recipes, setup.station.state().getItems(OvenStation.INGREDIENTS));
                        if (!allIn || making.isEmpty()) {
                            failures.add(recipe.id() + " x" + sets + (reversed ? " reversed" : "") + (one ? " one by one" : "")
                                    + (allIn ? "" : " (not all went on)") + ": " + tray);
                        }
                    }
                }
            }
        }
        failures.forEach(failure -> System.out.println("[oven by hand] " + failure));
        System.out.println("[oven by hand] " + tried + " layings, " + failures.size() + " make nothing");
        helper.assertTrue(failures.isEmpty(), failures.size() + " of " + tried + " layings make nothing, first: "
                + (failures.isEmpty() ? "" : failures.get(0)));
        helper.succeed();
    }

    /**
     * Farmer's Delight's sweet berry cheesecake (six berries, two milk, a crust) laid by hand with berries to spare, milk in buckets
     * or bottles, in any order: the berries are shared out over six places and the tray makes the cheesecake.
     */
    public static void berriesAreSharedOutForTheCheesecake(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        java.util.List<KitchenOven.Baking> recipes = CulturalOven.recipesOf(helper.getLevel());
        // Cultural Delights ships it under Farmer's Delight's name: where Farmer's Delight's own crafting recipe wins, the oven has none.
        if (recipes.stream().noneMatch(recipe -> recipe.result().is(farmers("sweet_berry_cheesecake")))) {
            System.out.println("[cheesecake] the oven has none here: Farmer's Delight's crafting recipe won");
            helper.succeed();
            return;
        }
        java.util.List<String> failures = new java.util.ArrayList<>();
        int[][] orders = permutations(3);
        for (Item milk : new Item[] {Items.MILK_BUCKET, farmers("milk_bottle")}) {
            for (int berries : new int[] {6, 7, 10, 12, 64}) {
                for (int crusts : new int[] {1, 2}) {
                    for (boolean one : new boolean[] {false, true}) {
                        for (int[] order : orders) {
                            for (int slot = 0; slot <= CulturalOven.OUTPUT_SLOT; slot++) {
                                setup.oven.setPlace(slot, ItemStack.EMPTY);
                            }
                            player.getInventory().clearContent();
                            ItemStack[] stacks = {new ItemStack(Items.SWEET_BERRIES, berries), new ItemStack(milk, 2),
                                    new ItemStack(farmers("pie_crust"), crusts)};
                            java.util.List<Integer> froms = new java.util.ArrayList<>();
                            int at = 0;
                            for (int kind : order) {
                                ItemStack stack = stacks[kind];
                                // Buckets do not stack: each is a slot of its own.
                                int each = Math.min(stack.getCount(), stack.getMaxStackSize());
                                for (int left = stack.getCount(); left > 0; left -= each) {
                                    player.getInventory().setItem(at, stack.copyWithCount(Math.min(each, left)));
                                    froms.add(at++);
                                }
                            }
                            for (int from : froms) {
                                int guard = 0;
                                do {
                                    CompoundTag lay = new CompoundTag();
                                    lay.putInt(OvenStation.FROM, from);
                                    lay.putBoolean(OvenStation.ONE, one);
                                    setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, lay);
                                } while (one && !player.getInventory().getItem(from).isEmpty() && ++guard < 64);
                            }
                            Optional<KitchenOven.Baking> making = KitchenOven.matching(recipes, setup.station.state().getItems(OvenStation.INGREDIENTS));
                            if (making.isEmpty() || !making.get().result().is(farmers("sweet_berry_cheesecake"))) {
                                StringBuilder tray = new StringBuilder();
                                for (int slot = 0; slot < CulturalOven.PLACES; slot++) {
                                    ItemStack there = setup.oven.item(slot);
                                    tray.append(there.isEmpty() ? "-" : there.getCount() + " " + BuiltInRegistries.ITEM.getKey(there.getItem()).getPath()).append(", ");
                                }
                                failures.add(BuiltInRegistries.ITEM.getKey(milk).getPath() + ", " + berries + " berries, " + crusts + " crusts"
                                        + (one ? ", one by one" : "") + ", order " + java.util.Arrays.toString(order) + ": "
                                        + making.map(KitchenOven.Baking::id).orElse(null) + " | " + tray);
                            }
                        }
                    }
                }
            }
        }
        failures.forEach(failure -> System.out.println("[cheesecake] " + failure));
        System.out.println("[cheesecake] " + orders.length * 40 + " layings, " + failures.size() + " make none");
        helper.assertTrue(failures.isEmpty(), failures.size() + " layings make no cheesecake, first: " + (failures.isEmpty() ? "" : failures.get(0)));
        helper.succeed();
    }

    private static int[][] permutations(int n) {
        java.util.List<int[]> all = new java.util.ArrayList<>();
        permute(new int[n], new boolean[n], 0, all);
        return all.toArray(new int[0][]);
    }

    private static void permute(int[] order, boolean[] used, int at, java.util.List<int[]> all) {
        if (at == order.length) {
            all.add(order.clone());
            return;
        }
        for (int i = 0; i < order.length; i++) {
            if (!used[i]) {
                used[i] = true;
                order[at] = i;
                permute(order, used, at + 1, all);
                used[i] = false;
            }
        }
    }
}
