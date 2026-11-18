package com.pockyl.handson_delight.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.pockyl.diegesis.api.station.StationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * An oven as the oven station sees it, whichever mod it comes from: a tray or rack of places where things bake, the places the baked
 * things wait in, and what else the oven has (a fuel slot, tool places on top). Each mod's oven is an adapter onto its own block
 * entity, the way {@link KitchenPot} serves several pots: Cooking for Blockheads' ({@link CfbOven}), each place baking alone, and
 * Cultural Delights' ({@link CulturalOven}), whose tray bakes as one set into a dish. The station, its door, tray, piles, glow, badge and book are
 * the same for all of them ({@link OvenLogic}, {@code OvenBehavior}, {@code OvenBook}).
 */
public interface KitchenOven {
    /** The places on the tray, row by row from the back, column 0 on the cook's left, as the oven's grid has them. */
    int GRID = 3;

    BlockEntity blockEntity();

    /** How many places the tray has (a 3 x 3 grid). */
    int places();

    ItemStack place(int index);

    void setPlace(int index, ItemStack stack);

    /** How many slots the baked things wait in. */
    int outputs();

    ItemStack output(int index);

    void setOutput(int index, ItemStack stack);

    /** Whether it burns fuel of its own (one slot); an oven without heats from what it stands on. */
    default boolean burnsFuel() {
        return false;
    }

    default ItemStack fuel() {
        return ItemStack.EMPTY;
    }

    default void setFuel(ItemStack stack) {
    }

    default boolean isFuel(ItemStack stack) {
        return false;
    }

    /** Tool places on top of it (bakeware, a pot, a skillet), none by default. */
    default int tools() {
        return 0;
    }

    default ItemStack tool(int index) {
        return ItemStack.EMPTY;
    }

    default void setTool(int index, ItemStack stack) {
    }

    default boolean isTool(ItemStack stack) {
        return false;
    }

    /**
     * Whether each place bakes on its own (one thing a place, each into what it bakes to), or the whole tray is one set that bakes into
     * a dish ({@link #recipes} of several ingredients).
     */
    boolean bakesEachPlace();

    /** The most things one place holds. */
    int perPlace();

    /** Whether {@code stack} may go on a place: what it bakes, or anything for an oven that bakes sets. */
    boolean takes(ItemStack stack);

    /** How far what lies on the place has baked, 0 to 1; 1 for what is done and waits there. */
    float progress(int place);

    /** Ticks until what lies on the place is baked, 0 when nothing bakes there. */
    int ticksLeft(int place);

    /** Whether it is hot: its fire burns, or the heat under it is on. */
    boolean isHot();

    /** Whether things on it wait for heat that is not there: no fuel burning, no heat under it. */
    boolean waitsForHeat();

    /** The oven's recipes as this level knows them (a client too): what goes in, what it bakes to, how long. */
    List<Baking> recipes(Level level);

    /** The oven's own hooks for something baked the cook takes out (experience, its events). */
    default void onTaken(Player player, ItemStack stack) {
        stack.onCraftedBy(player.level(), player, stack.getCount());
    }

    /**
     * Called every server tick of the station with the door open or shut: an oven that keeps its heat only shut holds its baking here
     * while the door is open. Nothing by default: the oven bakes as it always does.
     */
    default void whileDoor(StationContext station, boolean open) {
    }

    /**
     * A recipe: what goes in and what comes out after {@code ticks}. {@code width} 0: any places; otherwise laid out {@code width} x
     * {@code height} as at a crafting table, {@code ingredients} row by row with empty places. {@code kind} names the book's tab.
     */
    record Baking(ResourceLocation id, List<Ingredient> ingredients, int width, int height, ItemStack result, int ticks, String kind) {
        public boolean isShaped() {
            return width > 0;
        }

        /** What goes in, without the empty places of a laid-out recipe. */
        public List<Ingredient> needed() {
            return ingredients.stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        }
    }

    /** The recipe the tray (its places, row by row) bakes as one set, as such an oven finds it: a laid-out recipe first. */
    static Optional<Baking> matching(List<Baking> recipes, ItemStack[] grid) {
        for (Baking recipe : recipes) {
            if (recipe.isShaped() ? matchesShaped(recipe, grid) : matchesLoose(recipe, grid)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    /** What each place of an oven that bakes each place alone turns into, empty for what it does not bake. */
    static Optional<Baking> bakingOf(List<Baking> recipes, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return recipes.stream().filter(recipe -> recipe.ingredients().size() == 1 && recipe.ingredients().get(0).test(stack)).findFirst();
    }

    /** Laid out anywhere in the grid, unturned, unmirrored, nothing else in it. */
    private static boolean matchesShaped(Baking recipe, ItemStack[] grid) {
        for (int left = 0; left <= GRID - recipe.width(); left++) {
            for (int top = 0; top <= GRID - recipe.height(); top++) {
                if (fitsAt(recipe, grid, left, top)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean fitsAt(Baking recipe, ItemStack[] grid, int left, int top) {
        for (int x = 0; x < GRID; x++) {
            for (int y = 0; y < GRID; y++) {
                ItemStack there = grid[x + y * GRID];
                boolean inside = x >= left && x < left + recipe.width() && y >= top && y < top + recipe.height();
                Ingredient wanted = inside ? recipe.ingredients().get(x - left + (y - top) * recipe.width()) : Ingredient.EMPTY;
                if (inside ? !wanted.test(there) : !there.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** One stack for each ingredient, in any places, and nothing more. */
    private static boolean matchesLoose(Baking recipe, ItemStack[] grid) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : grid) {
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        List<Ingredient> wanted = recipe.ingredients();
        if (items.size() != wanted.size()) {
            return false;
        }
        int[] holder = new int[items.size()];
        Arrays.fill(holder, -1);
        for (int ingredient = 0; ingredient < wanted.size(); ingredient++) {
            if (!assign(ingredient, wanted, items, holder, new boolean[items.size()])) {
                return false;
            }
        }
        return true;
    }

    /** Finds a stack for {@code ingredient}, moving earlier ones to other stacks where they fit (a matching). */
    private static boolean assign(int ingredient, List<Ingredient> wanted, List<ItemStack> items, int[] holder, boolean[] seen) {
        for (int item = 0; item < items.size(); item++) {
            if (seen[item] || !wanted.get(ingredient).test(items.get(item))) {
                continue;
            }
            seen[item] = true;
            if (holder[item] < 0 || assign(holder[item], wanted, items, holder, seen)) {
                holder[item] = ingredient;
                return true;
            }
        }
        return false;
    }

    /** The oven of a block entity, from whichever mod it comes, or null for anything else. */
    static KitchenOven of(BlockEntity entity) {
        KitchenOven cfb = CfbOven.of(entity);
        return cfb != null ? cfb : CulturalOven.of(entity);
    }
}
