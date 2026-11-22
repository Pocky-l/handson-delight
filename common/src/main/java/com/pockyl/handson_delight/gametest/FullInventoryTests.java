package com.pockyl.handson_delight.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.BoardLogic;
import com.pockyl.handson_delight.station.CuisineBoardStation;
import com.pockyl.handson_delight.station.CuisineLogic;
import com.pockyl.handson_delight.station.CuttingBoardStation;
import com.pockyl.handson_delight.station.KitchenCuisine;
import com.pockyl.handson_delight.station.KitchenYoukaiPot;
import com.pockyl.handson_delight.station.YoukaiPotLogic;
import com.pockyl.handson_delight.station.YoukaiPotStation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a station gives a cook whose inventory has room for only part of it: what fits goes in, only the rest drops at their feet. A
 * stack that went in part of the way used to drop whole on top of that.
 */
public final class FullInventoryTests {
    private FullInventoryTests() {
    }

    /** A cook in survival whose drops are counted instead of thrown into the world. */
    private static final class Cook extends Player {
        private final List<ItemStack> dropped = new ArrayList<>();

        Cook(GameTestHelper helper) {
            super(helper.getLevel(), new GameProfile(UUID.randomUUID(), "cook"));
        }

        @Override
        public GameType gameMode() {
            return GameType.SURVIVAL;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public ItemEntity drop(ItemStack stack, boolean thrownFromHand) {
            dropped.add(stack.copy());
            return null;
        }

        int dropped(Item item) {
            return dropped.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
        }
    }

    /** {@link Cook} as a server player with a connection: Refabricated's board casts the cutting player to one. */
    private static final class ServerCook extends ServerPlayer {
        private final List<ItemStack> dropped = new ArrayList<>();

        ServerCook(GameTestHelper helper) {
            super(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "cook"),
                    ClientInformation.createDefault());
            new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new net.minecraft.network.Connection(PacketFlow.SERVERBOUND), this,
                    CommonListenerCookie.createInitial(getGameProfile(), false));
            // A new player takes the server's game mode, creative on the game test server: a creative knife never wears.
            setGameMode(GameType.SURVIVAL);
        }

        @Override
        public ItemEntity drop(ItemStack stack, boolean thrownFromHand) {
            dropped.add(stack.copy());
            return null;
        }

