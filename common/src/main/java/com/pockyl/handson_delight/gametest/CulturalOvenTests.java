package com.pockyl.handson_delight.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.KitchenRecipes;
import com.pockyl.handson_delight.RecipeParts;
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
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("farmersdelight", path));
    }

    private static Item cultural(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(CulturalOven.MOD_ID, path));
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
        // The oven sends what lies in it to clients (OvenMixin): its update carries its items, as its 26.3 build saves them.
        setup.oven.setPlace(0, new ItemStack(Items.WHEAT));
        helper.assertTrue(setup.oven.blockEntity().getUpdateTag(helper.getLevel().registryAccess()).contains("Items"),
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
     * Three wheat dough laid as one stack are shared out over three places: the bread from dough is on the tray, as the oven bakes it.
     */
    public static void doughIsSharedOutForBread(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        player.getInventory().setItem(0, new ItemStack(farmers("wheat_dough"), 3));
        CompoundTag lay = new CompoundTag();
        lay.putInt(OvenStation.FROM, 0);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, lay);
        int places = 0;
        for (int slot = 0; slot < CulturalOven.PLACES; slot++) {
            ItemStack there = setup.oven.item(slot);
            places += there.is(farmers("wheat_dough")) && there.getCount() == 1 ? 1 : 0;
        }
        helper.assertTrue(places == 3, "one dough a place, three places, got " + places);
        Optional<KitchenOven.Baking> making = KitchenOven.matching(CulturalOven.recipesOf(helper.getLevel()),
                setup.station.state().getItems(OvenStation.INGREDIENTS));
        helper.assertTrue(making.isPresent() && making.get().result().is(Items.BREAD), "the tray makes bread");
        helper.succeed();
    }

    /**
     * A client is sent the oven's recipes with the station's (it gets none from the game since 1.21.2), and reads them back whole: the
     * book lists sausages and mash, of six things.
     */
    public static void theClientIsSentTheOvenRecipes(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(CulturalOven.MOD_ID)) {
            helper.succeed();
            return;
        }
        KitchenRecipes.Payload sent = KitchenRecipes.payload(helper.getLevel());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        KitchenRecipes.Payload.CODEC.encode(buffer, sent);
        KitchenRecipes.Payload received = KitchenRecipes.Payload.CODEC.decode(buffer);
        Optional<RecipeHolder<?>> mash = received.recipes().stream()
                .filter(holder -> holder.id().identifier().toString().equals(SAUSAGES_AND_MASH)).findFirst();
        helper.assertTrue(mash.isPresent(), "the client is sent " + SAUSAGES_AND_MASH);
        helper.assertTrue(RecipeParts.result(mash.get().value()).is(cultural("sausages_and_mash")), "and reads what it makes");
        helper.assertTrue(RecipeParts.ingredients(mash.get().value()).size() == 6, "and its six things");
        helper.succeed();
    }
}
