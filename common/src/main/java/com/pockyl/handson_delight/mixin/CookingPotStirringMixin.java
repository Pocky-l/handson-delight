package com.pockyl.handson_delight.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.PotLogic;
import com.pockyl.handson_delight.station.PotStirring;
import com.pockyl.handson_delight.station.StirSkill;

/**
 * Cooking moves on only while the pot is stirred; left alone it keeps how far it got. Everything in the pot cooks as one batch and
 * comes out at once, a big batch taking longer by the square root, so that nobody stirs for an hour. How well it was stirred adds
 * servings. That is cooking by hand: food a cook put in at the station waits for the spoon while the cook is there. Food that came in
 * by itself (a hopper, a pipe, an autocrafter, Farmer's Delight's own menu on a client without this mod) cooks as Farmer's Delight
 * cooks it, a serving at a time at its own pace, so that automation in a modpack goes on working; so does a cook's food once they
 * walked away, since a pot is something you fill and leave. So do pots of other mods built on this class.
 */
@Mixin(value = CookingPotBlockEntity.class, remap = false)
abstract class CookingPotStirringMixin implements PotStirring {
    @Shadow
    private int cookTime;
    @Shadow
    private int cookTimeTotal;
    @Unique
    private final StirSkill handson$skill = new StirSkill();
    @Unique
    private long handson$stirredUntil;
    @Unique
    private int handson$grade;
    /** The servings the stars of the last batch added. */
    @Unique
    private int handson$bonus;
    /** The batch the cooking time was counted for; the part of a tick of cooking the speed left over. */
    @Unique
    private int handson$batch;
    @Unique
    private float handson$saved;
    /** How fast the cooking time ran on the last tick: the batch's speed, 1 the Farmer's Delight way, 0 not known yet. */
    @Unique
    private float handson$pace;
    /** On while the rest of a batch is made through Farmer's Delight's own code. */
    @Unique
    private boolean handson$serving;
    /**
     * The recipe a stirred batch cooks this tick, and how big its meal was before: when the meal grew, a serving was made and the rest of
     * the batch follows. Read after the cooking step, not at its return: another mod may end the step early (Autochef's Delight uses up
     * the ingredients itself and returns).
     */
    @Unique
    private CookingPotRecipe handson$cooking;
    @Unique
    private int handson$mealBefore;
    /** This serving cooks Farmer's Delight's way: no batch, no stars. */
    @Unique
    private boolean handson$farmersDelightsWay;
    /** The food in the pot was put in by a cook at the station: it waits for the spoon. Saved with the pot. */
    @Unique
    private boolean handson$byHand;
    /** The last tick a cook was at the station, and how many ingredients the pot held at the last tick (-1: not looked yet). */
    @Unique
    private long handson$attendedAt = Long.MIN_VALUE / 2;
    @Unique
    private int handson$fed;
    /** A cook put food in at the station since the pot's last tick. */
    @Unique
    private boolean handson$handFed;
    /** Sets of ingredients the last batch did not need, the stack of the meal being full: they go back to the cook. */
    @Unique
    private int handson$setsSaved;
    /** The part of a bonus serving the batches before earned, carried on to the next. Saved with the pot. */
    @Unique
    private float handson$bonusCarried;
    @Unique
    private static final String BY_HAND = HandsOnDelight.MOD_ID + ":by_hand";
    @Unique
    private static final String BONUS = HandsOnDelight.MOD_ID + ":bonus";
    /** Ticks after the cook left the station from which their food cooks without them, as Farmer's Delight cooks it. */
    @Unique
    private static final int AWAY = 40;
    /** A cook attended within this many ticks is at the station now: it ticks every tick, with one tick to spare. */
    @Unique
    private static final int HERE = 2;

    @Shadow
    private boolean processCooking(CookingPotRecipe recipe, CookingPotBlockEntity pot) {
        throw new AssertionError();
    }

    @Override
    public void handson$stir(long until) {
        handson$stirredUntil = until;
    }

    @Override
    public boolean handson$isStirred(long gameTime) {
        return gameTime < handson$stirredUntil;
    }

    @Override
    public StirSkill handson$skill() {
        return handson$skill;
    }

    @Override
    public int handson$grade() {
        return handson$grade;
    }

    @Override
    public int handson$bonus() {
        return handson$bonus;
    }

    @Override
    public int handson$takeSaved() {
        int saved = handson$setsSaved;
        handson$setsSaved = 0;
        return saved;
    }

