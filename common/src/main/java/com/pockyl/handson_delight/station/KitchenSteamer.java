package com.pockyl.handson_delight.station;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * One block of Youkai's Feasts' steamer as the station sees it: its pot (water, heat and two racks at most) or a block of racks
 * stacked on it (four at most), mixed into their common block entity by each loader that has that mod. A rack has four places of
 * one thing each, a feast block takes a whole rack. The pot sends the steam up the stack and the steamer cooks each thing by its own
 * code; this only reads it and does what its clicks do, through its own code, so that what it allows and uses up stays its own.
 * The tower as a whole: {@link SteamerTower}.
 */
public interface KitchenSteamer {
    String MOD_ID = KettleStation.YOUKAI_MOD_ID;
    /** The places of a rack. */
    int PLACES = 4;

    /** The steamer block of a block entity, or null for anything else. */
    static KitchenSteamer of(BlockEntity entity) {
        return entity instanceof KitchenSteamer steamer ? steamer : null;
    }

    BlockEntity handson$blockEntity();

    /** Whether this block is the pot, at the foot of the tower. */
    boolean handson$isPot();

    /** The racks of this block, from the bottom, as its block state has them. */
    int handson$racks();

    /** What lies on {@code place} of this block's rack {@code rack}; empty when nothing does. */
    ItemStack handson$item(int rack, int place);

    /** How far what lies there has steamed, 0 to 1; -1 when it has nothing more to steam into (done, or food that only sits). */
    float handson$steamed(int rack, int place);

    /** Whether what lies there steams now: it has a recipe the steamer is working through. */
    boolean handson$isSteaming(int rack, int place);

    /** The ticks what lies there takes to steam through, as its recipe says; 0 when it has none. */
    int handson$steamTime(int rack, int place);

    /** How much a thing on rack {@code rack} steams each tick now: the steam that passes it, up from the pot and down off a lid. */
    float handson$heat(int rack);

    /**
     * For a feast block, which fills a whole rack (a plate or bamboo meal): the room it leaves round itself in the rack, pixels, which
     * sets how small it is drawn; -1 for anything else.
     */
    float handson$clearance(ItemStack stack);

    /** Whether the steamer takes {@code stack} on a rack at all: food, or something it steams. */
    boolean handson$takes(ItemStack stack);

    /**
     * Lays one of {@code held} on {@code place} of rack {@code rack} as its click does, or on its first free place with -1; a feast
     * block only on an empty rack, filling it. False when it took none.
     */
    boolean handson$put(int rack, int place, ItemStack held, Player player);

    /** Takes what lies on {@code place} of rack {@code rack} into {@code player}'s inventory; what it was, or empty. */
    ItemStack handson$take(int rack, int place, Player player);

    /** Whether the top of this block is covered: its own thin lid, or the lid block over a full block. */
    boolean handson$isCapped();

    /**
     * One rack more on top of this block (the top of the tower), as a rack clicked on it puts one: on this block while it has room,
     * else a new block of racks over it, the lid moving up. False when none went on. The rack item is the caller's to use up.
     */
    boolean handson$addRack();

    /** The lid onto this block (the top of the tower); false when it has one or it does not fit. The lid item is the caller's. */
    boolean handson$cap();

    /** Whether there is water in the pot. */
    boolean handson$hasWater();

    /** Water into the pot, as a water bucket clicked on it; false when it has some. The bucket is the caller's to empty. */
    boolean handson$fill();

    /** Whether something under the pot heats it. */
    boolean handson$isHeated();
}
