package com.pockyl.handson_delight.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;

import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.Frying;
import com.pockyl.handson_delight.station.SkilletCooking;
import com.pockyl.handson_delight.station.SkilletLogic;
import com.pockyl.handson_delight.station.SkilletStation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** The skillet's rules against a real skillet of Farmer's Delight on a lit campfire, on every loader. */
public final class SkilletTests {
    /** One beef fries a side in 40 ticks, two in 57: Farmer's Delight's 120 at the station's pace. */
    private static final int TWO_BEEF_SIDE = SkilletCooking.sideTicks(120, 2, 1.0F);

    private SkilletTests() {
    }

    private record Pan(Station station, SkilletLogic logic, SkilletBlockEntity skillet, Player cook) {
        void act(String action, CompoundTag data) {
            logic.onCustomAction(station, cook, action, data);
        }

        void place(Item item, int count) {
            cook.inventoryMenu.setCarried(new ItemStack(item, count));
            act(SkilletStation.ACTION_PLACE, new CompoundTag());
        }

        void toss() {
            act(SkilletStation.ACTION_TOSS, new CompoundTag());
        }

        Frying frying() {
            return SkilletCooking.frying(skillet);
        }

        int jumpedOut(Item item) {
            return station.level.getEntitiesOfClass(ItemEntity.class, new AABB(station.pos).inflate(3)).stream()
                    .filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
        }
    }

