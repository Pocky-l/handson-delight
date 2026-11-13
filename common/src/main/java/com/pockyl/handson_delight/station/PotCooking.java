package com.pockyl.handson_delight.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.HandsOnDelight;

import java.util.ArrayList;
import java.util.List;

/**
 * Cooking moves on only while the pot is stirred; left alone it keeps how far it got. Everything in the pot cooks as one batch and
 * comes out at once, a big batch taking longer by the square root, so that nobody stirs for an hour. How well it was stirred adds
 * servings. That is cooking by hand: food a cook put in at the station waits for the spoon while the cook is there. Food that came in
 * by itself (a hopper, a pipe, an autocrafter, the pot's own menu on a client without this mod) cooks as the pot cooks it, a serving
 * at a time at its own pace, so that automation in a modpack goes on working; so does a cook's food once they walked away, since a pot
 * is something you fill and leave. Each pot's mixin calls this from its own cooking code.
 */
public final class PotCooking {
    private static final String BY_HAND = HandsOnDelight.MOD_ID + ":by_hand";
    private static final String BONUS = HandsOnDelight.MOD_ID + ":bonus";
    /** Ticks after the cook left the station from which their food cooks without them, as the pot cooks it. */
    private static final int AWAY = 40;
    /** A cook attended within this many ticks is at the station now: it ticks every tick, with one tick to spare. */
    private static final int HERE = 2;

    private final KitchenPot pot;
    private final StirSkill skill = new StirSkill();
    private long stirredUntil;
    private int grade;
    /** The servings the stars of the last batch added. */
    private int bonus;
    /** The batch the cooking time was counted for; the part of a tick of cooking the speed left over. */
    private int batch;
    private float saved;
    /** How fast the cooking time ran on the last tick: the batch's speed, 1 the pot's own way, 0 not known yet. */
    private float pace;
    /** On while the rest of a batch is made through the pot's own code. */
    private boolean serving;
    /**
     * The recipe a stirred batch cooks this tick, and how big its meal was before: when the meal grew, a serving was made and the rest of
     * the batch follows. Read after the cooking step, not at its return: another mod may end the step early (Autochef's Delight uses up
     * the ingredients itself and returns).
     */
    private CookingPotRecipe cooking;
    private int mealBefore;
    /** The food in the pot was put in by a cook at the station: it waits for the spoon. Saved with the pot. */
    private boolean byHand;
    /** The last tick a cook was at the station, and how many ingredients the pot held at the last tick (-1: not looked yet). */
    private long attendedAt = Long.MIN_VALUE / 2;
    private int fed;
    /** A cook put food in at the station since the pot's last tick. */
    private boolean handFed;
    /** Sets of ingredients the last batch did not need, the stack of the meal being full: they go back to the cook. */
    private int setsSaved;
    /** The part of a bonus serving the batches before earned, carried on to the next. Saved with the pot. */
    private float bonusCarried;
    /** Containers emptied into the pot while a cook is at it, for the station to hand over. */
    private final List<ItemStack> leftovers = new ArrayList<>();
    private boolean throwingOut;

    public PotCooking(KitchenPot pot) {
        this.pot = pot;
    }

    /** The pot counts as stirred until the game time {@code until}. */
    public void stir(long until) {
        stirredUntil = until;
    }

    public boolean isStirred(long gameTime) {
        return gameTime < stirredUntil;
    }

    public StirSkill skill() {
        return skill;
    }

    /** The stars the last batch was done with. */
    public int grade() {
        return grade;
    }

    /** The servings the stars of the last batch added. */
    public int bonus() {
        return bonus;
    }

    /** Sets of ingredients the last batch saved, once: they are the cook's again. */
    public int takeSaved() {
        int taken = setsSaved;
        setsSaved = 0;
        return taken;
    }

    /** A cook works at the pot's station in this tick. */
    public void attend(long gameTime) {
        attendedAt = gameTime;
    }

    /** A cook put food into the pot at the station: what comes in before the pot's next tick is put in by hand. */
    public void feedByHand() {
        handFed = true;
    }

    private Level level() {
        return pot.handson$blockEntity().getLevel();
    }

    private boolean attended() {
        return level() != null && level().getGameTime() - attendedAt <= AWAY;
    }

    private boolean here() {
        return level() != null && level().getGameTime() - attendedAt <= HERE;
    }

    /** Every tick of the pot, before it cooks: sees whether food came in, and whether by hand or by itself (a hopper, a pipe, a menu). */
    public void watchFood(long gameTime) {
        int count = 0;
        for (int slot = 0; slot < pot.handson$slots(); slot++) {
            count += pot.handson$getItem(slot).getCount();
        }
        if (fed >= 0 && count > fed) {
            // Food came in: from a cook at the station, or by itself (a hopper, a pipe, a menu), also while a cook stands there.
            byHand = handFed;
        }
        handFed = false;
        fed = count;
        throwOutForgotten(gameTime);
    }

    /**
     * With a cook at the station, what the ingredients leave goes to them: no bucket lands on the floor beside the pot. Only while the cook
     * is there: what is kept is not saved with the pot, and a pot broken before anyone took it would lose it.
     */
    public boolean keepLeftover(ItemStack remainder) {
        if (throwingOut || !here()) {
            return false;
        }
        leftovers.add(remainder.copy());
        return true;
    }

    /** Leftovers nobody took (the cook left that very tick) go out as the pot would throw them. */
    private void throwOutForgotten(long gameTime) {
        if (leftovers.isEmpty() || gameTime - attendedAt <= HERE) {
            return;
        }
        throwingOut = true;
        try {
            leftovers.forEach(pot::handson$throwOut);
        } finally {
            throwingOut = false;
        }
        leftovers.clear();
    }

