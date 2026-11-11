package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import vectorwing.farmersdelight.common.block.AbstractStoveBlock;
import vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.handson_delight.KitchenTags;
import com.pockyl.handson_delight.platform.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The stove's rules for food the cook lays on it: each piece grills in two halves, the side on the grill first; once that side is done
 * the piece smokes and waits until it is turned, then the other side grills. Done on both, it becomes what the fire makes of it and
 * waits on the grill for the cook. Food put on Farmer's Delight's way (its right click, a player without this mod) cooks its way and
 * pops off when done.
 */
public final class StoveCooking {
    /** As much faster than Farmer's Delight's stove as the pot and the skillet are than theirs. */
    private static final float PACE = 1.5F;
    /** Ticks between two turns of one piece the server takes: it is still in the air before that. */
    private static final int TURN_GAP = 6;

    private StoveCooking() {
    }

    public static Grill grill(AbstractStoveBlockEntity stove) {
        return ((StoveTop) stove).handson$grill();
    }

    /** Ticks one side of a piece takes, for a recipe Farmer's Delight's stove cooks in {@code ticks}. */
    public static int sideTicks(int ticks) {
        return Math.max(1, Math.round(ticks / PACE / 2.0F));
    }

    public static boolean isLit(AbstractStoveBlockEntity stove) {
        BlockState state = stove.getBlockState();
        return state.hasProperty(AbstractStoveBlock.LIT) && state.getValue(AbstractStoveBlock.LIT);
    }

    /** Whether something stands on the stove (a pot, a skillet): then its top is no grill, and Farmer's Delight drops what lay there. */
    public static boolean isCovered(AbstractStoveBlockEntity stove) {
        Level level = stove.getLevel();
        return level != null && AbstractStoveBlock.isStoveTopCovered(level, stove.getBlockPos(), stove.getBlockState());
    }

    /**
     * Every tick of the stove on the server, before Farmer's Delight's own: an emptied place starts over, and on a lit stove the pieces
     * laid by hand grill, Farmer's Delight's count for them held at nothing.
     */
    public static void tick(AbstractStoveBlockEntity stove) {
        Level level = stove.getLevel();
        if (level == null) {
            return;
        }
        finishLeft(stove);
        Grill grill = grill(stove);
        int[] progress = ((StoveTop) stove).handson$progress();
        int[] times = ((StoveTop) stove).handson$times();
        boolean lit = isLit(stove);
        boolean changed = false;
        for (int place = 0; place < Grill.PLACES; place++) {
            ItemStack stack = Platform.INSTANCE.getItem(stove, place);
            if (stack.isEmpty()) {
                if (grill.byHand(place) || grill.down(place) > 0.0F || grill.up(place) > 0.0F) {
                    grill.farmersDelightsWay(place);
                    changed = true;
                }
                continue;
            }
            if (!grill.byHand(place)) {
                continue;
            }
            // Farmer's Delight would pop it off once its count reaches its time: neither ever comes.
            progress[place] = 0;
            times[place] = Integer.MAX_VALUE;
            Optional<CampfireCookingRecipe> recipe = SkilletCooking.recipe(level, stack);
            if (!lit || recipe.isEmpty() || grill.waits(place)) {
                continue;
            }
            if (SkilletCooking.isOneSided(stack)) {
                grill.grillThrough(place, 0.5F / sideTicks(recipe.get().getCookingTime()));
            } else {
                grill.grill(place, 1.0F / sideTicks(recipe.get().getCookingTime()));
            }
            if (grill.down(place) >= 1.0F && grill.up(place) >= 1.0F) {
                // Done on both sides: Farmer's Delight's own code cooks it this very tick (see beforeFarmersDelightCooks).
                times[place] = 1;
                ((StoveTop) stove).handson$cookingNow()[place] = true;
            }
        }
        send(stove, changed);
    }

    /** The drops of Farmer's Delight's cooking this tick are caught when the station has a piece in it; the slots as they were. */
    private static final ThreadLocal<ItemStack[]> BEFORE = new ThreadLocal<>();

