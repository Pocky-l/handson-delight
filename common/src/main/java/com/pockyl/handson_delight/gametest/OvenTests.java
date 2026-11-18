package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.GameType;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.CfbOven;
import com.pockyl.handson_delight.station.KitchenOven;
import com.pockyl.handson_delight.station.OvenLogic;
import com.pockyl.handson_delight.station.OvenStation;

/**
 * Cooking for Blockheads' oven at the station: food laid on the rack bakes over burning fuel as the oven bakes it, the door keeps the
 * rack shut, fuel goes in from the hand or the inventory, a pick in the book fills the free places, and hoppers still fill and empty
 * it. Each test passes at once where Cooking for Blockheads is not there.
 */
public final class OvenTests {
    private OvenTests() {
    }

    @SuppressWarnings("removal")
    private static Block cfb(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(CfbOven.MOD_ID, path));
    }

    private record Setup(PotTests.TestStation station, OvenLogic logic, CfbOven oven) {
    }

    /** An oven of Cooking for Blockheads with the logic the station would make for it; null without that mod. */
    private static Setup oven(GameTestHelper helper, BlockPos at) {
        if (!Platform.INSTANCE.isLoaded(CfbOven.MOD_ID)) {
            helper.succeed();
            return null;
        }
        helper.setBlock(at, cfb("white_oven"));
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, OvenStation.CFB_TYPE);
        CfbOven oven = CfbOven.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(oven != null, "the block entity is Cooking for Blockheads' oven as the station knows it");
        return new Setup(station, new OvenLogic(station), oven);
    }

    private static Setup oven(GameTestHelper helper) {
        return oven(helper, new BlockPos(1, 2, 1));
    }

    private static void door(Setup setup, Player player, boolean open) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(OvenStation.OPEN, open);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_DOOR, data);
    }

    /** The oven's recipes are its furnace food: raw beef bakes, cobblestone does not go on the rack. */
    public static void theOvenReadsItsRecipes(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        helper.assertTrue(KitchenOven.bakingOf(setup.oven.recipes(helper.getLevel()), new ItemStack(Items.BEEF))
                .map(recipe -> recipe.result().is(Items.COOKED_BEEF)).orElse(false), "raw beef bakes into steak");
        helper.assertTrue(setup.oven.takes(new ItemStack(Items.BEEF)), "the rack takes raw beef");
        helper.assertFalse(setup.oven.takes(new ItemStack(Items.COBBLESTONE)), "and no cobblestone, which is no food");
        helper.assertFalse(setup.oven.takes(new ItemStack(Items.COOKED_BEEF)), "nor what is baked already");
        helper.assertTrue(setup.oven.isFuel(new ItemStack(Items.COAL)), "coal burns in it");
        helper.assertTrue(KitchenOven.of(new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState())) == null,
                "a furnace is no oven of the station");
        helper.succeed();
    }

    /**
     * The door is the station's: shut, nothing goes on the rack; the cook comes with food to an oven where nothing bakes and it
     * opens for them. One thing a place, each a window on the oven's own rack slots.
     */
    public static void theDoorKeepsTheRackShut(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(OvenStation.INGREDIENTS, 0), new ItemStack(Items.BEEF)),
                "the door is shut: nothing goes on the rack");
        player.inventoryMenu.setCarried(new ItemStack(Items.BEEF, 12));
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, new CompoundTag());
        helper.assertTrue(OvenLogic.isOpen(setup.station), "food brought to an oven where nothing bakes opens its door");
        for (int place = 0; place < CfbOven.PLACES; place++) {
            helper.assertTrue(setup.oven.place(place).is(Items.BEEF) && setup.oven.place(place).getCount() == 1,
                    "one raw beef on place " + place);
        }
        helper.assertTrue(player.inventoryMenu.getCarried().getCount() == 3, "three stay in the hand, got "
                + player.inventoryMenu.getCarried().getCount());
        helper.assertTrue(setup.station.state().getItem(SlotRef.of(OvenStation.INGREDIENTS, 4)).is(Items.BEEF),
                "the station's places are the oven's rack");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, SlotRef.of(OvenStation.INGREDIENTS, 0),
                new ItemStack(Items.COBBLESTONE)), "cobblestone goes on no place");
        door(setup, player, false);
        helper.assertFalse(setup.logic.mayTake(setup.station, player, SlotRef.of(OvenStation.INGREDIENTS, 0)),
                "shut, nothing comes off the rack");
        helper.assertTrue(setup.logic.mayTake(setup.station, player, SlotRef.of(OvenStation.TOOLS, 0)), "the tools on top stay in reach");
        helper.succeed();
    }

    /** Food laid on the rack and coal put in from the hand: the oven bakes each place as it always does, into its output. */
    public static void foodBakesOnTheRack(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        door(setup, player, true);
        player.inventoryMenu.setCarried(new ItemStack(Items.COAL, 2));
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_FUEL, new CompoundTag());
        helper.assertTrue(player.inventoryMenu.getCarried().isEmpty(), "the coal goes in");
        player.inventoryMenu.setCarried(new ItemStack(Items.CHICKEN));
        CompoundTag one = new CompoundTag();
        one.putBoolean(OvenStation.ONE, true);
        one.putInt(OvenStation.PLACE, 4);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, one);
        helper.assertTrue(setup.oven.place(4).is(Items.CHICKEN), "a right click lays one on the place under the cursor");
        player.getInventory().setItem(3, new ItemStack(Items.POTATO, 2));
        CompoundTag from = new CompoundTag();
        from.putInt(OvenStation.FROM, 3);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_LAY, from);
        helper.assertTrue(player.getInventory().getItem(3).isEmpty(), "Shift + click lays the inventory's potatoes on free places");
        door(setup, player, false);
        helper.runAfterDelay(CfbOven.bakeTicks() / 2, () -> {
            setup.logic.tick(setup.station);
            helper.assertTrue(setup.station.state().data().getBoolean(OvenStation.HOT), "the station tells the fire burns");
            helper.assertTrue(setup.station.state().getControl(OvenStation.PROGRESS) > 0.2F, "and how far it has baked");
        });
        helper.succeedWhen(() -> {
            int chicken = 0;
            int potatoes = 0;
            for (int slot = 0; slot < CfbOven.OUTPUTS; slot++) {
                ItemStack out = setup.oven.output(slot);
                chicken += out.is(Items.COOKED_CHICKEN) ? out.getCount() : 0;
                potatoes += out.is(Items.BAKED_POTATO) ? out.getCount() : 0;
            }
            helper.assertTrue(chicken == 1 && potatoes == 2, "baked: " + chicken + " chicken, " + potatoes + " potatoes");
        });
    }

    /** An empty hand at the fire is offered the inventory's fuel: it goes in from there. */
    public static void fuelComesFromTheInventory(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        door(setup, player, true);
        player.getInventory().setItem(9, new ItemStack(Items.BEEF, 4));
        player.getInventory().setItem(12, new ItemStack(Items.CHARCOAL, 5));
        helper.assertTrue(OvenLogic.fuelSlot(setup.oven, player.getInventory()) == 12, "the charcoal is offered, not the beef");
        CompoundTag data = new CompoundTag();
        data.putBoolean(OvenStation.FROM_INVENTORY, true);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_FUEL, data);
        helper.assertTrue(setup.oven.fuel().is(Items.CHARCOAL) && setup.oven.fuel().getCount() == 5, "all of it goes into the fuel slot");
        helper.assertTrue(player.getInventory().getItem(12).isEmpty(), "out of the inventory");
        helper.succeed();
    }

    /** A pick in the book: one onto a free place, with Shift onto every free place, from the inventory. */
    public static void aPickInTheBookFillsTheRack(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getInventory().setItem(20, new ItemStack(Items.MUTTON, 20));
        KitchenOven.Baking mutton = KitchenOven.bakingOf(setup.oven.recipes(helper.getLevel()), new ItemStack(Items.MUTTON)).orElse(null);
        helper.assertTrue(mutton != null, "mutton is in the oven's book");
        CompoundTag pick = new CompoundTag();
        pick.putString(OvenStation.DISH, mutton.id().toString());
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_FILL, pick);
        helper.assertTrue(OvenLogic.isOpen(setup.station), "a pick opens the door");
        helper.assertTrue(player.getInventory().getItem(20).getCount() == 19, "one is laid");
        pick.putBoolean(OvenStation.ALL, true);
        setup.logic.onCustomAction(setup.station, player, OvenStation.ACTION_FILL, pick);
        helper.assertTrue(player.getInventory().getItem(20).getCount() == 11, "with Shift every free place gets one, "
                + player.getInventory().getItem(20).getCount() + " left");
        helper.succeed();
    }

    /** The cook comes to an empty oven: its door opens; the last one leaves: it shuts behind them. */
    public static void theDoorShutsBehindTheLastCook(GameTestHelper helper) {
        Setup setup = oven(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        helper.assertTrue(OvenLogic.isOpen(setup.station), "an oven where nothing bakes opens for the cook");
        setup.logic.onClose(setup.station, player);
        helper.assertFalse(OvenLogic.isOpen(setup.station), "and shuts behind them");
        helper.succeed();
    }

    /**
     * A skillet clicked on the oven's top stands on the burner under the click, though the oven's kitchen does not
     * count it as a tool; an empty hand takes it back; a second skillet does not go onto a burner that has one.
     */
    @SuppressWarnings("removal")
    public static void aSkilletStandsOnABurner(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        Setup setup = oven(helper, at);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        net.minecraft.world.item.Item skillet = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "skillet"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(skillet));
        BlockPos pos = helper.absolutePos(at);
        net.minecraft.world.phys.BlockHitResult top = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0.25, 0.5, 0.25), Direction.UP, pos, false);
        int burner = CfbOven.burnerAt(helper.getBlockState(at), pos, top.getLocation());
        helper.getBlockState(at).useItemOn(player.getMainHandItem(), helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, top);
        helper.assertTrue(setup.oven.tool(burner).is(skillet) && player.getMainHandItem().isEmpty(),
                "the skillet stands on burner " + burner);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(skillet));
        helper.getBlockState(at).useItemOn(player.getMainHandItem(), helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, top);
        helper.assertTrue(player.getMainHandItem().is(skillet), "a second one does not go onto the same burner");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.getBlockState(at).useWithoutItem(helper.getLevel(), player, top);
        helper.assertTrue(setup.oven.tool(burner).isEmpty() && player.getMainHandItem().is(skillet), "an empty hand takes it back");
        helper.succeed();
    }

    /**
     * With the station there, the oven's automation is as before: a hopper on top fills it, one at its side feeds it, one under it takes
     * out what it gives.
     */
    public static void hoppersFeedAndEmptyTheOven(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        Setup setup = oven(helper, at);
        if (setup == null) {
            return;
        }
        helper.setBlock(at.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(at.east(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
        helper.setBlock(at.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(at.below(2), Blocks.STONE);
        ((HopperBlockEntity) helper.getBlockEntity(at.above())).setItem(0, new ItemStack(Items.PORKCHOP, 2));
        ((HopperBlockEntity) helper.getBlockEntity(at.east())).setItem(0, new ItemStack(Items.COAL, 1));
        // Cooking for Blockheads 21.1 with Balm 21.0 gives hoppers its whole container whatever their side (the same without this
        // mod): what the hoppers put in comes out again below, baked or not. The station keeps that as it is.
        helper.succeedWhen(() -> {
            HopperBlockEntity under = (HopperBlockEntity) helper.getBlockEntity(at.below());
            int porkchops = 0;
            for (int slot = 0; slot < under.getContainerSize(); slot++) {
                ItemStack stack = under.getItem(slot);
                porkchops += stack.is(Items.PORKCHOP) || stack.is(Items.COOKED_PORKCHOP) ? stack.getCount() : 0;
            }
            helper.assertTrue(((HopperBlockEntity) helper.getBlockEntity(at.above())).isEmpty(), "the hopper on top filled the oven");
            helper.assertTrue(porkchops == 2, "the hopper under the oven took out " + porkchops + " of 2 porkchops");
        });
    }
}
