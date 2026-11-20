package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.StationRecipes;
import com.pockyl.handson_delight.station.KitchenVat;
import com.pockyl.handson_delight.station.VatLogic;
import com.pockyl.handson_delight.station.VatStation;

import java.util.List;
import java.util.Optional;

/**
 * Cultural Delights' vat at the station: things put in by hand and from the book, containers on the rim, aging with the lid shut at
 * the vat's warmth as the vat ages, waiting while it is open. Each test passes at once where Cultural Delights is not there.
 */
public final class VatTests {
    private VatTests() {
    }

    private static Item cultural(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(KitchenVat.MOD_ID, path));
    }

    private static final String BEER = "culturaldelights:aging/fermenting/beer";

    private record Setup(PotTests.TestStation station, VatLogic logic, KitchenVat vat, BlockPos pos) {
    }

    /** A vat standing on its bottom on stone, neither heated nor cooled, with the logic the station would make for it; null without the mod. */
    private static Setup vat(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenVat.MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.STONE);
        // As a player places it: standing, mild, shut.
        BlockState placed = VatStation.VAT.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
        for (net.minecraft.world.level.block.state.properties.Property<?> property : placed.getProperties()) {
            if (property.getName().equals("temperature")) {
                placed = withValue(placed, property, "normal");
            }
        }
        helper.setBlock(at, placed);
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, VatStation.TYPE);
        KitchenVat vat = KitchenVat.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(vat != null, "the vat is a kitchen vat");
        return new Setup(station, new VatLogic(station), vat, pos);
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property,
            String name) {
        return property.getValue(name).map(value -> state.setValue(property, value)).orElse(state);
    }

    private static Player cook(Setup setup, GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        return player;
    }

    private static void lid(Setup setup, Player player, boolean open) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(VatStation.OPEN, open);
        setup.logic.onCustomAction(setup.station, player, VatStation.ACTION_LID, data);
    }

    private static void fill(Setup setup, Player player, String recipe, boolean all) {
        CompoundTag data = new CompoundTag();
        data.putString(VatStation.DISH, recipe);
        data.putBoolean(VatStation.ALL, all);
        setup.logic.onCustomAction(setup.station, player, VatStation.ACTION_FILL, data);
    }

    private static void put(Setup setup, Player player, int from) {
        CompoundTag data = new CompoundTag();
        data.putInt(VatStation.FROM, from);
        setup.logic.onCustomAction(setup.station, player, VatStation.ACTION_PUT, data);
    }

    /** Every aging recipe the vat has, its food dropped in or picked in the book: see {@link StationAudit}. */
    public static void everyAgingFromItsFoodDroppedIn(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenVat.MOD_ID)) {
            helper.succeed();
            return;
        }
        StationAudit.audit(helper, "vat", StationRecipes.candidates(KitchenVat.recipes(helper.getLevel()), KitchenVat.Aging::id,
                KitchenVat.Aging::ingredients), recipe -> null, KitchenVat.INGREDIENTS);
    }

    /**
     * The vat's recipes are read as it has them: beer of five things into a bottle at a mild warmth, vodka over heat, ice cream into a
     * wafer over ice; cola with an ingredient that is either of two. Its update carries what lies in it.
     */
    public static void theVatReadsItsRecipes(GameTestHelper helper) {
        Setup setup = vat(helper);
        if (setup == null) {
            return;
        }
        List<KitchenVat.Aging> recipes = KitchenVat.recipes(helper.getLevel());
        Optional<KitchenVat.Aging> beer = recipes.stream().filter(recipe -> recipe.id().toString().equals(BEER)).findFirst();
        helper.assertTrue(beer.isPresent() && beer.get().ingredients().size() == 5 && beer.get().container().test(new ItemStack(Items.GLASS_BOTTLE))
                && beer.get().temperature() == KitchenVat.Temperature.NORMAL && beer.get().ticks() > 0, "beer is read with five things, a bottle, mild");
        helper.assertTrue(recipes.stream().anyMatch(recipe -> recipe.result().is(cultural("vodka")) && recipe.temperature() == KitchenVat.Temperature.HOT),
                "vodka is distilled over heat");
        helper.assertTrue(recipes.stream().anyMatch(recipe -> recipe.result().is(cultural("ice_cream"))
                && recipe.temperature() == KitchenVat.Temperature.COLD && recipe.container().test(new ItemStack(cultural("wafer")))),
                "ice cream is chilled into a wafer");
        helper.assertTrue(recipes.stream().anyMatch(recipe -> recipe.result().is(cultural("cola")) && recipe.ingredients().size() == 6),
                "cola is read with its six things, one of them either of two");
        helper.assertTrue(KitchenVat.isContainer(recipes, new ItemStack(Items.GLASS_BOTTLE)), "a bottle is a container");
        helper.assertFalse(KitchenVat.isContainer(recipes, new ItemStack(Items.SUGAR)), "sugar is an ingredient");
        setup.vat.setItem(0, new ItemStack(Items.WHEAT));
        helper.assertTrue(setup.vat.blockEntity().getUpdateTag(helper.getLevel().registryAccess()).contains("inventory"),
                "the vat's update carries what lies in it");
        helper.succeed();
    }

    /**
     * The cook comes: the lid lifts on a vat that ages nothing. Things go in and come out only with it up; down, nothing does. Left
     * alone, the vat shuts itself, and the block's own {@code open} follows the lid. Only a vat on its bottom is a station.
     */
    public static void theLidKeepsTheVatShut(GameTestHelper helper) {
        Setup setup = vat(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(setup, helper);
        SlotRef first = SlotRef.of(VatStation.INGREDIENTS, 0);
        helper.assertTrue(VatLogic.isOpen(setup.station), "the lid lifts for the cook");
        helper.assertTrue(helper.getLevel().getBlockState(setup.pos).getValue(BlockStateProperties.OPEN), "the block shows it open");
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.WHEAT)), "wheat goes into the open vat");
        lid(setup, player, false);
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, new ItemStack(Items.WHEAT)), "nothing goes in through the lid");
        helper.assertFalse(setup.logic.mayTake(setup.station, player, first), "nothing comes out through the lid");
        lid(setup, player, true);
        setup.logic.onClose(setup.station, player);
        helper.assertFalse(VatLogic.isOpen(setup.station), "the last cook to leave shuts the lid");
        lid(setup, player, true);
        setup.station.users.clear();
        setup.logic.tick(setup.station);
        helper.assertFalse(VatLogic.isOpen(setup.station), "a vat left alone is shut");
        helper.assertFalse(helper.getLevel().getBlockState(setup.pos).getValue(BlockStateProperties.OPEN), "the block shows it shut");
        BlockState lying = helper.getLevel().getBlockState(setup.pos).setValue(BlockStateProperties.FACING, Direction.NORTH);
        helper.assertFalse(KitchenVat.isUpright(lying), "a vat on its side keeps its own menu");
        helper.succeed();
    }

    /** A vat placed by a player looking ahead stands on its bottom, as the station needs it, not on its side as Cultural Delights has it. */
    public static void aNewVatStandsUpright(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenVat.MOD_ID)) {
            helper.succeed();
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setXRot(0);
        player.setYRot(0);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                new ItemStack(VatStation.VAT.get()), new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        BlockState placed = VatStation.VAT.get().getStateForPlacement(context);
        helper.assertTrue(placed != null && KitchenVat.isUpright(placed), "a vat placed looking ahead stands upright, got " + placed);
        helper.succeed();
    }

    /**
     * Beer picked in the book: water, wheat, sugar and a mushroom go in from the inventory, no bottle; the water makes the brew. With
     * the lid up it does not age; shut it ages in the vat's own time into beer that stays in the vat, drawn into a bottle the cook holds,
     * one at a time: nothing with an empty hand.
     */
    public static void beerAgesWithTheLidShutAndIsDrawnIntoABottle(GameTestHelper helper) {
        Setup setup = vat(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(setup, helper);
        player.getInventory().setItem(0, new ItemStack(Items.WATER_BUCKET));
        player.getInventory().setItem(1, new ItemStack(Items.WHEAT, 5));
        player.getInventory().setItem(2, new ItemStack(Items.SUGAR, 3));
        player.getInventory().setItem(3, new ItemStack(Items.BROWN_MUSHROOM, 3));
        fill(setup, player, BEER, false);
        helper.assertTrue(setup.vat.getItem(0).is(Items.WATER_BUCKET) && setup.vat.getItem(KitchenVat.CONTAINER_SLOT).isEmpty(),
                "the book put the water in and no bottle");
        helper.assertTrue(player.getInventory().countItem(Items.WHEAT) == 3, "one set from the inventory");
        setup.logic.tick(setup.station);
        helper.assertTrue(setup.station.state().getTank(VatStation.BREW).amount() > 0, "the water stands in the vat");
        helper.onEachTick(() -> setup.logic.tick(setup.station));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(setup.vat.progress() <= 1, "nothing ages with the lid up, got " + setup.vat.progress());
            lid(setup, player, false);
        });
        helper.runAfterDelay(60, () -> helper.assertTrue(setup.vat.progress() > 0, "it ages with the lid shut and no bottle"));
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(setup.vat.servings().is(cultural("beer")) && setup.vat.servings().getCount() == 1,
                    "the beer waits in the vat, got " + setup.vat.servings());
            helper.assertTrue(setup.vat.getItem(KitchenVat.OUTPUT_SLOT).isEmpty(), "nothing in the output");
            helper.assertTrue(setup.station.state().getTank(VatStation.BREW).amount() > 0, "the beer is the vat's liquid");
            lid(setup, player, true);
            player.getInventory().setItem(5, new ItemStack(Items.GLASS_BOTTLE, 2));
            setup.logic.onCustomAction(setup.station, player, VatStation.ACTION_TAKE, new CompoundTag());
            helper.assertTrue(player.getInventory().countItem(cultural("beer")) == 0, "an empty hand draws nothing");
            player.inventoryMenu.setCarried(player.getInventory().removeItem(5, 2));
            setup.logic.onCustomAction(setup.station, player, VatStation.ACTION_TAKE, new CompoundTag());
            helper.assertTrue(player.getInventory().countItem(cultural("beer")) == 1 && player.inventoryMenu.getCarried().is(Items.GLASS_BOTTLE)
                    && player.inventoryMenu.getCarried().getCount() == 1, "the beer was drawn into one of the bottles on the cursor");
            helper.assertTrue(setup.vat.servings().isEmpty(), "the vat is empty");
            helper.succeed();
        });
    }

    /**
     * A bottle in the vat's own container slot (its menu, a hopper) ages a drink Cultural Delights' way into its output; servings
     * waiting in the vat go into bottles put there the same way.
     */
    public static void aBottleInItsSlotWorksAsBefore(GameTestHelper helper) {
        Setup setup = vat(helper);
        if (setup == null) {
            return;
        }
        setup.vat.setItem(0, new ItemStack(Items.WATER_BUCKET));
        setup.vat.setItem(1, new ItemStack(Items.WHEAT));
        setup.vat.setItem(2, new ItemStack(Items.WHEAT));
        setup.vat.setItem(3, new ItemStack(Items.SUGAR));
        setup.vat.setItem(4, new ItemStack(Items.BROWN_MUSHROOM));
        setup.vat.setItem(KitchenVat.CONTAINER_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        helper.runAfterDelay(260, () -> {
            helper.assertTrue(setup.vat.getItem(KitchenVat.OUTPUT_SLOT).is(cultural("beer")), "beer in the output, got "
                    + setup.vat.getItem(KitchenVat.OUTPUT_SLOT));
            setup.vat.setItem(KitchenVat.OUTPUT_SLOT, ItemStack.EMPTY);
            setup.vat.setServings(new ItemStack(cultural("beer"), 2));
            setup.vat.setItem(KitchenVat.CONTAINER_SLOT, new ItemStack(Items.GLASS_BOTTLE, 2));
        });
        helper.runAfterDelay(270, () -> {
            helper.assertTrue(setup.vat.getItem(KitchenVat.OUTPUT_SLOT).getCount() == 2 && setup.vat.servings().isEmpty(),
                    "the waiting beer went into the bottles put there, got " + setup.vat.getItem(KitchenVat.OUTPUT_SLOT));
            helper.succeed();
        });
    }

    /**
     * Pickles: four cucumbers put in as one stack with vinegar and salt are shared out over four places, as the vat takes one of each;
     * a bottle never goes in, and one left in the container slot (pickles want none) goes back to the cook.
     */
    public static void aStackIsSharedOutAndBottlesStayOut(GameTestHelper helper) {
        Setup setup = vat(helper);
        if (setup == null) {
            return;
        }
        setup.vat.setItem(KitchenVat.CONTAINER_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        Player player = cook(setup, helper);
        player.getInventory().setItem(0, new ItemStack(cultural("cucumber"), 4));
        player.getInventory().setItem(1, new ItemStack(cultural("vinegar"), 1));
        player.getInventory().setItem(2, new ItemStack(cultural("salt"), 1));
        player.getInventory().setItem(3, new ItemStack(Items.GLASS_BOTTLE, 2));
        for (int from = 0; from < 4; from++) {
            put(setup, player, from);
        }
        ItemStack[] places = setup.station.state().getItems(VatStation.INGREDIENTS);
        Optional<KitchenVat.Aging> making = KitchenVat.matchingPlaces(KitchenVat.recipes(helper.getLevel()), places);
        helper.assertTrue(making.isPresent() && making.get().result().is(cultural("pickle")), "the stack was shared out into pickles");
        helper.assertTrue(setup.vat.getItem(KitchenVat.CONTAINER_SLOT).isEmpty() && player.getInventory().countItem(Items.GLASS_BOTTLE) == 3,
                "the bottles stayed with the cook, the one in the vat came back");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(VatStation.INGREDIENTS, 5), new ItemStack(Items.GLASS_BOTTLE)),
                "a bottle never goes into the brine");
        helper.succeed();
    }
}
