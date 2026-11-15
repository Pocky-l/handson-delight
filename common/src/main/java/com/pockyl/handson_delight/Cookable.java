package com.pockyl.handson_delight;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * Whether a recipe can be made at all. An addon may name a tag nothing fills (Christmas Delight's {@code c:foods/milk}): that
 * ingredient takes no item, the recipe never cooks, and the vanilla recipe book hides it. The stations' books and the pot hide and
 * skip it too, so no dish is offered that no cook can make.
 */
public final class Cookable {
    private Cookable() {
    }

    /** Whether some item is {@code ingredient}; a gap (an empty ingredient of a shaped recipe) asks for none. */
    public static boolean ingredient(Ingredient ingredient) {
        if (ingredient.isEmpty()) {
            return true;
        }
        for (ItemStack stack : ingredient.getItems()) {
            // Forge and NeoForge stand a named barrier in for an empty tag.
            if (!stack.isEmpty() && !stack.is(Items.BARRIER)) {
                return true;
            }
        }
        return false;
    }

    /** Whether some item is each of {@code ingredients}. */
    public static boolean all(List<Ingredient> ingredients) {
        for (Ingredient ingredient : ingredients) {
            if (!ingredient(ingredient)) {
                return false;
            }
        }
        return true;
    }
}