    @Override
    public void handson$attend(long gameTime) {
        handson$attendedAt = gameTime;
    }

    @Override
    public void handson$feedByHand() {
        handson$handFed = true;
    }

    @Override
    public void handson$watchFood(long gameTime) {
        CookingPotBlockEntity pot = (CookingPotBlockEntity) (Object) this;
        int count = 0;
        for (int slot = 0; slot < CookingPotBlockEntity.MEAL_DISPLAY_SLOT; slot++) {
            count += Platform.INSTANCE.getItem(pot, slot).getCount();
        }
        if (handson$fed >= 0 && count > handson$fed) {
            // Food came in: from a cook at the station, or by itself (a hopper, a pipe, a menu), also while a cook stands there.
            handson$byHand = handson$handFed;
        }
        handson$handFed = false;
        handson$fed = count;
        handson$ejectForgotten(gameTime);
    }

    @Inject(method = "cookingTick", at = @At("HEAD"))
    private static void handson$seeWhoFeedsIt(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity pot, CallbackInfo callback) {
        ((PotStirring) pot).handson$watchFood(level.getGameTime());
    }

    /** Containers emptied into the pot while a cook is at it, for the station to hand over. */
    @Unique
    private final java.util.List<ItemStack> handson$leftovers = new java.util.ArrayList<>();
    @Unique
    private boolean handson$ejecting;

    @Shadow
    protected abstract void ejectIngredientRemainder(ItemStack remainder);

    /**
     * With a cook at the station, what the ingredients leave goes to them: no bucket lands on the floor beside the pot. Only while the cook
     * is there: what is kept is not saved with the pot, and a pot broken before anyone took it would lose it.
     */
    @Inject(method = "ejectIngredientRemainder", at = @At("HEAD"), cancellable = true)
    private void handson$keepLeftover(ItemStack remainder, CallbackInfo callback) {
        CookingPotBlockEntity pot = (CookingPotBlockEntity) (Object) this;
        if (!handson$ejecting && pot.getLevel() != null && pot.getLevel().getGameTime() - handson$attendedAt <= HERE) {
            handson$leftovers.add(remainder.copy());
            callback.cancel();
        }
    }

    /** Leftovers nobody took (the cook left that very tick) go out as Farmer's Delight would throw them. */
    @Unique
    private void handson$ejectForgotten(long gameTime) {
        if (handson$leftovers.isEmpty() || gameTime - handson$attendedAt <= HERE) {
            return;
        }
        handson$ejecting = true;
        handson$leftovers.forEach(this::ejectIngredientRemainder);
        handson$ejecting = false;
        handson$leftovers.clear();
    }

    @Override
    public java.util.List<ItemStack> handson$takeLeftovers() {
        java.util.List<ItemStack> taken = java.util.List.copyOf(handson$leftovers);
        handson$leftovers.clear();
        return taken;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void handson$saveByHand(CompoundTag tag, CallbackInfo callback) {
        if (handson$byHand) {
            tag.putBoolean(BY_HAND, true);
        }
        if (handson$bonusCarried > 0.0F) {
            tag.putFloat(BONUS, handson$bonusCarried);
        }
    }

    @Inject(method = "load", at = @At("TAIL"), remap = true)
    private void handson$loadByHand(CompoundTag tag, CallbackInfo callback) {
        handson$byHand = tag.getBoolean(BY_HAND);
        handson$bonusCarried = tag.getFloat(BONUS);
        // What a loaded pot holds was there before: only what comes in from now on says who feeds it.
        handson$fed = -1;
    }

    @Inject(method = "processCooking", at = @At("HEAD"), cancellable = true)
    private void handson$waitForTheSpoon(CookingPotRecipe recipe, CookingPotBlockEntity pot, CallbackInfoReturnable<Boolean> callback) {
        if (pot.getLevel() == null || handson$serving || !PotLogic.isOurs(pot)) {
            return;
        }
        boolean stirred = handson$isStirred(pot.getLevel().getGameTime());
        boolean attended = pot.getLevel().getGameTime() - handson$attendedAt <= AWAY;
        handson$farmersDelightsWay = !stirred
                && (!handson$byHand || !attended || !HandsOnConfig.stirringRequired() || PotLogic.isInMenu(pot));
        handson$cooking = null;
        if (handson$farmersDelightsWay) {
            // Fed by automation, worked in Farmer's Delight's own menu or left by the cook: its own way, a serving at a time.
            handson$keepTimeAt(1.0F);
            handson$batch = 0;
            return;
        }
        int batch = PotLogic.batch(pot, recipe);
        handson$keepTimeAt(PotLogic.speed(batch));
        handson$batch = batch;
        if (stirred) {
            // The pot adds one itself; a big batch takes some back.
            handson$saved += PotLogic.speed(batch) - 1.0F;
            int whole = (int) handson$saved;
            handson$saved -= whole;
            cookTime += whole;
            handson$cooking = recipe;
            handson$mealBefore = pot.getMeal().getCount();
        } else {
            callback.setReturnValue(false);
        }
    }

    /**
     * More food thrown in takes longer, less takes shorter, and a pot left to cook the Farmer's Delight way runs at its own pace: the
     * time already cooked stays what it was. Without this a serving's worth of time cooked alone counted as the same share of a batch.
     */
    @Unique
    private void handson$keepTimeAt(float pace) {
        if (handson$pace > 0.0F && pace != handson$pace && cookTimeTotal > 0) {
            cookTime = Math.min(cookTimeTotal - 1, Math.round(cookTime * pace / handson$pace));
        }
        handson$pace = pace;
    }

    @Inject(method = "cookingTick", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lvectorwing/farmersdelight/common/block/entity/"
            + "CookingPotBlockEntity;processCooking(Lvectorwing/farmersdelight/common/crafting/CookingPotRecipe;"
            + "Lvectorwing/farmersdelight/common/block/entity/CookingPotBlockEntity;)Z"))
    private static void handson$afterTheStep(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity pot, CallbackInfo callback) {
        ((PotStirring) pot).handson$afterCooking();
    }

