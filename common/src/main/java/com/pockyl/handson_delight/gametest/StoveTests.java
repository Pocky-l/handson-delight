package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import vectorwing.farmersdelight.common.block.AbstractStoveBlock;
import vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity;

import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.Grill;
import com.pockyl.handson_delight.station.StoveCooking;
import com.pockyl.handson_delight.station.StoveLogic;
import com.pockyl.handson_delight.station.StoveStation;
import com.pockyl.handson_delight.station.StoveTop;

import java.util.Collection;
import java.util.List;

/** The stove's rules against a real lit stove of Farmer's Delight, on every loader. */
public final class StoveTests {
    /** A side of beef: Farmer's Delight's 600 ticks at the station's pace, halved. */
    private static final int BEEF_SIDE = StoveCooking.sideTicks(600);

    private StoveTests() {
    }

    private record Top(Station station, StoveLogic logic, AbstractStoveBlockEntity stove, Player cook) {
        void act(String action, CompoundTag data) {
            logic.onCustomAction(station, cook, action, data);
        }

        void place(Item item, int count) {
            cook.inventoryMenu.setCarried(new ItemStack(item, count));
            act(StoveStation.ACTION_PLACE, new CompoundTag());
        }

        void at(String action, int place) {
            CompoundTag data = new CompoundTag();
            data.putInt(StoveStation.PLACE, place);
            act(action, data);
        }

        ItemStack on(int place) {
            return Platform.INSTANCE.getItem(stove, place);
        }

        Grill grill() {
            return StoveCooking.grill(stove);
        }
    }

    private static Top stove(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        helper.setBlock(at, FarmersDelightContent.STOVE.get().defaultBlockState().setValue(AbstractStoveBlock.LIT, true));
        BlockPos pos = helper.absolutePos(at);
        Station station = new Station(helper.getLevel(), pos);
        AbstractStoveBlockEntity stove = (AbstractStoveBlockEntity) helper.getLevel().getBlockEntity(pos);
        return new Top(station, new StoveLogic(station), stove, helper.makeMockPlayer());
    }

    public static void aPieceGrillsInHalvesAndWaitsToBeTurned(GameTestHelper helper) {
        Top top = stove(helper);
        top.place(Items.BEEF, 3);
        helper.assertTrue(top.on(0).is(Items.BEEF) && top.on(1).is(Items.BEEF) && top.on(2).is(Items.BEEF) && top.on(3).isEmpty(),
                "a stack goes on one by one, a piece a place");
        helper.assertTrue(top.cook.inventoryMenu.getCarried().isEmpty() && top.grill().byHand(0), "all three, laid by hand");
        helper.runAfterDelay(BEEF_SIDE + 10, () -> {
            helper.assertTrue(top.grill().waits(0) && top.grill().up(0) == 0.0F, "the lower side is done, the piece waits");
            top.at(StoveStation.ACTION_TURN, 0);
            helper.assertTrue(top.grill().up(0) >= 1.0F && top.grill().down(0) == 0.0F, "a click turns it over");
        });
        helper.runAfterDelay(2 * BEEF_SIDE + 25, () -> {
            helper.assertTrue(top.on(0).is(Items.COOKED_BEEF), "turned once, it is done and waits on the grill");
            helper.assertTrue(top.on(1).is(Items.BEEF) && top.grill().waits(1), "the others still wait to be turned");
            top.at(StoveStation.ACTION_TAKE, 0);
            helper.assertTrue(top.cook.getInventory().countItem(Items.COOKED_BEEF) == 1 && top.on(0).isEmpty(), "taken");
            helper.assertFalse(top.grill().byHand(0), "the place starts over");
            helper.succeed();
        });
    }

