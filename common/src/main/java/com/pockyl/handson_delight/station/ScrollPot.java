package com.pockyl.handson_delight.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import java.util.List;
import java.util.Optional;

/**
 * A pot that holds a spell scroll besides its food: Farmer's Spell's alchemist pot, with an Iron's Spells scroll. The scroll is never
 * used up; its school (that of its first spell) changes the pot: fire heats it with no fire under it, gluttony cooks faster, and some
 * of its recipes cook only under a scroll of their school. Mixed into the pot's block entity, so that the shared code needs neither mod.
 */
public interface ScrollPot {
    @SuppressWarnings("removal")
    ResourceLocation FIRE = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fire");
    @SuppressWarnings("removal")
    ResourceLocation ICE = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ice");
    @SuppressWarnings("removal")
    ResourceLocation LIGHTNING = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "lightning");
    @SuppressWarnings("removal")
    ResourceLocation HOLY = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "holy");
    @SuppressWarnings("removal")
    ResourceLocation GLUTTONY = ResourceLocation.fromNamespaceAndPath("farmers_spell", "gluttony");

    ItemStack handson$scroll();

    void handson$setScroll(ItemStack scroll);

    /** Whether {@code stack} is a scroll the pot takes. */
    boolean handson$isScroll(ItemStack stack);

    /** The school of a scroll's spell, or null for none. */
    ResourceLocation handson$schoolOf(ItemStack scroll);

    /** The school a recipe cooks only under, or null for none (Farmer's Delight's recipes and most of the pot's own). */
    ResourceLocation handson$schoolFor(CookingPotRecipe recipe);

    /** A scroll of every spell of {@code school}, to show where a recipe wants one; empty where there are none. */
    List<ItemStack> handson$scrollsOf(ResourceLocation school);

    /** Any scroll of {@code school}, for a recipe book: it shows one of every spell of that school. */
    Ingredient handson$scrollIngredient(ResourceLocation school);

    /** The colour of {@code school}, as Iron's Spells aims its spells, 0xRRGGBB. */
    int handson$colorOf(ResourceLocation school);

    /** Whether the recipe is the pot's own (a tab of the book), not Farmer's Delight's. */
    boolean handson$isOwnRecipe(CookingPotRecipe recipe);

    /** The school of the scroll hanging in the pot, or null. */
    default ResourceLocation handson$school() {
        ItemStack scroll = handson$scroll();
        return scroll.isEmpty() ? null : handson$schoolOf(scroll);
    }

    /**
     * The pot's own recipe that {@code items} make but that waits for a scroll of its school, which the pot does not hold; empty when
     * they make nothing or make it already. The pot looks only at its first match, as it does when it cooks.
     */
    Optional<RecipeHolder<CookingPotRecipe>> handson$waiting(Level level, List<ItemStack> items);

    /** The slot of the player's inventory holding a scroll of {@code school}: the one in hand first; -1 without one. */
    default int scrollSlot(Inventory inventory, ResourceLocation school) {
        if (school == null) {
            return -1;
        }
        if (handson$isScroll(inventory.getSelected()) && school.equals(handson$schoolOf(inventory.getSelected()))) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (handson$isScroll(stack) && school.equals(handson$schoolOf(stack))) {
                return slot;
            }
        }
        return -1;
    }

    /** The scroll pot of a block entity, or null for anything else. */
    static ScrollPot of(BlockEntity entity) {
        return entity instanceof ScrollPot pot ? pot : null;
    }
}
