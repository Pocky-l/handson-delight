package com.pockyl.handson_delight.station;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * Brewin' and Chewin's keg, as the station sees it: mixed into its block entity by each loader that has that mod. Slots are
 * counted as the keg's own inventory has them: four ingredients, the container, the output. Youkai's Feasts' fermentation tank is
 * the same station in its own way (eight things, one a slot, its lid a state of its block, no warmth, what ferments into things
 * stays where it lay): the defaults here are the keg's.
 */
public interface KitchenKeg {
    int INGREDIENTS = 4;
    int CONTAINER_SLOT = 4;
    int OUTPUT_SLOT = 5;

    /** How many things go in, each in a slot of its own: the first slots of {@link #handson$getItem}. */
    default int handson$ingredients() {
        return INGREDIENTS;
    }

    /** The most of one thing a slot holds. */
    default int handson$perSlot() {
        return 64;
    }

    /** Whether what ferments into an item goes to an output slot of its own ({@link #OUTPUT_SLOT}); without one it stays where it lay. */
    default boolean handson$hasOutput() {
        return true;
    }

    /** Whether it ferments only at a temperature (the keg's 1 to 5): the badge shows its thermometer. */
    default boolean handson$feelsWarmth() {
        return true;
    }

    /**
     * Whether the lid is off, when the lid is the block's own (the tank's {@code open} state, which its redstone also turns, and which
     * it opens itself when something is done); null when the station keeps it, as the keg's.
     */
    default Boolean handson$lidOff() {
        return null;
    }

    /** Takes the block's own lid off or puts it back; nothing for a lid the station keeps. */
    default void handson$setLidOff(boolean off) {
    }

    /** Whether {@code items}, what lies in it, make {@code recipe}: one of each ingredient, as the keg reads its recipes. */
    default boolean handson$makes(Fermenting recipe, List<ItemStack> items) {
        return StationRecipes.matches(recipe.ingredients(), items);
    }

    ItemStack handson$getItem(int slot);

    void handson$setItem(int slot, ItemStack stack);

    /** What the tank holds, or empty. */
    Fluid handson$fluid();

    int handson$amount();

    int handson$capacity();

    /** 0 = ticks fermented, 1 = ticks the recipe takes, 2 = the keg's temperature, 1 (cold) to 5 (hot). */
    ContainerData handson$data();

    /** Looks round the keg for heat and cold again, as the keg does every four seconds. */
    void handson$feelTemperature();

    /** The temperature, 1 to 5, the recipe the keg holds wants whatever the keg's own is; 0 for none. */
    int handson$wantedTemperature();

    /**
     * Uses {@code held} on the keg as a click in the world does: a bucket or a drink pours in, an empty container is filled from the
     * tap, up to {@code most} at once. {@code held} shrinks by what was used; the stacks returned are what the player gets.
     */
    List<ItemStack> handson$use(ItemStack held, int most, boolean creative);

    /** Whether {@code stack} can be filled from the tap now: a container the keg's drink is poured into, with enough left. */
    boolean handson$canTap(ItemStack stack);

    /**
     * Whether {@code stack} takes more than a serving when it is filled: a bucket, Youkai's Feasts' flask. It is filled when the cook
     * brings it, never offered to an empty hand.
     */
    default boolean handson$holdsMany(ItemStack stack) {
        return stack.is(net.minecraft.world.item.Items.BUCKET);
    }

    /** Whether {@code stack} holds something that pours into the keg now: a bucket, a bottle, a drink of the same. */
    boolean handson$canPour(ItemStack stack);

    /** Whether {@code stack} is a vessel of the keg (a bucket, a bottle, a tankard, a drink): it is poured or filled, never dropped in. */
    boolean handson$isVessel(ItemStack stack);

    /** The colour {@code fluid} is tinted with where it is drawn (its own texture's tint), 0 when it is not known here. */
    default int handson$tint(Fluid fluid) {
        return 0;
    }

    /** A full container of {@code fluid} as the keg pours it (a tankard of beer), or of its bucket; empty for none. */
    ItemStack handson$drinkOf(Fluid fluid);

    /** The keg's fermenting recipes, as the book lists them. */
    List<Fermenting> handson$recipes();

    /** What the result slot of the keg's menu gives a player who takes from it: experience, recipes unlocked. */
    void handson$awardRecipes(Player player);

    BlockEntity handson$blockEntity();

    /** The keg of a block entity, or null for anything else. */
    static KitchenKeg of(BlockEntity entity) {
        return entity instanceof KitchenKeg keg ? keg : null;
    }

    /**
     * {@code fermenting} (0 = ticks fermented, 1 = ticks the recipe takes) with the keg's {@code temperature} as 2, for a keg whose own
     * data does not carry it (Brewin' and Chewin' 4 asks the keg itself).
     */
    static ContainerData withTemperature(ContainerData fermenting, IntSupplier temperature) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return index == 2 ? temperature.getAsInt() : fermenting.get(index);
            }

            @Override
            public void set(int index, int value) {
                if (index < 2) {
                    fermenting.set(index, value);
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    /**
     * A fermenting recipe: up to four ingredients and a base liquid ({@code base} empty when it needs none) at a temperature, for
     * a time; it makes a liquid ({@code resultFluid}) or an item ({@code resultItem}).
     */
    record Fermenting(Identifier id, List<Ingredient> ingredients, Fluid base, int baseAmount, Fluid resultFluid, ItemStack resultItem,
            int ticks, int temperature) {
    }
}