    /**
     * A player without this mod cannot turn or take what a cook laid: their use of the stove hands it to Farmer's Delight's way. A done
     * piece pops off at once, one waiting to be turned cooks in Farmer's Delight's time.
     */
    public static void piecesLaidByHandGoFarmersDelightsWay(GameTestHelper helper) {
        Top top = stove(helper);
        top.place(Items.BEEF, 2);
        helper.runAfterDelay(BEEF_SIDE + 10, () -> top.at(StoveStation.ACTION_TURN, 0));
        helper.runAfterDelay(2 * BEEF_SIDE + 25, () -> {
            helper.assertTrue(top.on(0).is(Items.COOKED_BEEF) && top.grill().waits(1), "one is done, the other waits to be turned");
            StoveCooking.handToFarmersDelight(top.stove);
            helper.assertFalse(top.grill().byHand(0) || top.grill().byHand(1), "both are Farmer's Delight's now");
        });
        helper.runAfterDelay(2 * BEEF_SIDE + 50, () -> {
            helper.assertTrue(top.on(0).isEmpty(), "the done one came off the grill");
            BlockPos pos = top.stove.getBlockPos();
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0),
                    item -> item.getItem().is(Items.COOKED_BEEF)).isEmpty(), "where the player can pick it up");
            helper.assertTrue(top.on(1).is(Items.BEEF) && ((StoveTop) top.stove).handson$progress()[1] > 10,
                    "the other cooks, counting Farmer's Delight's time");
            helper.succeed();
        });
    }

    public static void farmersDelightsWayStaysItsOwn(GameTestHelper helper) {
        Top top = stove(helper);
        top.stove.placeFood(top.cook, new ItemStack(Items.BEEF), 600);
        helper.assertTrue(top.on(0).is(Items.BEEF) && !top.grill().byHand(0), "Farmer's Delight's own right click: its way");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(((StoveTop) top.stove).handson$progress()[0] > 10, "and it counts its own time");
            top.place(Items.CHICKEN, 1);
            helper.assertTrue(top.on(1).is(Items.CHICKEN) && top.grill().byHand(1), "a piece laid at the station beside it is the cook's");
            helper.succeed();
        });
    }

    public static void onlyFoodGoesOnAnOpenGrill(GameTestHelper helper) {
        Top top = stove(helper);
        top.place(Items.STICK, 2);
        helper.assertTrue(top.on(0).isEmpty() && top.cook.inventoryMenu.getCarried().getCount() == 2, "no stick on the grill");
        top.place(Items.BEEF, 10);
        helper.assertTrue(top.cook.inventoryMenu.getCarried().getCount() == 10 - Grill.PLACES, "six places, six pieces");
        top.at(StoveStation.ACTION_TAKE, 4);
        CompoundTag one = new CompoundTag();
        one.putBoolean(StoveStation.ONE, true);
        one.putInt(StoveStation.PLACE, 4);
        top.cook.inventoryMenu.setCarried(new ItemStack(Items.PORKCHOP, 3));
        top.act(StoveStation.ACTION_PLACE, one);
        helper.assertTrue(top.on(4).is(Items.PORKCHOP) && top.cook.inventoryMenu.getCarried().getCount() == 2, "one onto the place picked");
        helper.setBlock(new BlockPos(1, 2, 1), FarmersDelightContent.COOKING_POT.get());
        helper.assertTrue(StoveCooking.isCovered(top.stove), "a pot on top covers the grill");
        top.place(Items.BEEF, 1);
        helper.assertTrue(top.cook.inventoryMenu.getCarried().getCount() == 1, "nothing goes onto a covered stove");
        helper.succeed();
    }

    /** An egg would run through the bars: the station keeps it off the grill, Farmer's Delight's own use still lays it. */
    /** Every stove of the tag, an addon's too, opens as the station, and grills on its own block entity. */
    public static void everyStoveOfTheTagIsAStation(GameTestHelper helper) {
        List<Block> stoves = new java.util.ArrayList<>();
        BuiltInRegistries.BLOCK.getTagOrEmpty(StoveStation.STOVES).forEach(holder -> stoves.add(holder.value()));
        helper.assertTrue(stoves.contains(FarmersDelightContent.STOVE.get()), "Farmer's Delight's stove is one");
        BlockPos at = new BlockPos(1, 1, 1);
        for (Block block : stoves) {
            BlockState state = block.defaultBlockState();
            helper.setBlock(at, state);
            helper.assertTrue(StoveStation.TYPE.matches(state), BuiltInRegistries.BLOCK.getKey(block) + " opens as the station");
            helper.assertTrue(helper.getBlockEntity(at) instanceof AbstractStoveBlockEntity,
                    BuiltInRegistries.BLOCK.getKey(block) + " grills on a stove's block entity");
        }
        helper.succeed();
    }

    /**
     * What a cook carries from station to station and the stove has no use for (a knife, a bowl, bread) opens the grill as an empty
     * hand does; what the stove uses (a flint and steel, a shovel, a water bucket) and blocks (a pot set on it) keep its own use.
     */
    public static void kitchenThingsOpenTheStove(GameTestHelper helper) {
        Top top = stove(helper);
        BlockPos pos = top.station.pos();
        BlockState state = helper.getLevel().getBlockState(pos);
        Item knife = BuiltInRegistries.ITEM.get(FarmersDelightContent.id("iron_knife"));
        Item pot = BuiltInRegistries.ITEM.get(FarmersDelightContent.id("cooking_pot"));
        for (ItemStack held : List.of(ItemStack.EMPTY, new ItemStack(Items.BEEF), new ItemStack(knife), new ItemStack(Items.BOWL),
                new ItemStack(Items.BREAD))) {
            top.cook.setItemInHand(InteractionHand.MAIN_HAND, held);
            helper.assertTrue(StoveStation.opens(top.cook, pos, state), held + " opens the grill");
        }
        for (ItemStack held : List.of(new ItemStack(Items.FLINT_AND_STEEL), new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.WATER_BUCKET),
                new ItemStack(pot), new ItemStack(Items.COBBLESTONE))) {
            top.cook.setItemInHand(InteractionHand.MAIN_HAND, held);
            helper.assertFalse(StoveStation.opens(top.cook, pos, state), held + " keeps the stove's own use");
        }
        helper.succeed();
    }

    public static void anEggStaysOffTheGrill(GameTestHelper helper) {
        Top top = stove(helper);
        top.place(Items.EGG, 3);
        helper.assertTrue(top.on(0).isEmpty() && top.cook.inventoryMenu.getCarried().getCount() == 3, "no egg on the grill");
        helper.assertTrue(top.station.effects.contains(StoveStation.EFFECT_REFUSED), "it bounces back to the hand");
        top.stove.placeFood(top.cook, new ItemStack(Items.EGG), 600);
        helper.assertTrue(top.on(0).is(Items.EGG) && !top.grill().byHand(0), "Farmer's Delight's right click is its own way");
        boolean eggRecipe = false;
        for (CampfireCookingRecipe recipe : helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING)) {
            Ingredient food = recipe.getIngredients().get(0);
            if (food.test(new ItemStack(Items.EGG))) {
                eggRecipe = true;
                helper.assertFalse(StoveCooking.grillable(food).test(new ItemStack(Items.EGG)), "the book shows no egg");
            }
            if (food.test(new ItemStack(Items.BEEF))) {
                helper.assertTrue(StoveCooking.grillable(food).test(new ItemStack(Items.BEEF)), "the book keeps the beef");
            }
        }
        helper.assertTrue(eggRecipe, "Farmer's Delight fries an egg over the fire");
        helper.succeed();
    }

    /** The stove's station as the library would hold it, without a player at it. */
    private static final class Station implements StationContext {
        private final Level level;
        private final BlockPos pos;
        private final StationState state = new StationState(StoveStation.SCHEMA);

        Station(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        @Override
        public StationType type() {
            return StoveStation.TYPE;
        }

        @Override
        public Level level() {
            return level;
        }

        @Override
        public BlockPos pos() {
            return pos;
        }

        @Override
        public BlockState blockState() {
            return level.getBlockState(pos);
        }

        @Override
        public Direction facing() {
            return Direction.NORTH;
        }

        @Override
        public StationState state() {
            return state;
        }

        @Override
        public Collection<? extends Player> users() {
            return List.of();
        }

        /** The effects sent, for the tests to look at. */
        final java.util.List<ResourceLocation> effects = new java.util.ArrayList<>();

        @Override
        public void effect(ResourceLocation effect, String node, CompoundTag data) {
            effects.add(effect);
        }
    }
}