    private static Pan pan(GameTestHelper helper) {
        BlockPos fire = new BlockPos(1, 1, 1);
        helper.setBlock(fire, Blocks.CAMPFIRE);
        helper.setBlock(fire.above(), FarmersDelightContent.SKILLET.get());
        BlockPos pos = helper.absolutePos(fire.above());
        Station station = new Station(helper.getLevel(), pos);
        SkilletBlockEntity skillet = (SkilletBlockEntity) helper.getLevel().getBlockEntity(pos);
        // The empty template is smaller than the area counted: food an earlier batch left in the same spot would count as jumped out.
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).forEach(ItemEntity::discard);
        return new Pan(station, new SkilletLogic(station), skillet, helper.makeMockPlayer(GameType.CREATIVE));
    }

    public static void aBatchFriesInHalvesAndWaitsToBeTurned(GameTestHelper helper) {
        Pan pan = pan(helper);
        pan.place(Items.BEEF, 2);
        helper.assertTrue(pan.skillet.getStoredStack().getCount() == 2 && pan.cook.inventoryMenu.getCarried().isEmpty(),
                "both go into the pan");
        helper.assertTrue(pan.frying().byHand(), "put in at the station, it fries by hand");
        helper.runAfterDelay(TWO_BEEF_SIDE + 10, () -> {
            helper.assertTrue(pan.frying().down() >= 1.0F && pan.frying().up() == 0.0F, "the side on the pan is done, the top raw");
            helper.assertTrue(pan.frying().waits(), "it waits to be turned");
        });
        helper.runAfterDelay(2 * TWO_BEEF_SIDE + 20, () -> {
            helper.assertTrue(pan.skillet.getStoredStack().is(Items.BEEF) && pan.skillet.getStoredStack().getCount() == 2,
                    "untouched, nothing more happens: it is not done");
            pan.toss();
            helper.assertTrue(pan.frying().up() >= 1.0F && pan.frying().down() == 0.0F, "a toss turns it over");
        });
        helper.runAfterDelay(3 * TWO_BEEF_SIDE + 35, () -> {
            helper.assertTrue(pan.skillet.getStoredStack().is(Items.COOKED_BEEF) && pan.skillet.getStoredStack().getCount() == 2,
                    "both sides done: both are steaks at once, waiting in the pan");
            helper.assertTrue(pan.jumpedOut(Items.COOKED_BEEF) == 0, "nothing jumps out: the cook takes it");
        });
        helper.runAfterDelay(3 * TWO_BEEF_SIDE + 60, () -> {
            helper.assertTrue(pan.skillet.getStoredStack().is(Items.COOKED_BEEF), "still there a while later");
            pan.act(SkilletStation.ACTION_TAKE, new CompoundTag());
            helper.assertTrue(pan.cook.getInventory().countItem(Items.COOKED_BEEF) == 2 && pan.skillet.getStoredStack().isEmpty(),
                    "taken into the inventory");
            helper.assertFalse(pan.frying().byHand(), "an empty pan starts over");
            pan.place(Items.BEEF, 20);
            helper.assertTrue(pan.skillet.getStoredStack().getCount() == SkilletCooking.BATCH
                    && pan.cook.inventoryMenu.getCarried().getCount() == 20 - SkilletCooking.BATCH, "a batch at most goes in");
            helper.succeed();
        });
    }

    /**
     * Every campfire recipe the game has, each thing it takes, goes into the pan at the station: the skillet's book lists every campfire
     * recipe, so a page whose food the pan refused would be one the cook cannot make (the stage 3 audit of the Culinary Compat plan).
     */
    public static void everyCampfireFoodGoesInThePan(GameTestHelper helper) {
        Pan pan = pan(helper);
        Level level = helper.getLevel();
        List<String> refused = new ArrayList<>();
        for (RecipeHolder<CampfireCookingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING)) {
            CampfireCookingRecipe recipe = holder.value();
            if (recipe.getIngredients().isEmpty() || recipe.getResultItem(level.registryAccess()).isEmpty()) {
                continue;
            }
            for (ItemStack food : recipe.getIngredients().get(0).getItems()) {
                if (food.isEmpty() || food.is(Items.BARRIER)) {
                    continue;
                }
                if (!SkilletCooking.takes(pan.skillet, food.copyWithCount(1))
                        || SkilletCooking.recipe(level, food).map(found -> found.getResultItem(level.registryAccess()).isEmpty()).orElse(true)) {
                    refused.add(holder.id() + " (" + food.getItem() + ")");
                }
            }
        }
        helper.assertTrue(refused.isEmpty(), refused.size() + " refused by the pan: " + refused);
        helper.succeed();
    }

    /** An egg fries sunny side up: through from below in the time both sides of a steak take, never waiting for a toss. */
    public static void anEggFriesWithoutATurn(GameTestHelper helper) {
        Pan pan = pan(helper);
        pan.place(Items.EGG, 2);
        helper.assertTrue(pan.skillet.getStoredStack().is(Items.EGG) && pan.skillet.getStoredStack().getCount() == 2,
                "both eggs go into the pan, got " + pan.skillet.getStoredStack());
        helper.runAfterDelay(TWO_BEEF_SIDE + 5, () -> {
            helper.assertTrue(pan.skillet.getStoredStack().is(Items.EGG), "past one side's time it still fries");
            helper.assertFalse(pan.frying().waits(), "and does not wait to be turned");
            helper.assertTrue(pan.frying().up() == pan.frying().down() && pan.frying().down() > 0.4F, "it cooks through from below");
        });
        helper.runAfterDelay(2 * TWO_BEEF_SIDE + 10, () -> {
            ItemStack inPan = pan.skillet.getStoredStack();
            helper.assertTrue(inPan.getCount() == 2 && !inPan.is(Items.EGG), "done in the time of both sides, never tossed, got " + inPan);
            helper.succeed();
        });
    }

    /**
     * Fried food waits in the pan as it is, even when the campfire makes something else of it: with More Delight a fried egg cooks into
     * an omelette, and an egg fried at the station must stay a fried egg (GH #33). Without More Delight it checks the same on a steak.
     */
    public static void friedFoodFriesNoFurther(GameTestHelper helper) {
        Pan pan = pan(helper);
        boolean moreDelight = Platform.INSTANCE.isLoaded("moredelight");
        pan.place(moreDelight ? Items.EGG : Items.BEEF, 2);
        int done = moreDelight ? 2 * TWO_BEEF_SIDE + 10 : TWO_BEEF_SIDE + 10;
        if (!moreDelight) {
            helper.runAfterDelay(done, pan::toss);
            done = 2 * TWO_BEEF_SIDE + 25;
        }
        int ready = done;
        helper.runAfterDelay(ready, () -> {
            ItemStack inPan = pan.skillet.getStoredStack();
            helper.assertTrue(inPan.getCount() == 2 && !inPan.is(Items.EGG) && !inPan.is(Items.BEEF), "fried, got " + inPan);
            helper.assertTrue(pan.frying().ready(), "it waits for the cook");
            helper.assertTrue(SkilletCooking.isDone(helper.getLevel(), inPan, pan.frying().ready()), "and is done");
        });
        int next = moreDelight ? 2 * SkilletCooking.sideTicks(SkilletCooking.skilletTicks(helper.getLevel(),
                new ItemStack(BuiltInRegistries.ITEM.get(FarmersDelightContent.id("fried_egg"))), 0), 2, 1.0F) : 40;
        helper.runAfterDelay(ready + next + 20, () -> {
            ItemStack inPan = pan.skillet.getStoredStack();
            helper.assertTrue(inPan.getCount() == 2 && (!moreDelight || inPan.is(BuiltInRegistries.ITEM.get(FarmersDelightContent.id("fried_egg")))),
                    "past what the next recipe would take, it is still what was fried, got " + inPan);
            helper.assertTrue(pan.frying().down() == 0.0F && pan.frying().up() == 0.0F, "and fries no further");
            pan.toss();
            helper.assertTrue(pan.frying().ready() && pan.frying().down() == 0.0F, "a toss does not start it over");
            pan.place(inPan.getItem(), 1);
            helper.assertTrue(pan.skillet.getStoredStack().getCount() == 2, "nothing more goes into a pan of done food");
            pan.act(SkilletStation.ACTION_TAKE, new CompoundTag());
            helper.assertTrue(pan.skillet.getStoredStack().isEmpty() && !pan.frying().ready(), "taken, the pan starts over");
            helper.succeed();
        });
    }

    /**
     * Food a cook put in waits for a toss; with the station turned off nobody can toss it, so Farmer's Delight cooks it its way. Runs in
     * a batch of its own: the switch is the whole game's.
     */
    public static void aStationTurnedOffHandsThePanToFarmersDelight(GameTestHelper helper) {
        Pan pan = pan(helper);
        pan.place(Items.BEEF, 1);
        helper.assertTrue(pan.frying().byHand(), "put in at the station, it fries by hand");
        HandsOnConfig.setSkillet(false);
        helper.runAfterDelay(140, () -> {
            HandsOnConfig.setSkillet(true);
            helper.assertFalse(pan.frying().byHand(), "with the station off it is Farmer's Delight's");
            helper.assertTrue(pan.skillet.getStoredStack().isEmpty() && pan.jumpedOut(Items.COOKED_BEEF) == 1,
                    "cooked in its own time and out of the pan, never tossed");
            helper.succeed();
        });
    }

    /** The skillet's cooking time in the config: 1.0 fries a side in today's time, 2.0 in twice it. Both steps in this one tick. */
    public static void theFryingTimeFollowsTheConfig(GameTestHelper helper) {
        Pan pan = pan(helper);
        pan.place(Items.BEEF, 1);
        float pot = HandsOnConfig.cookingPotTime();
        float skillet = HandsOnConfig.skilletTime();
        float stove = HandsOnConfig.stoveTime();
        float[] steps = new float[2];
        try {
            for (int i = 0; i < 2; i++) {
                HandsOnConfig.setCookingTimes(pot, i + 1.0F, stove);
                float before = pan.frying().down();
                SkilletCooking.fry(pan.skillet, pan.skillet.getStoredStack());
                steps[i] = pan.frying().down() - before;
            }
        } finally {
            HandsOnConfig.setCookingTimes(pot, skillet, stove);
        }
        // Farmer's Delight's skillet takes 120 ticks for beef: a side is 40 at the station's pace.
        int side = SkilletCooking.sideTicks(120, 1, 1.0F);
        helper.assertTrue(side == 40 && Math.abs(steps[0] - 1.0F / side) < 1.0E-6F, "1.0: a side in today's 40 ticks, got " + steps[0]);
        helper.assertTrue(Math.abs(steps[0] - 2.0F * steps[1]) < 1.0E-6F, "2.0: half as far a tick, " + steps[1]);
        helper.succeed();
    }

    public static void farmersDelightsRightClickCooksItsWay(GameTestHelper helper) {
        Pan pan = pan(helper);
        pan.skillet.addItemToCook(new ItemStack(Items.BEEF), pan.cook);
        helper.assertTrue(pan.skillet.getStoredStack().is(Items.BEEF), "Farmer's Delight takes it");
        helper.assertFalse(pan.frying().byHand(), "and cooks it");
        helper.runAfterDelay(130, () -> {
            helper.assertTrue(pan.skillet.getStoredStack().isEmpty() && pan.jumpedOut(Items.COOKED_BEEF) == 1,
                    "done in its own time, never waiting to be turned");
            helper.succeed();
        });
    }

    public static void theFoodComesBackAndOnlyFoodGoesIn(GameTestHelper helper) {
        Pan pan = pan(helper);
        pan.place(Items.STICK, 3);
        helper.assertTrue(pan.skillet.getStoredStack().isEmpty(), "a stick is no food for the pan");
        helper.assertTrue(pan.cook.inventoryMenu.getCarried().getCount() == 3, "it stays in the hand");
        pan.cook.inventoryMenu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(SkilletCooking.takes(pan.skillet, new ItemStack(Items.CHICKEN)), "opened by use with chicken, it goes in");
        pan.place(Items.CHICKEN, 3);
        helper.assertFalse(SkilletCooking.takes(pan.skillet, new ItemStack(Items.BEEF)), "opened by use with other food, it stays in the hand");
        pan.cook.inventoryMenu.setCarried(new ItemStack(Items.BEEF));
        pan.act(SkilletStation.ACTION_PLACE, new CompoundTag());
        helper.assertTrue(pan.skillet.getStoredStack().is(Items.CHICKEN) && pan.cook.inventoryMenu.getCarried().is(Items.BEEF),
                "one kind at a time");
        pan.cook.inventoryMenu.setCarried(ItemStack.EMPTY);
        pan.cook.getInventory().setItem(5, new ItemStack(Items.CHICKEN, 2));
        CompoundTag shift = new CompoundTag();
        shift.putInt(SkilletStation.FROM, 5);
        pan.act(SkilletStation.ACTION_PLACE, shift);
        helper.assertTrue(pan.skillet.getStoredStack().getCount() == 5 && pan.cook.getInventory().getItem(5).isEmpty(),
                "shift + click in the inventory puts the whole stack in");
        pan.toss();
        helper.assertTrue(pan.frying().byHand(), "tossed");
        pan.act(SkilletStation.ACTION_TAKE, new CompoundTag());
        helper.assertTrue(pan.skillet.getStoredStack().isEmpty(), "taken out");
        helper.assertTrue(pan.cook.getInventory().countItem(Items.CHICKEN) == 5, "all of it, into the inventory");
        helper.assertFalse(pan.frying().byHand(), "the pan starts over");
        pan.place(Items.CHICKEN, SkilletCooking.BATCH);
        helper.assertFalse(SkilletCooking.takes(pan.skillet, new ItemStack(Items.CHICKEN)), "opened by use at a full pan, it stays in the hand");
        helper.succeed();
    }

    public static void moreFoodTakesLongerButKeepsWhatWasDone(GameTestHelper helper) {
        Pan pan = pan(helper);
        pan.place(Items.BEEF, 1);
        helper.runAfterDelay(20, () -> {
            float one = pan.frying().down();
            helper.assertTrue(one > 0.3F, "one beef is well on its way");
            pan.place(Items.BEEF, 3);
            helper.assertTrue(Math.abs(pan.frying().down() - one) < 0.001F, "until the next tick nothing changes");
        });
        helper.runAfterDelay(21, () -> {
            float four = pan.frying().down();
            helper.assertTrue(four < 0.3F && four > 0.15F, "four fry slower: how far it got is shared out, not lost");
            helper.succeed();
        });
    }

    /** The skillet's station as the library would hold it, without a player at it. */
    private static final class Station implements StationContext {
        private final Level level;
        private final BlockPos pos;
        private final StationState state = new StationState(SkilletStation.SCHEMA);

        Station(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        @Override
        public StationType type() {
            return SkilletStation.TYPE;
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

        @Override
        public void effect(ResourceLocation effect, String node, CompoundTag data) {
        }
    }
}
