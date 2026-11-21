package com.pockyl.handson_delight.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import com.pockyl.handson_delight.platform.Platform;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extra Delight's chiller, an ice box, as the station sees it: its block entity, given these members by this mod's NeoForge mixin
 * (Extra Delight is a NeoForge mod). Slots as its menu has them: four places for what sets (one of each a set), the container it sets
 * in (a mold, a tin, a bowl, sticks), the output, and the ice. Its tank holds one liquid, up to a bucket. It sets only while it is
 * cold: a piece of ice melts for as long as its kind lasts, and every tick of cold drips a millibucket of melt water into the drip
 * pan; a full pan stops the cold until it is emptied.
 */
public interface KitchenChiller {
    String MOD_ID = "extradelight";
    int PLACES = 4;
    int CONTAINER_SLOT = 5;
    int OUTPUT_SLOT = 6;
    int ICE_SLOT = 10;
    /** What its tank holds, and its drip pan, millibuckets. */
    int TANK = 1000;
    int PAN = 1000;
    int BOTTLE = 250;
    int BUCKET = 1000;
    ResourceLocation CHILLER_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "chiller");

    ItemStack handson$item(int slot);

    /** Puts a stack in a slot of the chiller's own inventory. */
    void handson$setItem(int slot, ItemStack stack);

    /** The liquid in its tank, {@link Fluids#EMPTY} for none, and how much of it, millibuckets. */
    Fluid handson$fluid();

    int handson$amount();

    /** Pours the liquid of one full container in, all of it or nothing: the container left, or null when nothing went in. */
    ItemStack handson$pour(ItemStack full);

    /** Draws the liquid into one empty container: the container filled, or null when it takes none of it. */
    ItemStack handson$draw(ItemStack empty);

    /** Ticks what is in it has set, and takes. */
    int handson$time();

    int handson$timeTotal();

    /** Ticks of cold left of the piece of ice melting now, and the ticks that piece lasts. */
    int handson$chill();

    int handson$chillTotal();

    /** Melt water in the drip pan, millibuckets. */
    int handson$pan();

    /**
     * Fills one empty container from the drip pan: a bucket from a full pan, a bottle from a bottleful. The container filled, or null
     * when the pan has not enough for it.
     */
    ItemStack handson$drawPan(ItemStack empty);

    /** Tips the drip pan out: the melt water is gone. */
    void handson$tipPan();

    default BlockEntity handson$entity() {
        return (BlockEntity) this;
    }

    /** The chiller of a block entity, or null for anything else. */
    static KitchenChiller of(BlockEntity entity) {
        return entity instanceof KitchenChiller chiller ? chiller : null;
    }

    static Block block() {
        return BuiltInRegistries.BLOCK.get(CHILLER_ID);
    }

    /**
     * A chilling recipe: what goes in (one of each, in any of the four places; none for a plain bar or popsicle), the liquid it sets
     * from and how much of it (none: the tank must be empty), the container it sets in and how many of it must stand there, whether
     * the container is used up (as many as the result counts), what comes out, and its ticks of cold.
     */
    record Setting(ResourceLocation id, List<Ingredient> ingredients, Fluid fluid, int amount, ItemStack container, boolean usesUp,
            ItemStack result, int ticks) {
        public boolean wantsLiquid() {
            return fluid != Fluids.EMPTY && amount > 0;
        }

        /** Whether a tank holding {@code mb} of {@code in} lets it set: enough of its liquid, or nothing at all where it wants none. */
        public boolean takes(Fluid in, int mb) {
            return wantsLiquid() ? in == fluid && mb >= amount : mb <= 0;
        }

        /** Whether {@code rack}, what stands in the container place, is its container, enough of it. */
        public boolean stands(ItemStack rack) {
            return container.isEmpty() ? rack.isEmpty() : ItemStack.isSameItem(container, rack) && rack.getCount() >= container.getCount();
        }

        public boolean isContainer(ItemStack stack) {
            return !container.isEmpty() && ItemStack.isSameItem(container, stack);
        }

        public boolean isThing(ItemStack stack) {
            return ingredients.stream().anyMatch(ingredient -> ingredient.test(stack));
        }

        /** How many containers one set uses up; none where the container stays (a mold, a tin). */
        public int usedUp() {
            return usesUp ? Math.max(1, result.getCount()) : 0;
        }

        /** How many containers must stand there for {@code sets} sets in a row. */
        public int containersFor(int sets) {
            if (container.isEmpty()) {
                return 0;
            }
            return usesUp ? Math.max(container.getCount(), usedUp() * sets) : container.getCount();
        }

        /** Whether the places, one stack each, are its ingredients, one each in any order, and nothing else. */
        public boolean made(ItemStack[] places) {
            List<ItemStack> lying = new ArrayList<>();
            for (ItemStack stack : places) {
                if (!stack.isEmpty()) {
                    lying.add(stack);
                }
            }
            return lying.size() == ingredients.size() && StationRecipes.matches(ingredients, lying);
        }
    }

    /** What only Extra Delight's own code knows: its recipes, the liquids of containers, and how long a piece of ice lasts. */
    interface Access {
        List<Setting> read(Level level);

        /** The liquid {@code stack} pours into the tank, if any: a bucket, a bottle Extra Delight knows, a water bottle. */
        Optional<KitchenEdVat.Liquid> liquidOf(ItemStack stack);

        /** The full containers of {@code fluid} a cook may pour it from, the bottle first. */
        List<ItemStack> containersOf(Fluid fluid);

        /** Ticks of cold one of {@code stack} gives as ice; 0 for anything that is no ice. */
        int chillOf(ItemStack stack);

        /** Every kind of ice there is, the longest lasting first. */
        List<ItemStack> ices();
    }

    /** The chiller's own code, or null where Extra Delight is not (and always on Fabric, which it has no build for). */
    static Access access() {
        return Platform.INSTANCE.edChillers();
    }

    /** The chiller's recipes as this level knows them (a client knows them too), in the order they were read. */
    static List<Setting> recipes(Level level) {
        return Recipes.of(level);
    }

    /** The recipes read last, kept for the recipe manager they came from: read again for another (a reload, another world). */
    final class Recipes {
        private static WeakReference<Object> readFrom = new WeakReference<>(null);
        private static List<Setting> read = List.of();

        private Recipes() {
        }

        static synchronized List<Setting> of(Level level) {
            Access access = access();
            if (access == null) {
                return List.of();
            }
            Object manager = level.getRecipeManager();
            if (readFrom.get() != manager) {
                read = List.copyOf(access.read(level));
                readFrom = new WeakReference<>(manager);
            }
            return read;
        }
    }

    /** The recipe the chiller sets as it stands: its things, its container and its liquid all right. */
    static Optional<Setting> making(List<Setting> recipes, ItemStack[] places, ItemStack rack, Fluid fluid, int amount) {
        return recipes.stream().filter(recipe -> recipe.made(places) && recipe.stands(rack) && recipe.takes(fluid, amount)).findFirst();
    }

    /**
     * The recipe the cook is setting up, also while its container or liquid is still missing: of those the things in the places make,
     * the one more of the rest is right for (the container on the rack, then the liquid). None while the chiller is empty; a recipe
     * without things (a plain bar, a popsicle) only once its liquid is in the tank, never for a container standing there alone.
     */
    static Optional<Setting> wanted(List<Setting> recipes, ItemStack[] places, ItemStack rack, Fluid fluid, int amount) {
        Setting best = null;
        int bestScore = 0;
        for (Setting recipe : recipes) {
            if (!recipe.made(places)) {
                continue;
            }
            boolean container = recipe.isContainer(rack);
            boolean liquid = recipe.wantsLiquid() ? amount > 0 && fluid == recipe.fluid() : amount <= 0;
            if (recipe.ingredients().isEmpty() && !(recipe.wantsLiquid() && liquid)) {
                continue;
            }
            int score = 1 + (container ? 4 : 0) + (recipe.stands(rack) ? 1 : 0) + (liquid ? 2 : 0) + (recipe.takes(fluid, amount) ? 1 : 0);
            if (score > bestScore) {
                best = recipe;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Whether {@code stack} is some recipe's container. */
    static boolean isContainer(List<Setting> recipes, ItemStack stack) {
        return !stack.isEmpty() && recipes.stream().anyMatch(recipe -> recipe.isContainer(stack));
    }

    /** Whether {@code stack} is a thing some recipe sets. */
    static boolean isThing(List<Setting> recipes, ItemStack stack) {
        return !stack.isEmpty() && recipes.stream().anyMatch(recipe -> recipe.isThing(stack));
    }

    /**
     * Whether a liquid in a container that is also a thing of some recipe (a milk bottle: fudge wants the bottle, ice cream the milk)
     * is poured rather than laid in: with a container on the rack, when no recipe of that container wants it as a thing or one of them
     * wants its liquid; with none, when any recipe wants its liquid.
     */
    static boolean pours(List<Setting> recipes, ItemStack rack, ItemStack stack, Fluid liquid) {
        if (rack.isEmpty()) {
            return recipes.stream().anyMatch(recipe -> recipe.fluid() == liquid);
        }
        boolean thing = false;
        boolean poured = false;
        for (Setting recipe : recipes) {
            if (recipe.isContainer(rack)) {
                thing |= recipe.isThing(stack);
                poured |= recipe.fluid() == liquid;
            }
        }
        return !thing || poured;
    }

    /**
     * Whether what is set up wants {@code stack} as its container: a recipe that sets in it from the liquid in the tank, and whose
     * things are the ones in the places or may still be laid into empty places (two glass bottles for a mousse in cream, a bowl for a
     * jelly, where a lone bottle or bowl would draw the liquid back out).
     */
    static boolean setsIn(List<Setting> recipes, ItemStack[] places, Fluid fluid, int amount, ItemStack stack) {
        boolean empty = true;
        for (ItemStack place : places) {
            empty &= place.isEmpty();
        }
        boolean nothingLaid = empty;
        return recipes.stream().anyMatch(recipe -> recipe.isContainer(stack) && (nothingLaid || recipe.made(places))
                && (recipe.wantsLiquid() ? amount > 0 && fluid == recipe.fluid() : amount <= 0));
    }

    /** Whether some recipe that sets from {@code fluid} takes {@code stack} as a thing: fluff for a cup of chocolate. */
    static boolean isThingIn(List<Setting> recipes, Fluid fluid, ItemStack stack) {
        return recipes.stream().anyMatch(recipe -> recipe.fluid() == fluid && recipe.isThing(stack));
    }
}
