package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.KegLogic;
import com.pockyl.handson_delight.station.KegStation;
import com.pockyl.handson_delight.station.KitchenKeg;

import java.util.List;

/**
 * Hearth and Harvest's cask at the station: its lid is its block's seal; things go in only unsealed and are laid out the way a
 * recipe reads them; sealed, a bottle of wine ages into its next vintage, which is given out with the lid on; the book puts in
 * whole sets, the lid taken off for them. Each test passes at once where Hearth and Harvest is not there.
 */
public final class HearthCaskTests {
    private HearthCaskTests() {
    }

    @SuppressWarnings("removal")
    private static Item hearth(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(KegStation.HEARTH_MOD_ID, path));
    }

    @SuppressWarnings("removal")
    private static String recipe(String path) {
        return Identifier.fromNamespaceAndPath(KegStation.HEARTH_MOD_ID, "aging/" + path).toString();
    }

    private record Setup(PotTests.TestStation station, KegLogic logic, KitchenKeg cask, BlockPos pos) {
    }

    /** A cask on stone, with the logic the station would make for it; null without Hearth and Harvest. */
    private static Setup cask(GameTestHelper helper) {
        if (!Platform.INSTANCE.isLoaded(KegStation.HEARTH_MOD_ID)) {
            helper.succeed();
            return null;
        }
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, KegStation.CASK.get());
        BlockPos pos = helper.absolutePos(at);
        PotTests.TestStation station = new PotTests.TestStation(helper.getLevel(), pos, KegStation.CASK_TYPE);
        KitchenKeg cask = KitchenKeg.of(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(cask != null, "Hearth and Harvest's cask is a kitchen keg");
        return new Setup(station, new KegLogic(station), cask, pos);
    }

    private static void lid(Setup setup, Player player, boolean off) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(KegStation.OPEN, off);
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_LID, data);
    }

    /** Whether the block shows the cask sealed: its {@code sealed} property, read by name. */
    private static boolean blockSealed(GameTestHelper helper, Setup setup) {
        for (Property<?> property : helper.getLevel().getBlockState(setup.pos).getProperties()) {
            if (property.getName().equals("sealed") && property instanceof BooleanProperty sealed) {
                return helper.getLevel().getBlockState(setup.pos).getValue(sealed);
            }
        }
        return false;
    }

    /** Brings what the cask ages to a few ticks before it is done, through its own saved data. */
    private static void nearlyDone(GameTestHelper helper, Setup setup) {
        BlockEntity entity = helper.getLevel().getBlockEntity(setup.pos);
        CompoundTag tag = entity.saveWithoutMetadata(helper.getLevel().registryAccess());
        int total = tag.getIntOr("AgeTimeTotal", 0);
        if (total > 8 && tag.getIntOr("AgeTime", 0) > 0) {
            tag.putInt("AgeTime", total - 4);
            entity.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        }
    }

    /** Its recipes are read as it ages them: a red wine into its next vintage, jerky of salt and three rotten flesh. */
    public static void hearthCaskReadsItsRecipes(GameTestHelper helper) {
        Setup setup = cask(helper);
        if (setup == null) {
            return;
        }
        List<KitchenKeg.Fermenting> recipes = setup.cask.handson$recipes();
        helper.assertTrue(recipes.size() >= 50, "every aging recipe, got " + recipes.size());
        KitchenKeg.Fermenting wine = find(recipes, recipe("red_grape_wine_aged"));
        helper.assertTrue(wine != null && wine.ingredients().size() == 1 && wine.resultItem().is(hearth("red_grape_wine"))
                && !wine.resultItem().getComponentsPatch().isEmpty(), "a red wine ages into a bottle of its next vintage");
        helper.assertTrue(wine.ticks() == 48000 || wine.ticks() == 24000 || wine.ticks() == 12000,
                "twice its day, shorter in the dark, got " + wine.ticks());
        KitchenKeg.Fermenting jerky = find(recipes, recipe("jerky_from_rotten_flesh"));
        ItemStack salt = new ItemStack(hearth("salt"));
        ItemStack flesh = new ItemStack(Items.ROTTEN_FLESH);
        helper.assertTrue(jerky != null && jerky.ingredients().size() == 4 && jerky.resultItem().getCount() == 3, "three jerky");
        helper.assertTrue(setup.cask.handson$makes(jerky, List.of(salt, flesh, flesh, flesh))
                && !setup.cask.handson$makes(jerky, List.of(salt, new ItemStack(Items.ROTTEN_FLESH, 3))),
                "salt and three rotten flesh, one a place; a stack in one place is not three");
        helper.assertTrue(!setup.cask.handson$feelsWarmth() && setup.cask.handson$fluid() == net.minecraft.world.level.material.Fluids.EMPTY
                && !setup.cask.handson$isVessel(new ItemStack(hearth("red_grape_wine"))), "no warmth, no liquid; a bottle goes in");
        helper.succeed();
    }

    private static KitchenKeg.Fermenting find(List<KitchenKeg.Fermenting> recipes, String id) {
        return recipes.stream().filter(recipe -> recipe.id().toString().equals(id)).findFirst().orElse(null);
    }

    /**
     * Its lid is its block's seal: a new cask stands sealed, as the world shows it; sealed, nothing goes in nor comes out of its places,
     * but what is done is taken; the lid taken off unseals it; the last cook leaving seals it.
     */
    public static void hearthCaskLidIsItsSeal(GameTestHelper helper) {
        Setup setup = cask(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        setup.logic.tick(setup.station);
        helper.assertTrue(Boolean.FALSE.equals(setup.cask.handson$lidOff()) && !KegLogic.isOpen(setup.station),
                "a new cask stands sealed, its lid on");
        SlotRef first = SlotRef.of(KegStation.INGREDIENTS, 0);
        lid(setup, player, true);
        helper.assertTrue(setup.logic.mayPlace(setup.station, player, first, new ItemStack(hearth("red_grape_wine"))),
                "a bottle of wine goes in unsealed");
        lid(setup, player, false);
        helper.assertTrue(blockSealed(helper, setup), "the lid put on seals the block");
        setup.logic.tick(setup.station);
        helper.assertFalse(KegLogic.isOpen(setup.station), "the station shows it sealed");
        helper.assertFalse(setup.logic.mayPlace(setup.station, player, first, new ItemStack(hearth("red_grape_wine"))),
                "nothing goes in sealed");
        helper.assertFalse(setup.logic.mayTake(setup.station, player, first), "nothing comes out of its places sealed");
        helper.assertTrue(setup.logic.mayTake(setup.station, player, KegStation.SERVED_SLOT), "what is done is taken sealed");
        lid(setup, player, true);
        helper.assertFalse(blockSealed(helper, setup), "the lid taken off unseals it");
        setup.logic.onClose(setup.station, player);
        helper.assertTrue(blockSealed(helper, setup), "the last cook leaving seals it");
        helper.succeed();
    }

    /**
     * Two bottles of red wine go in unsealed and lie together; sealed, the first ages into its next vintage, which waits in the served
     * slot and is taken with the lid on while the second ages.
     */
    public static void hearthWineAgesSealed(GameTestHelper helper) {
        Setup setup = cask(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        lid(setup, player, true);
        setup.station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 0), new ItemStack(hearth("red_grape_wine")));
        setup.station.state().setItem(SlotRef.of(KegStation.INGREDIENTS, 1), new ItemStack(hearth("red_grape_wine")));
        setup.logic.onSlotChanged(setup.station, SlotRef.of(KegStation.INGREDIENTS, 1));
        setup.logic.tick(setup.station);
        helper.assertTrue(setup.cask.handson$getItem(0).getCount() == 2 && setup.cask.handson$getItem(1).isEmpty(),
                "two bottles laid together, as the cask reads one wine, got " + setup.cask.handson$getItem(0) + ", "
                        + setup.cask.handson$getItem(1));
        lid(setup, player, false);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(setup.cask.handson$data().get(1) > 0 && setup.cask.handson$data().get(0) > 0,
                    "it ages sealed, " + setup.cask.handson$data().get(0) + " of " + setup.cask.handson$data().get(1));
            nearlyDone(helper, setup);
        });
        helper.runAfterDelay(14, () -> {
            setup.logic.tick(setup.station);
            ItemStack served = setup.station.state().getItem(KegStation.SERVED_SLOT);
            helper.assertTrue(served.is(hearth("red_grape_wine")) && !served.getComponentsPatch().isEmpty(), "a bottle of its next vintage waits, got " + served);
            helper.assertTrue(setup.cask.handson$getItem(0).getCount() == 1, "one bottle still ages");
            helper.assertFalse(KegLogic.isOpen(setup.station), "still sealed");
            helper.assertTrue(setup.logic.mayTake(setup.station, player, KegStation.SERVED_SLOT), "taken with the lid on");
            setup.station.state().setItem(KegStation.SERVED_SLOT, ItemStack.EMPTY);
            setup.logic.onTaken(setup.station, player, KegStation.SERVED_SLOT, served);
            helper.assertTrue(setup.cask.handson$getItem(KitchenKeg.OUTPUT_SLOT).isEmpty(), "taken out of its result slot");
            helper.succeed();
        });
    }

    /** Jerky from the book: the lid comes off, two sets go in, salt in one place and the rotten flesh shared over the other three. */
    public static void hearthCaskBookLaysJerky(GameTestHelper helper) {
        Setup setup = cask(helper);
        if (setup == null) {
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        lid(setup, player, false);
        player.getInventory().setItem(0, new ItemStack(hearth("salt"), 2));
        player.getInventory().setItem(1, new ItemStack(Items.ROTTEN_FLESH, 7));
        CompoundTag fill = new CompoundTag();
        fill.putString(KegStation.DISH, recipe("jerky_from_rotten_flesh"));
        fill.putBoolean(KegStation.ALL, true);
        setup.logic.onCustomAction(setup.station, player, KegStation.ACTION_FILL, fill);
        helper.assertTrue(!blockSealed(helper, setup), "the book took the lid off");
        int salt = 0;
        int places = 0;
        for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = setup.cask.handson$getItem(slot);
            if (stack.is(hearth("salt"))) {
                salt += stack.getCount();
            } else if (stack.is(Items.ROTTEN_FLESH)) {
                helper.assertTrue(stack.getCount() == 2, "two rotten flesh a place, got " + stack);
                places++;
            }
        }
        helper.assertTrue(salt == 2 && places == 3, "two salt and the flesh over three places, got " + salt + " salt, " + places + " places");
        helper.assertTrue(player.getInventory().countItem(Items.ROTTEN_FLESH) == 1, "one rotten flesh left");
        helper.succeed();
    }
}
