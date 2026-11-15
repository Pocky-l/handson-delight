package com.pockyl.handson_delight.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.handson_delight.Cookable;
import com.pockyl.handson_delight.station.StationRecipes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Every recipe of a station made the way a cook makes it: its food thrown in, where alike items lie together, and its page picked in
 * the book. One set thrown in must lie as that very recipe; two or three sets must still make a dish; a pick in the book must lay
 * out the recipe picked. Run over the recipes the game has, with or without addons: a new addon's recipe that a station cannot make
 * fails here, not in a player's world.
 */
final class StationAudit {
    private StationAudit() {
    }

    /**
     * Audits {@code candidates}, a station of {@code slots} ingredient slots; {@code base} is what a recipe needs besides its food (a
     * liquid), and only recipes of the same base compete for it.
     */
    static <T> void audit(GameTestHelper helper, String station, List<StationRecipes.Candidate<T>> candidates, Function<T, Object> base,
            int slots) {
        List<String> failures = new ArrayList<>();
        int audited = 0;
        for (StationRecipes.Candidate<T> recipe : candidates) {
            List<Ingredient> ingredients = recipe.ingredients();
            if (ingredients.isEmpty() || ingredients.size() > slots || !Cookable.all(ingredients)) {
                continue;
            }
            audited++;
            List<StationRecipes.Candidate<T>> rivals = candidates.stream()
                    .filter(other -> Objects.equals(base.apply(other.recipe()), base.apply(recipe.recipe()))).toList();
            List<ItemStack> set = new ArrayList<>();
            for (Ingredient ingredient : ingredients) {
                set.add(Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty() && !stack.is(Items.BARRIER)).findFirst().orElseThrow()
                        .copyWithCount(1));
            }
            for (int times = 1; times <= 3; times++) {
                List<ItemStack> kinds = new ArrayList<>();
                for (int t = 0; t < times; t++) {
                    set.forEach(stack -> StationRecipes.add(kinds, stack));
                }
                List<ItemStack> thrown = thrown(kinds);
                if (thrown.size() > slots) {
                    continue;
                }
                StationRecipes.Candidate<T> found = StationRecipes.reads(rivals, thrown);
                StationRecipes.Choice<T> choice = StationRecipes.choose(rivals, kinds, slots, null, found == null ? null : found.id(), false);
                if (choice == null) {
                    if (StationRecipes.parts(ingredients, kinds) != null && StationRecipes.fits(kinds, StationRecipes.parts(ingredients, kinds))) {
                        failures.add(recipe.id() + " x" + times + " thrown in: nothing chosen");
                    }
                    continue;
                }
                List<ItemStack> laid = laid(slots, thrown, kinds, choice.parts());
                if (StationRecipes.reads(rivals, laid) == null) {
                    failures.add(recipe.id() + " x" + times + " thrown in: laid out as " + choice.candidate().id() + ", which nothing reads");
                } else if (times == 1 && !StationRecipes.matches(ingredients, laid)) {
                    failures.add(recipe.id() + " x1 thrown in: laid out as " + choice.candidate().id());
                }
            }
            Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            Inventory inventory = player.getInventory();
            for (int t = 0; t < 2; t++) {
                set.forEach(stack -> inventory.add(stack.copy()));
            }
            StationRecipes.Filled filled = StationRecipes.fill(inventory, ingredients, new ArrayList<>(), slots, 2);
            if (filled == null) {
                failures.add(recipe.id() + " from the book: nothing went in");
            } else if (!StationRecipes.matches(ingredients, laid(slots, List.of(), filled.kinds(), filled.parts()))) {
                failures.add(recipe.id() + " from the book: not laid out as itself");
            }
        }
        System.out.println("[station audit] " + station + ": " + audited + " recipes, " + failures.size() + " failures");
        failures.forEach(failure -> System.out.println("[station audit] " + station + ": " + failure));
        helper.assertTrue(audited > 0, station + ": no recipe to audit");
        helper.assertTrue(failures.isEmpty(), station + ": " + failures.size() + " cannot be made, first: " + (failures.isEmpty() ? "" : failures.get(0)));
        helper.succeed();
    }

    /** The kinds as a cook throws them in: alike items stacked, a stack a slot. */
    private static List<ItemStack> thrown(List<ItemStack> kinds) {
        List<ItemStack> thrown = new ArrayList<>();
        for (ItemStack kind : kinds) {
            for (int left = kind.getCount(); left > 0; left -= kind.getMaxStackSize()) {
                thrown.add(kind.copyWithCount(Math.min(left, kind.getMaxStackSize())));
            }
        }
        return thrown;
    }

    /** The slots as {@link StationRecipes#lay} leaves them, starting from {@code before}. */
    private static List<ItemStack> laid(int size, List<ItemStack> before, List<ItemStack> kinds, int[] parts) {
        ItemStack[] slots = new ItemStack[size];
        Arrays.fill(slots, ItemStack.EMPTY);
        for (int i = 0; i < before.size() && i < size; i++) {
            slots[i] = before.get(i).copy();
        }
        StationRecipes.lay(new SlotStorage() {
            @Override
            public int size() {
                return size;
            }

            @Override
            public ItemStack get(int index) {
                return slots[index];
            }

            @Override
            public void set(int index, ItemStack stack) {
                slots[index] = stack;
            }
        }, kinds, parts);
        return Arrays.asList(slots);
    }
}
