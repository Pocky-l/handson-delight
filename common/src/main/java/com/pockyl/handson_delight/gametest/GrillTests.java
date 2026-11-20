package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.GrillLogic;
import com.pockyl.handson_delight.station.GrillStation;
import com.pockyl.handson_delight.station.KitchenGrill;

import java.util.List;

/**
 * Barbeque's Delight's grill at the station: skewers laid by hand, turned once from their half, done after it in the grill's own time,
 * seasoned, taken back. Each test passes at once where Barbeque's Delight is not there.
 */
public final class GrillTests {
    private GrillTests() {
    }

    private static Item barbeque(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(KitchenGrill.MOD_ID, path));
    }

    private record Setup(PotTests.TestStation station, GrillLogic logic, KitchenGrill grill, BlockPos pos) {
    }

    /** A grill with a campfire in it, lit, with the logic the station would make for it; null without the mod. */
    private static Setup grill(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KitchenGrill.MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.STONE);
        BlockState placed = GrillStation.GRILL_BLOCK.get().defaultBlockState();
        for (Property<?> property : placed.getProperties()) {
            if (property.getName().equals("has_campfire") && property instanceof BooleanProperty campfire) {
                placed = placed.setValue(campfire, true);
            }
        }
        helper.setBlock(at, placed);
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, GrillStation.TYPE);
        KitchenGrill grill = KitchenGrill.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(grill != null, "the grill is a kitchen grill");
        return new Setup(station, new GrillLogic(station), grill, pos);
    }

    private static Player cook(Setup setup, GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        setup.station.users.add(player);
        setup.logic.onOpen(setup.station, player);
        return player;
    }

    private static void act(Setup setup, Player player, String action, int place, int from) {
        CompoundTag data = new CompoundTag();
        if (place >= 0) {
            data.putInt(GrillStation.PLACE, place);
        }
        if (from >= 0) {
            data.putInt(GrillStation.FROM, from);
        }
        setup.logic.onCustomAction(setup.station, player, action, data);
    }

    /** The grill's recipes are read as it has them: ten skewers, the beef one in 320 ticks. */
    public static void theGrillReadsItsRecipes(GameTestHelper helper) {
        Setup setup = grill(helper);
        if (setup == null) {
            return;
        }
        List<KitchenGrill.Grilling> recipes = KitchenGrill.recipes(helper.getLevel());
        helper.assertTrue(recipes.size() >= 10, "ten grilling recipes, got " + recipes.size());
        KitchenGrill.Grilling beef = KitchenGrill.recipe(helper.getLevel(), new ItemStack(barbeque("raw_beef_skewer"))).orElse(null);
        helper.assertTrue(beef != null && beef.result().is(barbeque("grilled_beef_skewer")) && beef.ticks() == 320,
                "a raw beef skewer grills into a grilled one in 320 ticks, got " + beef);
        helper.assertFalse(KitchenGrill.grills(helper.getLevel(), new ItemStack(Items.BEEF)), "raw beef off a skewer is not grilled here");
        helper.succeed();
    }

    /**
     * A vegetable skewer laid from the inventory: it does not turn before its half; at its half it turns, once; after its full time it
     * is a grilled skewer, still on the grill, and taken it goes to the cook. Beef off a skewer is refused.
     */
    public static void aSkewerTurnsOnceFromItsHalfAndIsDoneAfter(GameTestHelper helper) {
        Setup setup = grill(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(setup, helper);
        player.getInventory().setItem(0, new ItemStack(Items.BEEF, 2));
        act(setup, player, GrillStation.ACTION_PLACE, -1, 0);
        helper.assertTrue(setup.grill.getItem(0).isEmpty() && player.getInventory().getItem(0).getCount() == 2, "raw beef stays off");
        player.getInventory().setItem(1, new ItemStack(barbeque("raw_vegetable_skewer"), 3));
        CompoundTag one = new CompoundTag();
        one.putInt(GrillStation.FROM, 1);
        one.putBoolean(GrillStation.ONE, true);
        one.putInt(GrillStation.PLACE, 1);
        setup.logic.onCustomAction(setup.station, player, GrillStation.ACTION_PLACE, one);
        helper.assertTrue(setup.grill.getItem(1).is(barbeque("raw_vegetable_skewer")) && setup.grill.getItem(0).isEmpty(),
                "one skewer lies on place 1");
        helper.assertTrue(player.getInventory().getItem(1).getCount() == 2, "and left the cook's stack");
        act(setup, player, GrillStation.ACTION_TURN, 1, -1);
        helper.assertFalse(setup.grill.turned(1), "it does not turn before its lower side is done");
        int half = setup.grill.duration(1) / 2;
        helper.runAfterDelay(half + 2, () -> {
            act(setup, player, GrillStation.ACTION_TURN, 1, -1);
            helper.assertTrue(setup.grill.turned(1), "at its half it turns");
            setup.logic.tick(setup.station);
            helper.assertTrue(setup.station.state().data().getBooleanOr(GrillStation.TURNED + 1, false), "the clients are told it was turned");
            helper.runAfterDelay(half + 2, () -> {
                helper.assertTrue(setup.grill.getItem(1).is(barbeque("grilled_vegetable_skewer")),
                        "done after its full time, got " + setup.grill.getItem(1));
                act(setup, player, GrillStation.ACTION_TAKE, 1, -1);
                helper.assertTrue(setup.grill.getItem(1).isEmpty() && player.getInventory().countItem(barbeque("grilled_vegetable_skewer")) == 1,
                        "taken, it goes to the cook");
                helper.succeed();
            });
        });
    }

    /**
     * A powder shift-clicked in the inventory goes over the skewer on the grill: the skewer carries it and the powder wears by one; a
     * skewer seasoned already takes no more.
     */
    public static void aPowderSeasonsASkewerOnTheGrill(GameTestHelper helper) {
        Setup setup = grill(helper);
        if (setup == null) {
            return;
        }
        Player player = cook(setup, helper);
        player.getInventory().setItem(0, new ItemStack(barbeque("raw_beef_skewer")));
        act(setup, player, GrillStation.ACTION_PLACE, -1, 0);
        helper.assertFalse(setup.grill.getItem(0).isEmpty(), "the skewer lies on place 0");
        ItemStack cumin = new ItemStack(barbeque("cumin_powder"));
        player.getInventory().setItem(1, cumin);
        act(setup, player, GrillStation.ACTION_SEASON, -1, 1);
        helper.assertTrue(KitchenGrill.isSeasoned(setup.grill.getItem(0)), "the skewer is seasoned, got " + setup.grill.getItem(0));
        helper.assertTrue(KitchenGrill.seasoning(setup.grill.getItem(0)).map(it -> it.is(barbeque("cumin_powder"))).orElse(false),
                "with cumin");
        helper.assertTrue(player.getInventory().getItem(1).getDamageValue() == 1, "the powder wore by one use");
        act(setup, player, GrillStation.ACTION_SEASON, 0, 1);
        helper.assertTrue(player.getInventory().getItem(1).getDamageValue() == 1, "a seasoned skewer takes no more");
        helper.succeed();
    }

    /** A new grill opens as a station for an empty hand, a skewer or a seasoning; a campfire and sneaking keep the grill's own use. */
    public static void theGrillOpensForWhatItTakes(GameTestHelper helper) {
        Setup setup = grill(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockState state = helper.getLevel().getBlockState(setup.pos);
        helper.assertTrue(GrillStation.opens(player, setup.pos, state), "an empty hand opens it");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(barbeque("raw_beef_skewer")));
        helper.assertTrue(GrillStation.opens(player, setup.pos, state), "a skewer opens it");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(barbeque("chili_powder")));
        helper.assertTrue(GrillStation.opens(player, setup.pos, state), "a seasoning opens it");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.CAMPFIRE));
        helper.assertFalse(GrillStation.opens(player, setup.pos, state), "a campfire goes into the grill");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        helper.assertFalse(GrillStation.opens(player, setup.pos, state), "sneaking keeps the grill's quick turn");
        helper.succeed();
    }
}