    /**
     * Farmer's Delight is about to cook what is due on the grill. A piece the station finished is cooked by its code, so that what
     * other mods add to its stove still happens (quality, experience); what it throws off the grill is caught.
     */
    public static void beforeFarmersDelightCooks(AbstractStoveBlockEntity stove) {
        boolean[] now = ((StoveTop) stove).handson$cookingNow();
        boolean any = false;
        for (boolean place : now) {
            any |= place;
        }
        if (!any) {
            return;
        }
        ItemStack[] before = new ItemStack[Grill.PLACES];
        for (int place = 0; place < Grill.PLACES; place++) {
            before[place] = Platform.INSTANCE.getItem(stove, place).copy();
        }
        BEFORE.set(before);
        CaughtDrops.begin();
    }

    /**
     * Farmer's Delight cooked: each place it emptied threw one item, in the order of the places. A piece the station finished lies
     * back on the grill as what was thrown, to wait for the cook; anything else (food cooked Farmer's Delight's way, an addon's extra
     * drop) is thrown into the world after all, as it would have been.
     */
    public static void afterFarmersDelightCooks(AbstractStoveBlockEntity stove) {
        ItemStack[] before = BEFORE.get();
        if (before == null) {
            return;
        }
        BEFORE.remove();
        List<CaughtDrops.Drop> drops = new ArrayList<>(CaughtDrops.end());
        Level level = stove.getLevel();
        boolean[] now = ((StoveTop) stove).handson$cookingNow();
        for (int place = 0; place < Grill.PLACES; place++) {
            if (!now[place] || before[place].isEmpty() || !Platform.INSTANCE.getItem(stove, place).isEmpty()) {
                continue;
            }
            now[place] = false;
            CaughtDrops.Drop drop = dropFor(level, before[place], drops);
            if (drop == null) {
                // Another mod sent the cooked piece somewhere else: the place is free again.
                grill(stove).farmersDelightsWay(place);
                continue;
            }
            grilled(stove, place, drop.stack());
        }
        if (level != null) {
            drops.forEach(drop -> drop.spawn(level));
        }
        // A piece Farmer's Delight did not cook (its recipe for it differs): cooked here, as the campfire makes it.
        finishLeft(stove);
        send(stove, true);
    }

    /** The drop that came of {@code raw}: the first of what its recipe makes, else the first drop left. Taken from {@code drops}. */
    private static CaughtDrops.Drop dropFor(Level level, ItemStack raw, List<CaughtDrops.Drop> drops) {
        if (drops.isEmpty()) {
            return null;
        }
        ItemStack expected = level == null ? ItemStack.EMPTY : SkilletCooking.recipe(level, raw)
                .map(recipe -> recipe.assemble(new SingleRecipeInput(raw.copyWithCount(1)), level.registryAccess())).orElse(ItemStack.EMPTY);
        for (int i = 0; i < drops.size(); i++) {
            if (drops.get(i).stack().is(expected.getItem())) {
                return drops.remove(i);
            }
        }
        return drops.remove(0);
    }

    /** Places still marked to be cooked by Farmer's Delight (it did not run, or did not take them): finished here. */
    private static void finishLeft(AbstractStoveBlockEntity stove) {
        Level level = stove.getLevel();
        boolean[] now = ((StoveTop) stove).handson$cookingNow();
        for (int place = 0; place < Grill.PLACES; place++) {
            if (!now[place]) {
                continue;
            }
            now[place] = false;
            ItemStack stack = Platform.INSTANCE.getItem(stove, place);
            Optional<CampfireCookingRecipe> recipe = level == null || stack.isEmpty() ? Optional.empty() : SkilletCooking.recipe(level, stack);
            if (recipe.isPresent()) {
                grilled(stove, place, recipe.get().assemble(new SingleRecipeInput(stack.copyWithCount(1)), level.registryAccess()));
            } else {
                grill(stove).done(place);
            }
        }
    }

    /** The piece at {@code place} is done on both sides and lies there as {@code done}, waiting for the cook. */
    private static void grilled(AbstractStoveBlockEntity stove, int place, ItemStack done) {
        Platform.INSTANCE.setItem(stove, place, done);
        grill(stove).done(place);
        ((StoveTop) stove).handson$progress()[place] = 0;
        ((StoveTop) stove).handson$times()[place] = Integer.MAX_VALUE;
        CompoundTag data = new CompoundTag();
        data.putInt(StoveStation.PLACE, place);
        data.put(StoveStation.STACK, done.saveOptional(stove.getLevel().registryAccess()));
        effect(stove, StoveStation.EFFECT_GRILLED, data);
    }