        int dropped(Item item) {
            return dropped.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
        }
    }

    private static Item feasts(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(KitchenCuisine.MOD_ID, path));
    }

    private static Item farmers(String path) {
        return BuiltInRegistries.ITEM.getValue(FarmersDelightContent.id(path));
    }

    /**
     * Youkai's Feasts' cuisine board cuts a kappa maki into three pieces for a cook whose only room is two more on their stack of
     * pieces: two go in, one drops. Passes at once where Youkai's Feasts is not there.
     */
    public static void cutPiecesThatDoNotFitDropOnce(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenCuisine.MOD_ID)) {
            helper.succeed();
            return;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, CuisineBoardStation.BOARD.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, CuisineBoardStation.TYPE);
        KitchenCuisine board = KitchenCuisine.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(board != null, "Youkai's Feasts' cuisine board is a kitchen board");
        CuisineLogic logic = new CuisineLogic(station);
        Cook cook = new Cook(helper);
        Inventory inventory = cook.getInventory();
        inventory.setItem(0, new ItemStack(Items.DRIED_KELP));
        inventory.setItem(1, new ItemStack(farmers("cooked_rice")));
        inventory.setItem(2, new ItemStack(feasts("soy_sauce_bottle")));
        inventory.setItem(3, new ItemStack(feasts("cucumber_slice")));
        CompoundTag fill = new CompoundTag();
        fill.putString(CuisineBoardStation.DISH, "youkaisfeasts:cuisine_ordered/kappa_maki");
        logic.onCustomAction(station, cook, CuisineBoardStation.ACTION_FILL, fill);
        helper.assertTrue(board.handson$finished().is(feasts("kappa_maki")), "a kappa maki lies rolled, got " + board.handson$finished());

        Item piece = feasts("kappa_maki_slice");
        int had = piece.getDefaultMaxStackSize() - 2;
        inventory.clearContent();
        inventory.setItem(0, new ItemStack(farmers("iron_knife")));
        inventory.setItem(1, new ItemStack(piece, had));
        for (int slot = 2; slot < Inventory.INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        for (int mark = 0; mark < 2; mark++) {
            CompoundTag cut = new CompoundTag();
            cut.putInt(CuisineBoardStation.MARK, mark);
            logic.onCustomAction(station, cook, CuisineBoardStation.ACTION_CUT, cut);
        }
        helper.assertTrue(board.handson$contents().isEmpty(), "the maki was cut through");
        int kept = inventory.countItem(piece) - had;
        int dropped = cook.dropped(piece);
        helper.assertTrue(kept == 2 && dropped == 1, "of three pieces two fit and one drops; got " + kept + " in, " + dropped + " dropped");
        helper.succeed();
    }

    /** Every slot but {@code free} filled with cobblestone. */
    private static void fillAllBut(Inventory inventory, int free) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (slot != free) {
                inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
        }
    }

    /**
     * The knife from the inventory breaks on the board's last cut, its slot the only room the cook has: the cabbage leaves go into
     * that slot, and stay there.
     */
    public static void aKnifeThatBreaksOnTheLastCutLeavesThePieces(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, FarmersDelightContent.CUTTING_BOARD.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, CuttingBoardStation.TYPE);
        BoardLogic logic = new BoardLogic(station);
        ServerCook cook = new ServerCook(helper);
        cook.inventoryMenu.setCarried(new ItemStack(farmers("cabbage")));
        logic.onCustomAction(station, cook, CuttingBoardStation.ACTION_PLACE, new CompoundTag());
        helper.assertTrue(cook.inventoryMenu.getCarried().isEmpty(), "the cabbage lies on the board");
        ItemStack knife = new ItemStack(farmers("iron_knife"));
        knife.setDamageValue(knife.getMaxDamage() - 1);
        Inventory inventory = cook.getInventory();
        inventory.setItem(0, knife);
        fillAllBut(inventory, 0);
        CompoundTag chop = new CompoundTag();
        chop.putInt(CuttingBoardStation.MARK, 0);
        chop.putFloat(CuttingBoardStation.AT, 10.0F);
        logic.onCustomAction(station, cook, CuttingBoardStation.ACTION_CHOP, chop);
        Item leaf = farmers("cabbage_leaf");
        int kept = inventory.countItem(leaf);
        int dropped = cook.dropped(leaf);
        helper.assertTrue(inventory.countItem(farmers("iron_knife")) == 0, "the knife broke");
        helper.assertTrue(kept + dropped == 2, "both leaves reach the cook; got " + kept + " in, " + dropped + " dropped");
        helper.succeed();
    }

    /**
     * The last bowl of cream goes into Youkai's Feasts' stockpot from the cook's inventory, its slot the only room: the bowl comes back
     * into that slot. Passes at once where Youkai's Feasts is not there.
     */
    public static void aYoukaiPotGivesTheContainerBackIntoTheEmptiedSlot(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenYoukaiPot.MOD_ID)) {
            helper.succeed();
            return;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, YoukaiPotStation.STOCKPOT.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, YoukaiPotStation.STOCKPOT_TYPE);
        YoukaiPotLogic logic = new YoukaiPotLogic(station);
        Cook cook = new Cook(helper);
        station.users.add(cook);
        logic.onOpen(station, cook);
        Inventory inventory = cook.getInventory();
        inventory.setItem(0, new ItemStack(feasts("bowl_of_cream")));
        fillAllBut(inventory, 0);
        CompoundTag from = new CompoundTag();
        from.putInt(YoukaiPotStation.FROM, 0);
        logic.onCustomAction(station, cook, YoukaiPotStation.ACTION_PUT, from);
        KitchenYoukaiPot pot = KitchenYoukaiPot.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(pot != null && pot.handson$contents().size() == 1, "the cream went in");
        int back = inventory.countItem(Items.BOWL) + cook.dropped(Items.BOWL);
        helper.assertTrue(back == 1, "its bowl comes back; got " + back);
        helper.succeed();
    }

    /**
     * The last cooked rice goes onto Youkai's Feasts' cuisine board from the cook's inventory, its slot the only room: the bowl comes
     * back into that slot. Passes at once where Youkai's Feasts is not there.
     */
    public static void theCuisineBoardGivesTheContainerBackIntoTheEmptiedSlot(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenCuisine.MOD_ID)) {
            helper.succeed();
            return;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, CuisineBoardStation.BOARD.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, CuisineBoardStation.TYPE);
        KitchenCuisine board = KitchenCuisine.of(helper.getLevel().getBlockEntity(pos));
        CuisineLogic logic = new CuisineLogic(station);
        Cook cook = new Cook(helper);
        Inventory inventory = cook.getInventory();
        inventory.setItem(0, new ItemStack(farmers("cooked_rice")));
        fillAllBut(inventory, 0);
        CompoundTag from = new CompoundTag();
        from.putInt(CuisineBoardStation.FROM, 0);
        logic.onCustomAction(station, cook, CuisineBoardStation.ACTION_PUT, from);
        helper.assertTrue(board != null && board.handson$contents().size() == 1, "the rice went on");
        int back = inventory.countItem(Items.BOWL) + cook.dropped(Items.BOWL);
        helper.assertTrue(back == 1, "its bowl comes back; got " + back);
        helper.succeed();
    }
}
