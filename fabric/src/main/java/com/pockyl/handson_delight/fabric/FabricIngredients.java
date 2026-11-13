package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients;
import net.fabricmc.fabric.impl.recipe.ingredient.builtin.AllIngredient;
import net.fabricmc.fabric.impl.recipe.ingredient.builtin.AnyIngredient;
import net.fabricmc.fabric.impl.recipe.ingredient.builtin.DifferenceIngredient;
import net.fabricmc.fabric.impl.recipe.ingredient.builtin.NbtIngredient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.crafting.Ingredient;

import com.pockyl.handson_delight.HandsOnDelight;

import java.lang.reflect.Field;

/**
 * Ingredients that test and list their items, with Refabricated's tool action taken out of the wrapper that breaks it.
 *
 * <p>Refabricated 2.5.x's {@code ToolActionIngredient} ({@code "fabric:type": "farmersdelight:tool_action"}: the axe, shears
 * and pickaxe cuts of addons) is a vanilla ingredient and a Fabric custom one at once. Its custom {@code test} lost its name
 * when the jar was remapped, so the wrapper Fabric puts round it throws {@code AbstractMethodError} on every test and shows
 * no items. As the vanilla ingredient it also is, it tests and lists the tools that do its action. Addons also put it inside
 * Fabric's own ingredients ({@code fabric:any} of an axe action and a tag, say), whose parts are such wrappers: those are
 * built anew around the parts taken out.
 */
final class FabricIngredients {
    /** The parts of Fabric's built-in ingredients; null when this Fabric API names them otherwise, and nothing is taken apart. */
    private static final Field PARTS = field(AnyIngredient.class.getSuperclass(), "ingredients");
    private static final Field BASE = field(DifferenceIngredient.class, "base");
    private static final Field SUBTRACTED = field(DifferenceIngredient.class, "subtracted");
    private static final Field NBT_BASE = field(NbtIngredient.class, "base");
    private static final Field NBT = field(NbtIngredient.class, "nbt");
    private static final Field STRICT = field(NbtIngredient.class, "strict");

    private FabricIngredients() {
    }

    /** {@code ingredient} itself when nothing in it is a wrapped tool action, else one that tests and lists the same items. */
    static Ingredient unwrap(Ingredient ingredient) {
        // Ingredient is final in the game; Refabricated's access widener opens it, so the checks go through Object.
        Object custom = ingredient.getCustomIngredient();
        if (custom == null) {
            return ingredient;
        }
        if (custom instanceof Ingredient own) {
            return own;
        }
        try {
            if (custom instanceof AnyIngredient || custom instanceof AllIngredient) {
                if (PARTS == null) {
                    return ingredient;
                }
                Ingredient[] parts = ((Ingredient[]) PARTS.get(custom)).clone();
                boolean changed = false;
                for (int i = 0; i < parts.length; i++) {
                    Ingredient part = unwrap(parts[i]);
                    changed |= part != parts[i];
                    parts[i] = part;
                }
                if (!changed) {
                    return ingredient;
                }
                return custom instanceof AnyIngredient ? DefaultCustomIngredients.any(parts) : DefaultCustomIngredients.all(parts);
            }
            if (custom instanceof DifferenceIngredient && BASE != null && SUBTRACTED != null) {
                Ingredient base = (Ingredient) BASE.get(custom);
                Ingredient subtracted = (Ingredient) SUBTRACTED.get(custom);
                Ingredient newBase = unwrap(base);
                Ingredient newSubtracted = unwrap(subtracted);
                return newBase == base && newSubtracted == subtracted ? ingredient
                        : DefaultCustomIngredients.difference(newBase, newSubtracted);
            }
            if (custom instanceof NbtIngredient && NBT_BASE != null && NBT != null && STRICT != null) {
                Ingredient base = (Ingredient) NBT_BASE.get(custom);
                Ingredient newBase = unwrap(base);
                return newBase == base ? ingredient
                        : DefaultCustomIngredients.nbt(newBase, (CompoundTag) NBT.get(custom), STRICT.getBoolean(custom));
            }
        } catch (IllegalAccessException | ClassCastException e) {
            HandsOnDelight.LOGGER.debug("Cannot take apart the ingredient {}", custom, e);
        }
        return ingredient;
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            HandsOnDelight.LOGGER.warn("Fabric's {} has no field {}: tool actions inside it are not taken out", owner.getName(), name);
            return null;
        }
    }
}
