package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.KitchenSteamer;
import com.pockyl.handson_delight.station.SteamerLogic;
import com.pockyl.handson_delight.station.SteamerStation;
import com.pockyl.handson_delight.station.SteamerTower;

import java.util.List;

/** Youkai's Feasts' steamer at the station: its tower, water, racks, lid, things laid and taken, and the steaming itself. */
public final class YoukaiSteamerTests {
    /** A potato steams 200 ticks at 1.2 a tick in a covered tower; a little more for the steam to come up. */
    static final int STEAMS = 200;

    private YoukaiSteamerTests() {
    }

    @SuppressWarnings("removal")
    private static Item feasts(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(KitchenSteamer.MOD_ID, path));
    }

    private record Setup(PotTests.TestStation station, SteamerLogic logic, BlockPos at, Player player) {
        SteamerTower tower() {
            return SteamerTower.of(station.level(), station.pos());
        }

        void act(String action, CompoundTag data) {
            logic.onCustomAction(station, player, action, data);
        }

        /** {@code action} with {@code stack} on the cursor. */
        void carrying(ItemStack stack, String action, CompoundTag data) {
            player.inventoryMenu.setCarried(stack);
            act(action, data);
        }
    }

    /** An empty steamer pot over a lit campfire, a cook at it in survival; null without Youkai's Feasts. */
    private static Setup steamer(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenSteamer.MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CAMPFIRE);
        helper.setBlock(at, SteamerStation.POT.get());
        BlockPos pos = helper.absolutePos(at);
        helper.assertTrue(KitchenSteamer.of(helper.getLevel().getBlockEntity(pos)) != null,
                "Youkai's Feasts' steamer is a kitchen steamer");
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, SteamerStation.TYPE);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = false;
        BlockPos stands = helper.absolutePos(at.north(2));
        player.setPos(stands.getX() + 0.5, stands.getY(), stands.getZ() + 0.5);
        Setup setup = new Setup(station, new SteamerLogic(station), at, player);
        station.users.add(player);
        setup.logic.onOpen(station, player);
        return setup;
    }

    private static CompoundTag none() {
        return new CompoundTag();
    }

    /**
     * Water from a bucket (the empty bucket back), three racks (two on the pot, the third a block of racks over it), the lid; a potato
     * on a place of the top rack, a stick refused; it steams into a baked potato, which shift takes to the cook.
     */
    public static void youkaiSteamerSteamsAPotato(GameTestHelper helper) {
        Setup setup = steamer(helper);
        if (setup == null) {
            return;
        }
        setup.carrying(new ItemStack(Items.WATER_BUCKET), SteamerStation.ACTION_WATER, none());
        helper.assertTrue(setup.tower().potBlock().handson$hasWater() && setup.player.inventoryMenu.getCarried().is(Items.BUCKET),
                "the water went in, the empty bucket stays in the hand");
        setup.carrying(new ItemStack(SteamerStation.RACK_BLOCK.get(), 3), SteamerStation.ACTION_RACK, none());
        setup.act(SteamerStation.ACTION_RACK, none());
        setup.act(SteamerStation.ACTION_RACK, none());
        SteamerTower tower = setup.tower();
        helper.assertTrue(tower.racks() == 3 && tower.blocks() == 2 && setup.player.inventoryMenu.getCarried().isEmpty(),
                "three racks: two on the pot, one in a block over it; got " + tower.racks() + " in " + tower.blocks());
        setup.carrying(new ItemStack(SteamerStation.LID.get()), SteamerStation.ACTION_LID, none());
        helper.assertTrue(setup.tower().isCapped() && setup.player.inventoryMenu.getCarried().isEmpty(), "the lid is on");
        helper.assertTrue(SteamerLogic.openRack(setup.station, setup.tower()) == 2, "the top rack is open");
        CompoundTag on = new CompoundTag();
        on.putInt(SteamerStation.RACK, 2);
        on.putInt(SteamerStation.PLACE, 2);
        setup.carrying(new ItemStack(Items.STICK), SteamerStation.ACTION_PUT, on);
        helper.assertTrue(setup.tower().isEmpty(2) && setup.player.inventoryMenu.getCarried().is(Items.STICK),
                "a stick is no food: refused, it stays in the hand");
        setup.carrying(new ItemStack(Items.POTATO, 2), SteamerStation.ACTION_PUT, on);
        helper.assertTrue(setup.tower().item(2, 2).is(Items.POTATO) && setup.player.inventoryMenu.getCarried().getCount() == 1,
                "one potato lies on the place it was let go over");
        setup.player.inventoryMenu.setCarried(ItemStack.EMPTY);
        helper.runAfterDelay(5, () -> helper.assertTrue(setup.tower().steamed(2, 2) > 0.0F && setup.tower().heat(2) > 1.0F,
                "it steams, with the steam back off the lid: " + setup.tower().steamed(2, 2) + " at " + setup.tower().heat(2)));
        helper.runAfterDelay(STEAMS, () -> {
            helper.assertTrue(setup.tower().item(2, 2).is(Items.BAKED_POTATO), "the potato is steamed: " + setup.tower().item(2, 2));
            CompoundTag all = new CompoundTag();
            all.putBoolean(SteamerStation.ALL, true);
            setup.act(SteamerStation.ACTION_TAKE, all);
            helper.assertTrue(setup.tower().isEmpty(2) && setup.player.getInventory().countItem(Items.BAKED_POTATO) == 1,
                    "shift took everything done to the cook");
            helper.succeed();
        });
    }

    /**
     * Shift lays a stack over every free place of the open rack and leaves the closed ones alone; the other button takes back what
     * was laid last; a feast block fills a rack of its own; a rack lower down opens, the racks over it lifted aside.
     */
    public static void youkaiSteamerLaysTheTower(GameTestHelper helper) {
        Setup setup = steamer(helper);
        if (setup == null) {
            return;
        }
        setup.carrying(new ItemStack(SteamerStation.RACK_BLOCK.get(), 3), SteamerStation.ACTION_RACK, none());
        setup.act(SteamerStation.ACTION_RACK, none());
        setup.act(SteamerStation.ACTION_RACK, none());
        CompoundTag all = new CompoundTag();
        all.putBoolean(SteamerStation.ALL, true);
        setup.carrying(new ItemStack(Items.POTATO, 6), SteamerStation.ACTION_PUT, all);
        SteamerTower tower = setup.tower();
        int top = 0;
        int bottom = 0;
        for (int place = 0; place < KitchenSteamer.PLACES; place++) {
            top += tower.item(2, place).isEmpty() ? 0 : 1;
            bottom += tower.item(0, place).isEmpty() ? 0 : 1;
        }
        helper.assertTrue(top == 4 && bottom == 0 && setup.player.inventoryMenu.getCarried().getCount() == 2,
                "shift: four on the open top rack, none in the closed racks, two left; got " + top + " and " + bottom);
        setup.player.inventoryMenu.setCarried(ItemStack.EMPTY);
        setup.act(SteamerStation.ACTION_BACK, none());
        helper.assertTrue(setup.tower().item(2, 3).isEmpty() && setup.player.getInventory().countItem(Items.POTATO) == 1,
                "back: the last potato laid came off to the cook");
        CompoundTag open = new CompoundTag();
        open.putInt(SteamerStation.RACK, 1);
        setup.act(SteamerStation.ACTION_OPEN, open);
        helper.assertTrue(SteamerLogic.openRack(setup.station, setup.tower()) == 1, "the middle rack is open");
        CompoundTag on = new CompoundTag();
        on.putInt(SteamerStation.RACK, 1);
        on.putInt(SteamerStation.PLACE, 3);
        setup.carrying(new ItemStack(feasts("raw_tutu_congee")), SteamerStation.ACTION_PUT, on);
        helper.assertTrue(setup.tower().item(1, 0).is(feasts("raw_tutu_congee")),
                "a feast block let go anywhere on an empty rack fills it from its first place");
        setup.carrying(new ItemStack(Items.POTATO), SteamerStation.ACTION_PUT, on);
        helper.assertTrue(setup.tower().item(1, 3).isEmpty() && setup.player.inventoryMenu.getCarried().is(Items.POTATO),
                "nothing goes beside a feast block");
        helper.succeed();
    }

    /**
     * Two cooks at one steamer: the other button takes back only what that cook laid, the other cook's potato stays. A feast block
     * comes off its rack by a click on any quarter of it.
     */
    public static void youkaiSteamerTakesBackEachCooksOwn(GameTestHelper helper) {
        Setup setup = steamer(helper);
        if (setup == null) {
            return;
        }
        Player other = helper.makeMockPlayer(GameType.CREATIVE);
        other.getAbilities().instabuild = false;
        setup.station.users.add(other);
        setup.logic.onOpen(setup.station, other);
        setup.carrying(new ItemStack(SteamerStation.RACK_BLOCK.get(), 2), SteamerStation.ACTION_RACK, none());
        setup.act(SteamerStation.ACTION_RACK, none());
        CompoundTag on = new CompoundTag();
        on.putInt(SteamerStation.RACK, 1);
        on.putInt(SteamerStation.PLACE, 1);
        setup.carrying(new ItemStack(Items.POTATO), SteamerStation.ACTION_PUT, on);
        helper.assertTrue(setup.tower().item(1, 1).is(Items.POTATO), "the first cook laid a potato");
        setup.logic.onCustomAction(setup.station, other, SteamerStation.ACTION_BACK, none());
        helper.assertTrue(setup.tower().item(1, 1).is(Items.POTATO) && other.getInventory().countItem(Items.POTATO) == 0,
                "the other cook laid nothing: their take-back leaves the potato");
        setup.act(SteamerStation.ACTION_BACK, none());
        helper.assertTrue(setup.tower().item(1, 1).isEmpty() && setup.player.getInventory().countItem(Items.POTATO) == 1,
                "the first cook takes their potato back");
        CompoundTag feast = new CompoundTag();
        feast.putInt(SteamerStation.RACK, 0);
        feast.putInt(SteamerStation.PLACE, 0);
        setup.carrying(new ItemStack(feasts("raw_tutu_congee")), SteamerStation.ACTION_PUT, feast);
        helper.assertTrue(setup.tower().item(0, 0).is(feasts("raw_tutu_congee")), "a feast block fills the lower rack");
        feast.putInt(SteamerStation.PLACE, 2);
        setup.logic.onCustomAction(setup.station, other, SteamerStation.ACTION_TAKE, feast);
        helper.assertTrue(setup.tower().isEmpty(0) && other.getInventory().countItem(feasts("raw_tutu_congee")) == 1,
                "a click on another quarter of the rack takes the feast block");
        helper.succeed();
    }

    /** A rack block or the lid opens the station at the pot under it; racks on anything else are no station; the tower hides as one. */
    public static void youkaiSteamerIsOneTower(GameTestHelper helper) {
        Setup setup = steamer(helper);
        if (setup == null) {
            return;
        }
        setup.carrying(new ItemStack(SteamerStation.RACK_BLOCK.get(), 6), SteamerStation.ACTION_RACK, none());
        for (int i = 0; i < 5; i++) {
            setup.act(SteamerStation.ACTION_RACK, none());
        }
        setup.carrying(new ItemStack(SteamerStation.LID.get()), SteamerStation.ACTION_LID, none());
        BlockPos pot = setup.station.pos();
        BlockPos rack = pot.above();
        BlockPos lid = pot.above(2);
        helper.assertTrue(SteamerTower.isRack(helper.getLevel().getBlockState(rack)) && SteamerTower.isLid(helper.getLevel()
                .getBlockState(lid)), "six racks: the pot full, a full block of racks over it, the lid a block over that");
        helper.assertTrue(SteamerTower.potUnder(helper.getLevel(), lid, helper.getLevel().getBlockState(lid)).equals(pot)
                && SteamerTower.potUnder(helper.getLevel(), rack, helper.getLevel().getBlockState(rack)).equals(pot),
                "the rack block and the lid open the station at the pot");
        List<BlockPos> hidden = List.copyOf(SteamerStation.TYPE.hiddenBlocks(helper.getLevel(), pot));
        helper.assertTrue(hidden.equals(List.of(pot, rack, lid)), "the whole tower is hidden as one: " + hidden);
        BlockPos alone = helper.absolutePos(new BlockPos(3, 2, 1));
        helper.getLevel().setBlockAndUpdate(alone.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(alone, SteamerStation.RACK_BLOCK.get().defaultBlockState());
        helper.assertTrue(SteamerTower.potUnder(helper.getLevel(), alone, helper.getLevel().getBlockState(alone)).equals(alone)
                && !SteamerStation.opens(setup.player, alone, helper.getLevel().getBlockState(alone)),
                "a rack on stone is no station: it keeps its own use");
        helper.succeed();
    }

    /**
     * Shift with racks on the pot, the way round the station: each goes onto the tower flush (the pot's own two), none is placed as a
     * block of its own over the pot (Discord, 2026-11-26: a rack floated 8 px over the pot). The real click, so the loader's hook runs.
     */
    public static void youkaiSteamerTakesARackOnShift(GameTestHelper helper) {
        Setup setup = steamer(helper);
        if (setup == null) {
            return;
        }
        ServerPlayer cook = TestPlayers.serverPlayer(helper, "cook");
        cook.setShiftKeyDown(true);
        cook.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SteamerStation.RACK_BLOCK.get(), 2));
        BlockPos pot = setup.station.pos();
        for (int i = 0; i < 2; i++) {
            cook.gameMode.useItemOn(cook, helper.getLevel(), cook.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.upFromBottomCenterOf(pot, 1.0), Direction.UP, pot, false));
        }
        helper.assertTrue(setup.tower().racks() == 2, "both racks went onto the pot, got " + setup.tower().racks());
        helper.assertTrue(helper.getLevel().getBlockState(pot.above()).isAir(), "no block of racks over the pot");
        helper.assertTrue(cook.getMainHandItem().isEmpty(), "both racks were used");
        helper.succeed();
    }
}