    /**
     * What the ingredients left (a milk bucket's bucket) while a cook was at the station, taken once: the cook gets it instead of the
     * pot throwing it out beside itself.
     */
    public List<ItemStack> takeLeftovers() {
        List<ItemStack> taken = List.copyOf(leftovers);
        leftovers.clear();
        return taken;
    }

    public void save(CompoundTag tag) {
        if (byHand) {
            tag.putBoolean(BY_HAND, true);
        }
        if (bonusCarried > 0.0F) {
            tag.putFloat(BONUS, bonusCarried);
        }
    }

    public void load(CompoundTag tag) {
        byHand = tag.getBoolean(BY_HAND);
        bonusCarried = tag.getFloat(BONUS);
        // What a loaded pot holds was there before: only what comes in from now on says who feeds it.
        fed = -1;
    }

    /**
     * Before the pot's own cooking step: false when it must not cook this tick (a cook's food waits for the spoon). A stirred batch
     * cooks at the batch's pace.
     */
    public boolean beforeCooking(CookingPotRecipe recipe) {
        cooking = null;
        if (level() == null || serving || !pot.handson$isOurs()) {
            return true;
        }
        boolean stirred = isStirred(level().getGameTime());
        boolean farmersDelightsWay = !stirred
                && (!byHand || !attended() || !HandsOnConfig.stirringRequired() || pot.handson$isInMenu());
        if (farmersDelightsWay) {
            // Fed by automation, worked in the pot's own menu or left by the cook: its own way, a serving at a time.
            keepTimeAt(1.0F);
            batch = 0;
            return true;
        }
        int now = PotLogic.batch(pot, recipe);
        keepTimeAt(PotLogic.speed(now));
        batch = now;
        if (!stirred) {
            return false;
        }
        // The pot adds one itself; a big batch takes some back.
        saved += PotLogic.speed(now) - 1.0F;
        int whole = (int) saved;
        saved -= whole;
        pot.handson$setCookTime(pot.handson$cookTime() + whole);
        cooking = recipe;
        mealBefore = pot.handson$meal().getCount();
        return true;
    }

    /**
     * More food thrown in takes longer, less takes shorter, and a pot left to cook its own way runs at its own pace: the time already
     * cooked stays what it was. Without this a serving's worth of time cooked alone counted as the same share of a batch.
     */
    private void keepTimeAt(float now) {
        int total = pot.handson$cookTimeTotal();
        if (pace > 0.0F && now != pace && total > 0) {
            pot.handson$setCookTime(Math.min(total - 1, Math.round(pot.handson$cookTime() * now / pace)));
        }
        pace = now;
    }

    /**
     * The time the pot's step set for its recipe, {@code recipeTicks}, as long as the config makes the pot's cooking: every way this pot
     * cooks follows it, and the pot's own menu and the station read it as the total. Pots of other mods on the same class keep theirs.
     */
    public int timeFor(int recipeTicks) {
        return pot.handson$isOurs() ? HandsOnConfig.scaled(recipeTicks, HandsOnConfig.cookingPotTime()) : recipeTicks;
    }

    /** Right after the pot's own cooking step in a tick: when it served a stirred batch's first serving, the rest follows. */
    public void afterCooking() {
        CookingPotRecipe recipe = cooking;
        cooking = null;
        if (recipe == null || serving || level() == null || !pot.handson$isOurs()) {
            return;
        }
        ItemStack result = pot.handson$resultOf(recipe, level().registryAccess());
        ItemStack meal = pot.handson$meal();
        if (result.isEmpty() || !ItemStack.isSameItemSameTags(meal, result) || meal.getCount() <= mealBefore) {
            return;
        }
        serveTheBatch(recipe, result, meal);
    }

    /** The pot made one serving: the rest of the batch follows at once, and what the stirring earned with it. */
    private void serveTheBatch(CookingPotRecipe recipe, ItemStack result, ItemStack meal) {
        // The batch as the step before counted it, before the pot's serving: a slot that held the last item is empty now, and a count
        // made now would leave it out and cook the rest without it.
        // The pot lets the meal grow past its stack size: the serving it made is then the only one.
        int room = Math.max(1, (result.getMaxStackSize() - meal.getCount()) / result.getCount() + 1);
        int left = Math.min(room, batch);
        grade = skill.grade(level().getGameTime());
        // The most servings the room takes, out of as few ingredients as that needs: what is saved stays in the pot.
        int cooked = 1;
        int servings = Math.min(room, 1 + PotLogic.bonus(1, grade, bonusCarried));
        for (int size = 2; size <= left; size++) {
            int made = Math.min(room, size + PotLogic.bonus(size, grade, bonusCarried));
            if (made > servings) {
                cooked = size;
                servings = made;
            }
        }
        serving = true;
        try {
            for (int i = 1; i < cooked; i++) {
                pot.handson$setCookTime(pot.handson$cookTimeTotal() - 1);
                pot.handson$cookOnce(recipe);
            }
        } finally {
            serving = false;
        }
        if (servings > cooked) {
            pot.handson$meal().grow((servings - cooked) * result.getCount());
        }
        bonus = servings - cooked;
        // A full stack needed fewer sets than the batch had: the stars saved the rest, for the cook at the station. With the cook gone
        // they stay in the pot and cook next, and nobody opening it later is handed what then lies there.
        setsSaved = here() ? Math.max(0, left - cooked) : 0;
        float earned = bonusCarried + cooked * PotLogic.bonusRate(grade) - (servings - cooked);
        bonusCarried = Mth.clamp(earned, 0.0F, 0.999F);
        pot.handson$setCookTime(0);
        saved = 0.0F;
        batch = 0;
    }
}
