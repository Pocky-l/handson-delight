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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.KitchenMeltingPot;
import com.pockyl.handson_delight.station.MeltingPotLogic;
import com.pockyl.handson_delight.station.MeltingPotStation;

import java.util.List;

/**
 * Extra Delight's melting pot at the station: chocolate put in melts over a stove into syrup, which a bottle draws. Each test passes at
 * once where Extra Delight is not there.
 */
public final class MeltingPotTests {
    private MeltingPotTests() {
    }

    private static Item extra(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(KitchenMeltingPot.MOD_ID, path));
    }

    /** Its recipes are read: a bar melts into a bottle's worth of syrup, a block into a bucket's. */
    public static void theMeltingPotReadsItsRecipes(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenMeltingPot.MOD_ID)) {
            helper.succeed();
            return;
        }
        List<KitchenMeltingPot.Melting> recipes = KitchenMeltingPot.recipes(helper.getLevel());
        helper.assertTrue(recipes.size() >= 10, "the melting pot's recipes are read, got " + recipes.size());
        helper.assertTrue(KitchenMeltingPot.melting(recipes, new ItemStack(extra("milk_chocolate_bar"))).map(recipe -> recipe.amount() == 250)
                .orElse(false), "a bar melts into 250 mB");
        helper.assertTrue(KitchenMeltingPot.melting(recipes, new ItemStack(extra("milk_chocolate_block"))).map(recipe -> recipe.amount() == 1000)
                .orElse(false), "a block melts into 1000 mB");
        helper.succeed();
    }

    /** Two bars put in by hand melt one after the other over a lit stove; the syrup is drawn into a bottle. */
    public static void chocolateMeltsAndIsDrawnIntoABottle(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenMeltingPot.MOD_ID)) {
            helper.succeed();
            return;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        Block stove = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "stove"));
        BlockState lit = stove.defaultBlockState();
        if (lit.hasProperty(BlockStateProperties.LIT)) {
            lit = lit.setValue(BlockStateProperties.LIT, true);
        }
        helper.setBlock(at.below(), lit);
        helper.setBlock(at, MeltingPotStation.POT.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, MeltingPotStation.TYPE);
        KitchenMeltingPot pot = KitchenMeltingPot.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(pot != null, "the melting pot is a kitchen melting pot");
        MeltingPotLogic logic = new MeltingPotLogic(station);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        station.users.add(player);
        logic.onOpen(station, player);
        player.getInventory().setItem(0, new ItemStack(extra("milk_chocolate_bar"), 2));
        player.getInventory().setItem(1, new ItemStack(Items.GLASS_BOTTLE, 3));
        CompoundTag put = new CompoundTag();
        put.putInt(MeltingPotStation.FROM, 0);
        logic.onCustomAction(station, player, MeltingPotStation.ACTION_PUT, put);
        helper.assertTrue(pot.handson$input().getCount() == 2 && player.getInventory().getItem(0).isEmpty(), "both bars go in");
        helper.onEachTick(() -> logic.tick(station));
        helper.runAfterDelay(20, () -> helper.assertTrue(pot.handson$isHeated() && station.state().getControl(MeltingPotStation.PROGRESS) > 0.0F,
                "it melts over the stove"));
        helper.succeedWhen(() -> {
            helper.assertTrue(pot.handson$amount() >= 250 && pot.handson$input().getCount() == 1, "one bar has melted, got " + pot.handson$amount());
            CompoundTag draw = new CompoundTag();
            draw.putInt(MeltingPotStation.FROM, 1);
            logic.onCustomAction(station, player, MeltingPotStation.ACTION_DRAW, draw);
            helper.assertTrue(player.getInventory().countItem(extra("milk_chocolate_syrup_bottle")) == 1
                    && player.getInventory().countItem(Items.GLASS_BOTTLE) == 2, "a bottle draws the syrup");
            helper.assertTrue(pot.handson$amount() == 0, "the syrup is drawn");
        });
    }
}
