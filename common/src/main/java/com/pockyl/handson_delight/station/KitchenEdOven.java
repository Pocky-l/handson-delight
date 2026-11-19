package com.pockyl.handson_delight.station;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Extra Delight's oven block entity as this mod reaches into it: mixed in by the NeoForge build (Extra Delight is a NeoForge mod). Its
 * item handler of twelve: nine places for what bakes (one of each a bake, stacks are sets), a slot of its own for showing a meal, the
 * bakeware slot and the output. {@link EdOven} is the oven station's view of it.
 */
public interface KitchenEdOven {
    ItemStack handson$item(int slot);

    /** Puts a stack in a slot of its own inventory, as its menu would. */
    void handson$setItem(int slot, ItemStack stack);

    /** Ticks the set has baked, and the ticks its recipe takes (0 before its first bake). */
    int handson$cookTime();

    void handson$setCookTime(int ticks);

    int handson$cookTimeTotal();

    /** Whether the heat under it is on, as it asks Farmer's Delight. */
    boolean handson$isHeated();

    /** Its own reward for what the cook takes out, as its menu's output slot gives it: experience and the recipes unlocked. */
    void handson$award(Player player);

    /** Sends what lies in it, and how far it has baked, to the clients near it. */
    void handson$sync();

    default BlockEntity handson$entity() {
        return (BlockEntity) this;
    }
}
