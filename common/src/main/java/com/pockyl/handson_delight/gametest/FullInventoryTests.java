package com.pockyl.handson_delight.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.CuisineBoardStation;
import com.pockyl.handson_delight.station.CuisineLogic;
import com.pockyl.handson_delight.station.KitchenCuisine;

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
            super(helper.getLevel(), BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "cook"));
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
        public ItemEntity drop(ItemStack stack, boolean includeThrowerName) {
            dropped.add(stack.copy());
            return null;
        }

        int dropped(Item item) {
            return dropped.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
        }
    }

    private static Item feasts(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(KitchenCuisine.MOD_ID, path));
    }

    private static Item farmers(String path) {
        return BuiltInRegistries.ITEM.get(FarmersDelightContent.id(path));
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
}
