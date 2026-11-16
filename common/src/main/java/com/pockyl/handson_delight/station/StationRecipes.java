package com.pockyl.handson_delight.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.pockyl.handson_delight.Cookable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * What the food in a station makes, whichever way it lies. The pot, the keg and the kettle read a recipe one ingredient a slot, and a
 * cook throws food in as into a pot: alike items lie together. Here a station's food is read as kinds and counts, the recipe it makes
 * is chosen, and the slots are laid out the way the station reads that recipe; the station's own mod, its menu, the book and the
 * station's view then see the same dish. Each station hands in its recipes as {@link Candidate}s, already filtered by what it holds
 * besides the food (a liquid, a scroll).
 */
public final class StationRecipes {
    private StationRecipes() {
    }

    /**
     * Sent by a station's view when a stack is clicked onto a group of slots that has no place for it, every slot holding other food:
     * the logic answers with {@link #offer}. Data: {@link #ONE}.
     */
    public static final String ACTION_OFFER = "offer";
    /** Data of {@link #ACTION_OFFER}: one of the stack, not the whole stack. */
    public static final String ONE = "one";

    /** A recipe of a station as this reads it: its id and its ingredients, one slot each, with the station's own recipe object. */
    public record Candidate<T>(T recipe, ResourceLocation id, List<Ingredient> ingredients) {
        public static <T> Candidate<T> of(T recipe, ResourceLocation id, List<Ingredient> ingredients) {
            return new Candidate<>(recipe, id, ingredients.stream().filter(ingredient -> !ingredient.isEmpty()).toList());
        }
    }

    /** Wraps a station's recipes, in their order. */
    public static <T> List<Candidate<T>> candidates(List<T> recipes, Function<T, ResourceLocation> id, Function<T, List<Ingredient>> ingredients) {
        List<Candidate<T>> out = new ArrayList<>(recipes.size());
        for (T recipe : recipes) {
            out.add(Candidate.of(recipe, id.apply(recipe), ingredients.apply(recipe)));
        }
        return out;
    }

    /**
     * A recipe the food makes: in how many slots every kind lies, and whether every kind fits its slots (with too much of a kind the
     * rest goes back to the cook).
     */
    public record Choice<T>(Candidate<T> candidate, int[] parts, boolean fits) {
    }