    @Override
    public void handson$afterCooking() {
        CookingPotRecipe recipe = handson$cooking;
        handson$cooking = null;
        CookingPotBlockEntity pot = (CookingPotBlockEntity) (Object) this;
        if (recipe == null || handson$serving || pot.getLevel() == null || !PotLogic.isOurs(pot)) {
            return;
        }
        ItemStack result = recipe.getResultItem(pot.getLevel().registryAccess());
        ItemStack meal = pot.getMeal();
        if (result.isEmpty() || !ItemStack.isSameItemSameTags(meal, result) || meal.getCount() <= handson$mealBefore) {
            return;
        }
        handson$serveTheBatch(recipe, pot, result, meal);
    }

    /** Farmer's Delight made one serving: the rest of the batch follows at once, and what the stirring earned with it. */
    @Unique
    private void handson$serveTheBatch(CookingPotRecipe recipe, CookingPotBlockEntity pot, ItemStack result, ItemStack meal) {
        // The batch as HEAD counted it, before Farmer's Delight's serving: a slot that held the last item is empty now, and a count
        // made now would leave it out and cook the rest without it.
        // Farmer's Delight lets the meal grow past its stack size: the serving it made is then the only one.
        int room = Math.max(1, (result.getMaxStackSize() - meal.getCount()) / result.getCount() + 1);
        int left = Math.min(room, handson$batch);
        int grade = handson$skill.grade(pot.getLevel().getGameTime());
        handson$grade = grade;
        // The most servings the room takes, out of as few ingredients as that needs: what is saved stays in the pot.
        int cooked = 1;
        int servings = Math.min(room, 1 + PotLogic.bonus(1, grade, handson$bonusCarried));
        for (int batch = 2; batch <= left; batch++) {
            int made = Math.min(room, batch + PotLogic.bonus(batch, grade, handson$bonusCarried));
            if (made > servings) {
                cooked = batch;
                servings = made;
            }
        }
        handson$serving = true;
        try {
            for (int i = 1; i < cooked; i++) {
                cookTime = cookTimeTotal - 1;
                processCooking(recipe, pot);
            }
        } finally {
            handson$serving = false;
        }
        if (servings > cooked) {
            pot.getMeal().grow((servings - cooked) * result.getCount());
        }
        handson$bonus = servings - cooked;
        // A full stack needed fewer sets than the batch had: the stars saved the rest, for the cook at the station. With the cook gone
        // they stay in the pot and cook next, and nobody opening it later is handed what then lies there.
        boolean here = pot.getLevel().getGameTime() - handson$attendedAt <= HERE;
        handson$setsSaved = here ? Math.max(0, left - cooked) : 0;
        float earned = handson$bonusCarried + cooked * PotLogic.bonusRate(grade) - (servings - cooked);
        handson$bonusCarried = Mth.clamp(earned, 0.0F, 0.999F);
        cookTime = 0;
        handson$saved = 0.0F;
        handson$batch = 0;
    }
}
