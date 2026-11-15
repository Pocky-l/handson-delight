package com.pockyl.handson_delight.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;

import java.util.List;

/**
 * A kettle, as the station sees it: mixed into the block entity of Farmer's Respite's kettle or Youkai's Feasts' by each loader that
 * has that mod. Slots are counted as the kettle's own inventory has them, the ingredients first (Farmer's Respite: two ingredients,
 * the drink shown in its menu, the container, the output; Youkai's Feasts: four ingredients, one thing each).
 */
public interface KitchenKettle {
    /** How many things Farmer's Respite's kettle steeps at once. */
    int INGREDIENTS = 2;
    /** The most things any kettle steeps at once: the scene has a place for each. */
    int MOST_INGREDIENTS = 4;
    int CONTAINER_SLOT = 3;
    int OUTPUT_SLOT = 4;

    /** How many things the kettle steeps at once: its ingredient slots. */
    default int handson$ingredients() {
        return INGREDIENTS;
    }

    /** How many of one thing an ingredient slot holds: Youkai's Feasts' kettle takes one leaf a slot. */
    default int handson$perSlot() {
        return 64;
    }

    /**
     * Whether the lid is the kettle block's own state ({@code lid}, Farmer's Respite's, drawn in the world too). Youkai's Feasts' kettle
     * has no such state: its lid is the station's alone, on whenever nobody works at the kettle.
     */
    default boolean handson$lidInBlock() {
        return true;
    }

    /** Whether the lid is off. */
    default boolean handson$isOpen() {
        return KettleStation.isOpen(handson$blockEntity().getBlockState());
    }

    /** Takes the lid off ({@code open}) or puts it back. */
    default void handson$setOpen(boolean open) {
        BlockEntity entity = handson$blockEntity();
        BlockState state = entity.getBlockState();
        BooleanProperty lid = KettleStation.lidProperty(state);
        if (entity.getLevel() != null && lid != null && state.getValue(lid) == open) {
            entity.getLevel().setBlockAndUpdate(entity.getBlockPos(), state.setValue(lid, !open));
        }
    }

    /**
     * Whether the kettle brews only in boiling water, which it heats first a tick a millibucket (Youkai's Feasts'); its recipes' times
     * are its own then, not scaled by the config.
     */
    default boolean handson$boilsFirst() {
        return false;
    }

    /**
     * How far the kettle's water has come to the boil, 0 to 1, for a kettle that brews only in boiling water (Youkai's Feasts': it heats
     * the water first, a tick a millibucket); -1 for one that brews as soon as it stands on heat.
     */
    default float handson$boil() {
        return -1.0F;
    }

    /** Ticks on the heat until the water boils; 0 when it boils, or for a kettle without that step. */
    default int handson$ticksToBoil() {
        return 0;
    }

    /** Whether the kettle's mod draws it turned a quarter to the right on a tray (Youkai's Feasts does): the scene turns it so too. */
    default boolean handson$turnsOnTray() {
        return false;
    }

    ItemStack handson$getItem(int slot);

    void handson$setItem(int slot, ItemStack stack);

    /** What the kettle holds, or empty. */
    Fluid handson$fluid();

    int handson$amount();

    int handson$capacity();

    /** 0 = ticks brewed, 1 = ticks the recipe takes; for a kettle that boils its water first, 2 = how hot it is (1 a millibucket). */
    ContainerData handson$data();

    /** Whether a fire, a stove or another heat source warms the kettle, as Farmer's Delight tells. */
    boolean handson$isHeated();

    /**
     * Uses one of {@code held} on the kettle as a click in the world does, through the kettle's own code: a bucket or a drink pours in,
     * an empty cup or bottle is filled from the spout. {@code held} shrinks by the one used; what comes back is what the player gets,
     * or empty when nothing happened.
     */
    ItemStack handson$useOne(ItemStack held);

    /** Whether {@code stack} can be filled at the spout now: a container the kettle's drink is poured into, with enough left. */
    boolean handson$canServe(ItemStack stack);

    /** Whether {@code stack} holds something that pours into the kettle now: a bucket, or a drink of what the kettle holds. */
    boolean handson$canPour(ItemStack stack);

    /** Whether {@code stack} is a vessel of the kettle (a bucket, a cup, a bottle, a drink): it is poured or filled, never dropped in. */
    boolean handson$isVessel(ItemStack stack);

    /** A full container of {@code fluid} as the kettle pours it (a bottle of green tea), or its bucket; empty for none. */
    ItemStack handson$drinkOf(Fluid fluid);

    /** The colour the kettle's own mod gives {@code fluid}, ARGB; 0 when it gives none. Only asked on clients. */
    int handson$tint(Fluid fluid);

    /** The kettle's brewing recipes, as the book lists them. */
    List<Brewing> handson$recipes();

    /** What taking a drink from the kettle's menu gives a player: experience, recipes unlocked. */
    void handson$awardRecipes(Player player);

    BlockEntity handson$blockEntity();

    /** The kettle of a block entity, or null for anything else. */
    static KitchenKettle of(BlockEntity entity) {
        return entity instanceof KitchenKettle kettle ? kettle : null;
    }

    /**
     * A brewing recipe: one to four ingredients steeped in a base liquid ({@code baseAmount} millibuckets of it, a whole kettle) over
     * heat for a time (once it boils, for a kettle that boils its water first); the base becomes the result liquid.
     */
    record Brewing(ResourceLocation id, List<Ingredient> ingredients, Fluid base, int baseAmount, Fluid result, int ticks) {
    }
}