    /** The food in {@code slots} as kinds: alike stacks counted together, in the order of the slots. */
    public static List<ItemStack> kinds(SlotStorage slots) {
        List<ItemStack> kinds = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            add(kinds, slots.get(i));
        }
        return kinds;
    }

    /**
     * {@code offered} goes into {@code slots} that have no place for it, when with what lies there the food still makes a dish
     * ({@code choose} gives each kind's slots, or null) that keeps all of what lay there. The food is laid out anew for that dish: a kind
     * that two tags of the dish read gives a slot up to the new one (a second stack of tomatoes over both vegetables, then carrots,
     * GH #34). How the food lies inside is never seen, so the station takes what the cook means to cook. Returns how many of
     * {@code offered} went in, at most what its slots hold; none when no such dish.
     */
    public static int offer(SlotStorage slots, ItemStack offered, Function<List<ItemStack>, int[]> choose, int perSlot) {
        if (offered.isEmpty()) {
            return 0;
        }
        List<ItemStack> kinds = kinds(slots);
        List<ItemStack> with = new ArrayList<>();
        for (ItemStack kind : kinds) {
            with.add(kind.copy());
        }
        add(with, offered);
        int[] parts = choose.apply(with);
        if (parts == null || parts.length != with.size()) {
            return 0;
        }
        int at = -1;
        for (int kind = 0; kind < with.size(); kind++) {
            int held = Math.min(with.get(kind).getCount(), parts[kind] * hold(with.get(kind), perSlot));
            int had = kind < kinds.size() ? kinds.get(kind).getCount() : 0;
            if (held < had) {
                return 0;
            }
            if (ItemStack.isSameItemSameComponents(with.get(kind), offered)) {
                at = kind;
            }
        }
        int had = at < kinds.size() ? kinds.get(at).getCount() : 0;
        int taken = Math.min(with.get(at).getCount(), parts[at] * hold(with.get(at), perSlot)) - had;
        if (taken <= 0) {
            return 0;
        }
        with.set(at, with.get(at).copyWithCount(had + taken));
        lay(slots, with, parts, perSlot);
        return taken;
    }

    /**
     * {@link #offer} of the stack on {@code player}'s cursor (one of it with {@code one}) to the station's slot {@code group}, held by
     * {@code slots}: only what the group and the logic take ({@code mayPlace}). Returns whether some went in.
     */
    public static boolean offerCarried(StationContext station, StationLogic logic, Player player, String group, SlotStorage slots,
            boolean one, Function<List<ItemStack>, int[]> choose) {
        ItemStack carried = player.inventoryMenu.getCarried();
        StationSchema.SlotGroup slotGroup = station.type().schema().slotGroups().get(group);
        if (carried.isEmpty() || slotGroup == null || !slotGroup.rules().accepts().test(carried)
                || !logic.mayPlace(station, player, new SlotRef(group, 0), carried)) {
            return false;
        }
        int taken = offer(slots, carried.copyWithCount(one ? 1 : carried.getCount()), choose, slotGroup.rules().limitFor(carried));
        if (taken <= 0) {
            return false;
        }
        carried.shrink(taken);
        player.inventoryMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        return true;
    }

    /** Counts {@code stack} into {@code kinds}; a count may grow past a stack. */
    public static void add(List<ItemStack> kinds, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (ItemStack kind : kinds) {
            if (ItemStack.isSameItemSameComponents(kind, stack)) {
                kind.grow(stack.getCount());
                return;
            }
        }
        kinds.add(stack.copy());
    }

    /**
     * Whether {@code stacks}, one a slot, are {@code ingredients}, one each in any order. Every way is tried: a tag that takes what
     * another ingredient wants is given something else (the first-come way read a cabbage and a carrot as no kimchi).
     */
    public static boolean matches(List<Ingredient> ingredients, List<ItemStack> stacks) {
        List<Ingredient> wanted = ingredients.stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        List<ItemStack> items = stacks.stream().filter(stack -> !stack.isEmpty()).toList();
        if (wanted.size() != items.size()) {
            return false;
        }
        int[] holder = new int[items.size()];
        Arrays.fill(holder, -1);
        for (int ingredient = 0; ingredient < wanted.size(); ingredient++) {
            if (!give(ingredient, wanted, items, holder, new boolean[items.size()])) {
                return false;
            }
        }
        return true;
    }

    private static boolean give(int ingredient, List<Ingredient> wanted, List<ItemStack> items, int[] holder, boolean[] seen) {
        for (int item = 0; item < items.size(); item++) {
            if (seen[item] || !wanted.get(ingredient).test(items.get(item))) {
                continue;
            }
            seen[item] = true;
            if (holder[item] < 0 || give(holder[item], wanted, items, holder, seen)) {
                holder[item] = ingredient;
                return true;
            }
        }
        return false;
    }

    /** The first recipe, in their order, that {@code slots} make one ingredient a slot, as the stations' own mods read them; or null. */
    public static <T> Candidate<T> reads(List<Candidate<T>> candidates, List<ItemStack> slots) {
        for (Candidate<T> candidate : candidates) {
            if (!candidate.ingredients().isEmpty() && matches(candidate.ingredients(), slots)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * In how many slots every kind lies for {@code ingredients}: each ingredient takes an item of a kind it accepts, every kind goes to
     * some ingredient, and no kind gives more items than it has. Of the ways that work (a tag may take what another ingredient wants),
     * the one that makes the most servings; null when none does.
     */
    public static int[] parts(List<Ingredient> ingredients, List<ItemStack> kinds) {
        if (kinds.isEmpty() || kinds.size() > ingredients.size()) {
            return null;
        }
        boolean[][] takes = new boolean[ingredients.size()][kinds.size()];
        boolean[] used = new boolean[kinds.size()];
        for (int i = 0; i < ingredients.size(); i++) {
            boolean any = false;
            for (int kind = 0; kind < kinds.size(); kind++) {
                takes[i][kind] = ingredients.get(i).test(kinds.get(kind));
                any |= takes[i][kind];
                used[kind] |= takes[i][kind];
            }
            if (!any) {
                return null;
            }
        }
        for (boolean kind : used) {
            if (!kind) {
                return null;
            }
        }
        int[][] best = new int[1][];
        assign(0, takes, kinds, new int[kinds.size()], best);
        return best[0];
    }

    private static void assign(int ingredient, boolean[][] takes, List<ItemStack> kinds, int[] parts, int[][] best) {
        int unused = 0;
        for (int part : parts) {
            unused += part == 0 ? 1 : 0;
        }
        if (unused > takes.length - ingredient) {
            return;
        }
        if (ingredient == takes.length) {
            if (best[0] == null || servings(kinds, parts) > servings(kinds, best[0])) {
                best[0] = parts.clone();
            }
            return;
        }
        for (int kind = 0; kind < kinds.size(); kind++) {
            if (takes[ingredient][kind] && parts[kind] < kinds.get(kind).getCount()) {
                parts[kind]++;
                assign(ingredient + 1, takes, kinds, parts, best);
                parts[kind]--;
            }
        }
    }

    /** How many of {@code kind} one slot holds: a stack, or {@code perSlot} where the station takes fewer (one leaf a slot). */
    private static int hold(ItemStack kind, int perSlot) {
        return Math.min(kind.getMaxStackSize(), perSlot);
    }

    /** How many servings the kinds make laid over {@code parts} slots: the smallest slot sets it. */
    public static int servings(List<ItemStack> kinds, int[] parts) {
        return servings(kinds, parts, Integer.MAX_VALUE);
    }

    private static int servings(List<ItemStack> kinds, int[] parts, int perSlot) {
        int servings = Integer.MAX_VALUE;
        for (int kind = 0; kind < parts.length; kind++) {
            servings = Math.min(servings, Math.min(kinds.get(kind).getCount(), parts[kind] * hold(kinds.get(kind), perSlot)) / parts[kind]);
        }
        return servings;
    }

    /** Whether every kind fits the slots {@code parts} gives it, a stack a slot. */
    public static boolean fits(List<ItemStack> kinds, int[] parts) {
        return fits(kinds, parts, Integer.MAX_VALUE);
    }

    private static boolean fits(List<ItemStack> kinds, int[] parts, int perSlot) {
        for (int kind = 0; kind < parts.length; kind++) {
            if (kinds.get(kind).getCount() > parts[kind] * hold(kinds.get(kind), perSlot)) {
                return false;
            }
        }
        return true;
    }

    /** How many items the slots {@code parts} gives every kind hold. */
    public static int kept(List<ItemStack> kinds, int[] parts) {
        return kept(kinds, parts, Integer.MAX_VALUE);
    }

    private static int kept(List<ItemStack> kinds, int[] parts, int perSlot) {
        int kept = 0;
        for (int kind = 0; kind < parts.length; kind++) {
            kept += Math.min(kinds.get(kind).getCount(), parts[kind] * hold(kinds.get(kind), perSlot));
        }
        return kept;
    }

    /** How many items stay uncooked once the kinds made all the servings they make. */
    private static int leftOver(List<ItemStack> kinds, int[] parts) {
        int total = 0;
        int ingredients = 0;
        for (int kind = 0; kind < parts.length; kind++) {
            total += kinds.get(kind).getCount();
            ingredients += parts[kind];
        }
        return total - servings(kinds, parts) * ingredients;
    }

    /**
     * The recipe {@code kinds} make in a station of {@code slots} ingredient slots, or null. The dish the cook picked in the book comes
     * first while the food still makes it. Then a dish the food makes whole, with nothing over, and of those the one of more
     * ingredients: two sugars and a dye make the dish of sugar, sugar and dye, not the one of sugar and dye; six coffee beans make Rustic
     * Delight's dark coffee of six, not two coffees of three. Food no dish takes whole makes the most servings (ten corn and six milk
     * make six elote, not five creamed corn), then {@code found}, the dish the station reads in the slots as they lie, so that it cooks
     * as it did, then the dish that leaves least over. Last the order of the recipes, as the station's mod reads them. A recipe nobody
     * can make is never chosen. With {@code overflow}, when no recipe holds all of it, the one that keeps the most is chosen and the
     * rest is for the cook.
     */
    public static <T> Choice<T> choose(List<Candidate<T>> candidates, List<ItemStack> kinds, int slots, ResourceLocation wanted,
            ResourceLocation found, boolean overflow) {
        List<Choice<T>> held = new ArrayList<>();
        List<Choice<T>> spilt = new ArrayList<>();
        for (Candidate<T> candidate : candidates) {
            List<Ingredient> ingredients = candidate.ingredients();
            if (ingredients.isEmpty() || ingredients.size() > slots || !Cookable.all(ingredients)) {
                continue;
            }
            int[] parts = parts(ingredients, kinds);
            if (parts != null) {
                boolean fits = fits(kinds, parts);
                (fits ? held : spilt).add(new Choice<>(candidate, parts, fits));
            }
        }
        // Stream.min keeps the first of equals: ties go by the order of the recipes.
        Comparator<Choice<T>> picked = Comparator.comparing(choice -> !choice.candidate().id().equals(wanted));
        if (!held.isEmpty()) {
            return held.stream().min(picked.thenComparing((first, second) -> rank(kinds, found, first, second))).orElseThrow();
        }
        if (!overflow || spilt.isEmpty()) {
            return null;
        }
        return spilt.stream().min(picked.thenComparingInt((Choice<T> choice) -> -kept(kinds, choice.parts()))).orElseThrow();
    }

    /** Which of two recipes the food rather makes, below 0 for {@code first}: see {@link #choose}. */
    private static <T> int rank(List<ItemStack> kinds, ResourceLocation found, Choice<T> first, Choice<T> second) {
        int overFirst = leftOver(kinds, first.parts());
        int overSecond = leftOver(kinds, second.parts());
        if ((overFirst == 0) != (overSecond == 0)) {
            return overFirst == 0 ? -1 : 1;
        }
        int sizeFirst = first.candidate().ingredients().size();
        int sizeSecond = second.candidate().ingredients().size();
        int servingsFirst = servings(kinds, first.parts());
        int servingsSecond = servings(kinds, second.parts());
        if (overFirst == 0 && sizeFirst != sizeSecond) {
            return sizeSecond - sizeFirst;
        }
        if (servingsFirst != servingsSecond) {
            return servingsSecond - servingsFirst;
        }
        boolean foundFirst = first.candidate().id().equals(found);
        if (foundFirst != second.candidate().id().equals(found)) {
            return foundFirst ? -1 : 1;
        }
        if (overFirst != overSecond) {
            return overFirst - overSecond;
        }
        return sizeSecond - sizeFirst;
    }

    /**
     * Lays every kind evenly over its {@code parts} slots of {@code slots}, at most a full stack a slot; returns what did not fit. A kind
     * keeps the slots it lay in, so that what floats in the station stays where it was; a slot whose stack is already right is left
     * alone.
     */
    public static List<ItemStack> lay(SlotStorage slots, List<ItemStack> kinds, int[] parts) {
        return lay(slots, kinds, parts, Integer.MAX_VALUE);
    }

    /** {@link #lay(SlotStorage, List, int[])} in a station whose slots hold at most {@code perSlot} each. */
    public static List<ItemStack> lay(SlotStorage slots, List<ItemStack> kinds, int[] parts, int perSlot) {
        int size = slots.size();
        int[] kindAt = new int[size];
        Arrays.fill(kindAt, -1);
        int[] placed = new int[kinds.size()];
        for (int slot = 0; slot < size; slot++) {
            ItemStack there = slots.get(slot);
            for (int kind = 0; kind < kinds.size() && !there.isEmpty(); kind++) {
                if (placed[kind] < parts[kind] && ItemStack.isSameItemSameComponents(there, kinds.get(kind))) {
                    kindAt[slot] = kind;
                    placed[kind]++;
                    break;
                }
            }
        }
        int free = 0;
        for (int kind = 0; kind < kinds.size(); kind++) {
            while (placed[kind] < parts[kind]) {
                while (kindAt[free] >= 0) {
                    free++;
                }
                kindAt[free] = kind;
                placed[kind]++;
            }
        }
        List<ItemStack> rest = new ArrayList<>();
        int[] left = new int[kinds.size()];
        int[] partsLeft = parts.clone();
        for (int kind = 0; kind < kinds.size(); kind++) {
            ItemStack stack = kinds.get(kind);
            left[kind] = Math.min(stack.getCount(), parts[kind] * hold(stack, perSlot));
            if (left[kind] < stack.getCount()) {
                rest.add(stack.copyWithCount(stack.getCount() - left[kind]));
            }
        }
        for (int slot = 0; slot < size; slot++) {
            int kind = kindAt[slot];
            ItemStack laid = ItemStack.EMPTY;
            if (kind >= 0) {
                int share = (left[kind] + partsLeft[kind] - 1) / partsLeft[kind];
                laid = kinds.get(kind).copyWithCount(share);
                left[kind] -= share;
                partsLeft[kind]--;
            }
            if (!ItemStack.matches(slots.get(slot), laid)) {
                slots.set(slot, laid);
            }
        }
        return rest;
    }

    /** What a pick in the book put in: the food now in the station, laid out over {@code parts}; {@code sets} whole sets of it. */
    public record Filled(List<ItemStack> kinds, int[] parts, int sets) {
    }

    /**
     * Takes up to {@code sets} whole sets of {@code ingredients} from the inventory into the food a station holds ({@code kinds}), as
     * many as its {@code slots} hold laid out for the recipe; null when not one set goes in. A station that holds food the recipe does
     * not use takes nothing. Items alike to what the station holds come first, so that its stacks stay alike. The caller lays the
     * result out.
     */
    public static Filled fill(Inventory inventory, List<Ingredient> ingredients, List<ItemStack> kinds, int slots, int sets) {
        return fill(inventory, ingredients, kinds, slots, sets, Integer.MAX_VALUE);
    }

    /** {@link #fill(Inventory, List, List, int, int)} for a station whose slots hold at most {@code perSlot} each. */
    public static Filled fill(Inventory inventory, List<Ingredient> ingredients, List<ItemStack> kinds, int slots, int sets, int perSlot) {
        if (ingredients.isEmpty() || ingredients.size() > slots || !Cookable.all(ingredients)) {
            return null;
        }
        for (ItemStack kind : kinds) {
            if (ingredients.stream().noneMatch(ingredient -> ingredient.test(kind))) {
                return null;
            }
        }
        List<ItemStack> now = kinds;
        int[] parts = null;
        int done = 0;
        while (done < sets) {
            int[] from = new int[ingredients.size()];
            List<ItemStack> more = new ArrayList<>();
            now.forEach(kind -> more.add(kind.copy()));
            for (int i = 0; i < from.length; i++) {
                from[i] = source(inventory, ingredients.get(i), now, from, i);
                if (from[i] < 0) {
                    break;
                }
                add(more, inventory.getItem(from[i]).copyWithCount(1));
            }
            int[] laid = Arrays.stream(from).anyMatch(slot -> slot < 0) ? null : parts(ingredients, more);
            if (laid == null || !fits(more, laid, perSlot)) {
                break;
            }
            for (int slot : from) {
                inventory.removeItem(slot, 1);
            }
            now = more;
            parts = laid;
            done++;
        }
        return parts == null ? null : new Filled(now, parts, done);
    }

    /**
     * The inventory slot that gives the next item for {@code ingredient}: one alike to what the station holds first, and not wanted by
     * an earlier ingredient of this set beyond what it holds; -1 for none.
     */
    private static int source(Inventory inventory, Ingredient ingredient, List<ItemStack> held, int[] taken, int index) {
        int any = -1;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !ingredient.test(stack)) {
                continue;
            }
            int wanted = 1;
            for (int earlier = 0; earlier < index; earlier++) {
                wanted += taken[earlier] == slot ? 1 : 0;
            }
            if (stack.getCount() < wanted) {
                continue;
            }
            if (held.stream().anyMatch(kind -> ItemStack.isSameItemSameComponents(kind, stack))) {
                return slot;
            }
            if (any < 0) {
                any = slot;
            }
        }
        return any;
    }
}
