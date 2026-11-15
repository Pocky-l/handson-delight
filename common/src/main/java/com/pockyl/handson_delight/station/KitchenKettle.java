package com.pockyl.handson_delight.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;

import java.util.List;

/**
 * Farmer's Respite's kettle, as the station sees it: mixed into its block entity by each loader that has that mod. Slots are counted
 * as the kettle's own inventory has them: two ingredients, the drink shown in its menu, the container, the output.
 */
public interface KitchenKettle {
    int INGREDIENTS = 2;
    int CONTAINER_SLOT = 3;
    int OUTPUT_SLOT = 4;

    ItemStack handson$getItem(int slot);

    void handson$setItem(int slot, ItemStack stack);

    /** What the kettle holds, or empty. */
    Fluid handson$fluid();

    int handson$amount();

    int handson$capacity();

    /** 0 = ticks brewed, 1 = ticks the recipe takes. */
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
     * A brewing recipe: one or two ingredients steeped in a base liquid ({@code baseAmount} millibuckets of it, a whole kettle) over
     * heat for a time; the base becomes the result liquid.
     */
    record Brewing(ResourceLocation id, List<Ingredient> ingredients, Fluid base, int baseAmount, Fluid result, int ticks) {
    }
}