    /**
     * The stack on the cursor goes onto the grill: with {@code place} -1 one onto every free place, as many as there are; else one onto
     * that place. Returns how many went on. What would run through the bars (an egg cracked open) goes into a skillet, not here.
     */
    public static int place(AbstractStoveBlockEntity stove, ItemStack carried, int place) {
        Level level = stove.getLevel();
        if (carried.isEmpty() || level == null || isCovered(stove) || !canGrill(level, carried)) {
            return 0;
        }
        Grill grill = grill(stove);
        int laid = 0;
        for (int at = 0; at < Grill.PLACES && !carried.isEmpty(); at++) {
            if (place >= 0 && at != place || !Platform.INSTANCE.getItem(stove, at).isEmpty()) {
                continue;
            }
            Platform.INSTANCE.setItem(stove, at, carried.split(1));
            grill.farmersDelightsWay(at);
            grill.layByHand(at);
            ((StoveTop) stove).handson$progress()[at] = 0;
            ((StoveTop) stove).handson$times()[at] = Integer.MAX_VALUE;
            laid++;
        }
        if (laid > 0) {
            send(stove, true);
        }
        return laid;
    }

    /** The cook turns the piece on {@code place}. False with nothing there to turn, or while it is still in the air. */
    public static boolean turn(AbstractStoveBlockEntity stove, int place) {
        Level level = stove.getLevel();
        Grill grill = grill(stove);
        ItemStack stack = place >= 0 && place < Grill.PLACES ? Platform.INSTANCE.getItem(stove, place) : ItemStack.EMPTY;
        if (level == null || stack.isEmpty() || SkilletCooking.recipe(level, stack).isEmpty()
                || level.getGameTime() - grill.turnedAt(place) < TURN_GAP) {
            return false;
        }
        if (!grill.byHand(place)) {
            // Cooking Farmer's Delight's way: from now on the cook's, from raw as the skillet does.
            grill.farmersDelightsWay(place);
            grill.layByHand(place);
        }
        grill.turn(place, level.getGameTime());
        send(stove, true);
        return true;
    }

    /** The piece on {@code place} back to the cook, raw, half done or done. */
    public static ItemStack take(AbstractStoveBlockEntity stove, int place) {
        if (place < 0 || place >= Grill.PLACES) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = Platform.INSTANCE.getItem(stove, place).copy();
        if (!taken.isEmpty()) {
            Platform.INSTANCE.setItem(stove, place, ItemStack.EMPTY);
            grill(stove).farmersDelightsWay(place);
            send(stove, true);
        }
        return taken;
    }

    /** Whether the grill takes {@code stack}: campfire food that stays on the bars. */
    public static boolean canGrill(Level level, ItemStack stack) {
        return !stack.is(KitchenTags.NOT_ON_GRILL) && SkilletCooking.recipe(level, stack).isPresent();
    }

    /** Whether {@code stack} on the grill is done: nothing the fire makes of it any more. */
    public static boolean isDone(Level level, ItemStack stack) {
        return SkilletCooking.isDone(level, stack);
    }

    private static void effect(AbstractStoveBlockEntity stove, ResourceLocation effect, CompoundTag data) {
        if (stove.getLevel() instanceof ServerLevel server) {
            Diegesis.activeStation(server, stove.getBlockPos()).ifPresent(station -> station.effect(effect, "grill", data));
        }
    }

    /** Saves the stove and tells the clients around: always, or only when a piece got a step further. */
    private static void send(AbstractStoveBlockEntity stove, boolean always) {
        boolean due = grill(stove).dueToSend();
        if (!always && !due) {
            return;
        }
        stove.setChanged();
        Level level = stove.getLevel();
        if (level != null) {
            BlockPos pos = stove.getBlockPos();
            level.sendBlockUpdated(pos, stove.getBlockState(), stove.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
