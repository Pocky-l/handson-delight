package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import vectorwing.farmersdelight.common.block.AbstractStoveBlock;
import vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.StoveCooking;
import com.pockyl.handson_delight.station.StoveLogic;
import com.pockyl.handson_delight.station.StoveStation;

/**
 * An addon's stove may size its top with fewer places than Farmer's Delight's six: the grill and the station use only those it has,
 * and the stove ticks without a slot out of range.
 */
public final class SmallStoveTests {
    /** Places of the stove made here. */
    private static final int PLACES = 4;

    private SmallStoveTests() {
    }

    public static void aStoveWithFewerPlacesGrillsOnThoseItHas(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        BlockState lit = FarmersDelightContent.STOVE.get().defaultBlockState().setValue(AbstractStoveBlock.LIT, true);
        helper.setBlock(at, lit);
        // 1.21's test frame lays its barrier ceiling right above: the top would count as covered.
        helper.setBlock(at.above(), Blocks.AIR);
        BlockPos pos = helper.absolutePos(at);
        @SuppressWarnings("removal")
        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(FarmersDelightContent.id("stove"));
        AbstractStoveBlockEntity stove = new AbstractStoveBlockEntity(type, pos, lit, RecipeType.CAMPFIRE_COOKING) {
            @Override
            protected int getInventorySlotCount() {
                return PLACES;
            }

            @Override
            public Vec2 getStoveItemOffset(int index) {
                return Vec2.ZERO;
            }
        };
        stove.setLevel(helper.getLevel());
        helper.assertTrue(StoveCooking.place(stove, new ItemStack(Items.BEEF, 6), -1) == PLACES, "beef on each of its four places");
        StoveCooking.tick(stove);
        helper.assertTrue(StoveCooking.take(stove, PLACES + 1).isEmpty() && !StoveCooking.turn(stove, PLACES + 1),
                "a place it does not have holds nothing");
        StoveCooking.handToFarmersDelight(stove);

        // Saved by the small stove and loaded by Farmer's Delight's own: it takes the slot count saved, its cooking counts stay six.
        AbstractStoveBlockEntity reloaded = (AbstractStoveBlockEntity) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(reloaded != null && reloaded != stove, "Farmer's Delight's stove stands there");
        reloaded.loadWithComponents(stove.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        StoveCooking.tick(reloaded);
        StoveCooking.place(reloaded, new ItemStack(Items.BEEF, 6), -1);
        StoveCooking.tick(reloaded);
        helper.assertTrue(StoveCooking.take(reloaded, PLACES - 1).is(Items.BEEF), "the loaded stove kept the beef it was saved with");
        StoveCooking.handToFarmersDelight(reloaded);

        // In the world, ticked by Farmer's Delight's own ticker, with the station open on it.
        helper.getLevel().setBlockEntity(stove);
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) == stove, "the small stove stands in the world");
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, StoveStation.TYPE);
        StoveLogic logic = new StoveLogic(station);
        Player cook = helper.makeMockPlayer(GameType.CREATIVE);
        cook.inventoryMenu.setCarried(new ItemStack(Items.BEEF));
        logic.onCustomAction(station, cook, StoveStation.ACTION_PLACE, new CompoundTag());
        helper.assertTrue(cook.inventoryMenu.getCarried().getCount() == 1, "no free place: the beef stays in the hand");
        CompoundTag take = new CompoundTag();
        take.putInt(StoveStation.PLACE, PLACES - 1);
        logic.onCustomAction(station, cook, StoveStation.ACTION_TAKE, take);
        helper.assertTrue(Platform.INSTANCE.getItem(stove, PLACES - 1).isEmpty() && cook.getInventory().countItem(Items.BEEF) == 1,
                "its last place is taken");
        logic.tick(station);
        helper.runAfterDelay(10, helper::succeed);
    }
}
