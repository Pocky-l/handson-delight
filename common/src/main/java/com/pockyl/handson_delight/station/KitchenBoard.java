package com.pockyl.handson_delight.station;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A cutting board that works as Farmer's Delight's: one slot for what lies on it, a tool stuck into it, Farmer's Delight's cutting
 * recipes. Farmer's Delight's own board and the boards other mods copy from it (Chopper's Delight's boards of every wood) are mixed
 * into, so that the station works the same at all of them.
 */
public interface KitchenBoard extends BoardLayers {
    /** What lies on the board, to be cut or a finished dish; a tool stuck into it. */
    ItemStack handson$stored();

    void handson$store(ItemStack stack);

    /** Whether what lies there is a tool stuck into the board, not food. */
    boolean handson$isCarving();

    void handson$setCarving(boolean carving);

    /** How much of one kind the board holds. */
    int handson$maxStack();

    /** Sticks {@code tool} into the bare board, as the board's own use does; false when it does not take it. */
    boolean handson$carve(ItemStack tool);

    /** The board's own cut of what lies there with {@code tool}, its results dropped as the board drops them; false when none. */
    boolean handson$cut(ItemStack tool, Player player);

    BlockEntity handson$blockEntity();

    /** The board of a block entity, or null for anything else. */
    static KitchenBoard of(BlockEntity entity) {
        return entity instanceof KitchenBoard board ? board : null;
    }
}
