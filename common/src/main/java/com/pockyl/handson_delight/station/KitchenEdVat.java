package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import com.pockyl.handson_delight.platform.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extra Delight's vat, as the station sees it: its block entity, given these members by this mod's NeoForge mixin (Extra Delight is a
 * NeoForge mod). Slots as its menu has them: six places for what ages, the container the result goes into, the place of what a stage
 * of aging wants (salt, yeast), two for filling and emptying its tank, and the output. Its tank holds one liquid, up to a bucket.
 * What is in it ages in one or two stages, each with its own time, its own thing in the stage place, and with or without Extra
 * Delight's lid block on top: a stage that wants the lid waits without it, one that wants the vat open waits under it.
 */
public interface KitchenEdVat {
    String MOD_ID = "extradelight";
    int INGREDIENTS = 6;
    int CONTAINER_SLOT = 6;
    int STAGE_SLOT = 7;
    int OUTPUT_SLOT = 10;
    /** What its tank holds, millibuckets. */
    int CAPACITY = 1000;
    ResourceLocation VAT_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "vat");
    ResourceLocation LID_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "lid");

    ItemStack handson$item(int slot);

    /** Puts a stack in a slot of the vat's own inventory. */
    void handson$setItem(int slot, ItemStack stack);

    /** The liquid in its tank, {@link Fluids#EMPTY} for none. */
    Fluid handson$fluid();

    /** How much of it, millibuckets. */
    int handson$amount();

    /** Pours the liquid of one full container in, all of it or nothing: the container left, or null when nothing went in. */
    ItemStack handson$pour(ItemStack full);

    /** Draws the liquid into one empty container: the container filled, or null when it takes none of it. */
    ItemStack handson$draw(ItemStack empty);

    /** The stage aging is at, from 0; the number of stages of what is in it. */
    int handson$stage();

    int handson$stages();

    /** Ticks the stage under way has aged, and takes. */
    int handson$time();

    int handson$timeTotal();

    /** Starts aging over from the first stage. */
    void handson$restart();

    /** What was aged without its containers, waiting in the vat to be drawn into them; kept by this mod. */
    ItemStack handson$servings();

    void handson$setServings(ItemStack stack);

    /** The lid the station has lifted off and set aside while the cook works; empty for none. */
    ItemStack handson$lidAside();

    void handson$setLidAside(ItemStack lid);

    default BlockEntity handson$entity() {
        return (BlockEntity) this;
    }

    /** The vat of a block entity, or null for anything else. */
    static KitchenEdVat of(BlockEntity entity) {
        return entity instanceof KitchenEdVat vat ? vat : null;
    }

    static Block vat() {
        return BuiltInRegistries.BLOCK.get(VAT_ID);
    }

    static Block lid() {
        return BuiltInRegistries.BLOCK.get(LID_ID);
    }

    static boolean isLid(BlockState state) {
        return !state.isAir() && BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(LID_ID);
    }

    static boolean isLid(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(LID_ID);
    }

    /** Whether Extra Delight's lid stands on the vat at {@code pos}. */
    static boolean lidOn(BlockGetter level, BlockPos pos) {
        return isLid(level.getBlockState(pos.above()));
    }

    /** A stage of aging: what it wants in the stage place (nothing for an empty ingredient), how long, with the lid on or without. */
    record Stage(Ingredient ingredient, int ticks, boolean lid) {
        public boolean wantsItem() {
            return !ingredient.isEmpty();
        }

        /** Whether {@code stack} in the stage place lets this stage age: its thing, or nothing at all for a stage that wants none. */
        public boolean fits(ItemStack stack) {
            return wantsItem() ? !stack.isEmpty() && ingredient.test(stack) : stack.isEmpty();
        }
    }

    /**
     * An aging recipe: what goes in (one of each, in any of the six places), the liquid it stands in and how much of it, the containers
     * the result goes into (as many as it makes), what comes out, and its stages.
     */
    record Aging(ResourceLocation id, List<Ingredient> ingredients, List<Fluid> fluids, int amount, ItemStack container, ItemStack result,
            List<Stage> stages) {
        public boolean takes(Fluid fluid, int mb) {
            return mb >= amount && fluids.stream().anyMatch(wanted -> wanted.isSame(fluid));
        }

        public Fluid fluid() {
            return fluids.isEmpty() ? Fluids.EMPTY : fluids.get(0);
        }

        public int ticks() {
            return stages.stream().mapToInt(Stage::ticks).sum();
        }

        public Optional<Stage> stage(int index) {
            return index >= 0 && index < stages.size() ? Optional.of(stages.get(index)) : Optional.empty();
        }

        /** Whether {@code stack} is the thing some stage of it wants. */
        public boolean isStageItem(ItemStack stack) {
            return stages.stream().anyMatch(stage -> stage.wantsItem() && stage.ingredient().test(stack));
        }
    }

    /** A liquid in a container: which, and how much it pours. */
    record Liquid(Fluid fluid, int amount) {
    }

    /** What only Extra Delight's own code knows: its recipes, and the liquids of containers. Given by the NeoForge build. */
    interface Access {
        List<Aging> read(Level level);

        /** The liquid {@code stack} pours into the vat, if any: a bucket, a bottle Extra Delight knows, a water bottle. */
        Optional<Liquid> liquidOf(ItemStack stack);

        /** The full containers of {@code fluid} a cook may pour it from, the bottle first. */
        List<ItemStack> containersOf(Fluid fluid);
    }

    /** The vat's own code, or null where Extra Delight is not (and always on Fabric, which it has no build for). */
    static Access access() {
        return Platform.INSTANCE.edVats();
    }

    /** The vat's recipes as this level knows them (a client knows them too), in the order the vat tries them. */
    static List<Aging> recipes(Level level) {
        return Recipes.of(level);
    }

    /** The recipe read last, kept for the recipe manager it came from: read again for another (a reload, another world). */
    final class Recipes {
        private static java.lang.ref.WeakReference<Object> readFrom = new java.lang.ref.WeakReference<>(null);
        private static List<Aging> read = List.of();

        private Recipes() {
        }

        static synchronized List<Aging> of(Level level) {
            Access access = access();
            if (access == null) {
                return List.of();
            }
            Object manager = level.getRecipeManager();
            if (readFrom.get() != manager) {
                read = List.copyOf(access.read(level));
                readFrom = new java.lang.ref.WeakReference<>(manager);
            }
            return read;
        }
    }

    /** The recipe the six places make, the container place, liquid and stage left out: what the vat ages once they are right. */
    static Optional<Aging> matchingPlaces(List<Aging> recipes, ItemStack[] places) {
        return recipes.stream().filter(recipe -> KitchenVat.matchesLoose(recipe.ingredients(), places)).findFirst();
    }

    /** As {@link #matchingPlaces}, among the recipes the liquid in the vat also makes, when there are such. */
    static Optional<Aging> making(List<Aging> recipes, ItemStack[] places, Fluid fluid, int amount) {
        List<Aging> found = new ArrayList<>();
        for (Aging recipe : recipes) {
            if (KitchenVat.matchesLoose(recipe.ingredients(), places)) {
                found.add(recipe);
            }
        }
        return found.stream().filter(recipe -> recipe.takes(fluid, amount)).findFirst().or(() -> found.stream().findFirst());
    }

    /** The recipes that age into {@code result}: a serving waiting in the vat is drawn into the container of any of them. */
    static List<Aging> bottling(List<Aging> recipes, ItemStack result) {
        return result.isEmpty() ? List.of() : recipes.stream().filter(recipe -> !recipe.container().isEmpty()
                && ItemStack.isSameItem(recipe.result(), result)).toList();
    }

    /** Whether {@code container} takes a serving of {@code result} waiting in the vat. */
    static boolean draws(List<Aging> recipes, ItemStack result, ItemStack container) {
        return !container.isEmpty()
                && bottling(recipes, result).stream().anyMatch(recipe -> ItemStack.isSameItem(recipe.container(), container));
    }

    /** Whether {@code stack} is no recipe's ingredient but some recipe's container: it never goes into the vat. */
    static boolean isContainer(List<Aging> recipes, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean container = false;
        for (Aging recipe : recipes) {
            if (recipe.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack))) {
                return false;
            }
            container |= ItemStack.isSameItem(recipe.container(), stack);
        }
        return container;
    }

    /** Whether {@code stack} is no recipe's ingredient but some stage's thing: it goes into the stage place, one at a time. */
    static boolean isStageItem(List<Aging> recipes, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean stage = false;
        for (Aging recipe : recipes) {
            if (recipe.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack))) {
                return false;
            }
            stage |= recipe.isStageItem(stack);
        }
        return stage;
    }
}
