package com.pockyl.handson_delight.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.assembly.AssemblyRecipes;
import com.pockyl.handson_delight.station.BoardLogic;
import com.pockyl.handson_delight.station.Cutting;
import com.pockyl.handson_delight.station.CuttingBoardStation;

import java.util.ArrayList;
import java.util.List;

/**
 * The board's book: what can be cut, listed by what comes off (a cabbage leaf: a cabbage and a knife, with everything else the cut
 * gives at the foot), and the dishes put together on it, bread first. The dishes come from the station's data: a dedicated server
 * sends their recipes nowhere else.
 */
final class BoardBook implements RecipeBook.Source {
    /** The dishes' recipes as the server last sent them ({@code CuttingBoardStation.EFFECT_BOOK}): the same for every board. */
    private static ListTag received = new ListTag();
    private ListTag readFrom;
    private List<RecipeBook.Page> dishes = List.of();
    /** Goes up each time the dishes are read anew: what the drawer shows depends on them. */
    private int version;

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        Level level = view.level();
        List<RecipeBook.Page> pages = new ArrayList<>(dishes(view));
        for (CuttingBoardRecipe recipe : Cutting.all(level)) {
            List<ItemStack> results = recipe.getResults();
            if (results.isEmpty()) {
                continue;
            }
            List<Ingredient> ingredients = List.of(recipe.getIngredients().get(0), recipe.getTool());
            List<ItemStack> foot = results.size() > 1 ? results : List.of();
            pages.add(new RecipeBook.Page(Cutting.id(level, recipe), results.get(0).copy(), ingredients, foot, null, false));
        }
        return pages;
    }

    static void received(ListTag book) {
        received = book;
    }

    private List<RecipeBook.Page> dishes(StationView view) {
        ListTag book = received;
        if (book != readFrom) {
            readFrom = book;
            List<RecipeBook.Page> read = new ArrayList<>();
            for (AssemblyRecipes.Assembly dish : AssemblyRecipes.read(book)) {
                List<Ingredient> ingredients = new ArrayList<>(dish.ingredients());
                // The bread first: it goes down first.
                ingredients.add(0, ingredients.remove(dish.base()));
                ItemStack result = dish.recipe().getResultItem(view.level().registryAccess()).copy();
                read.add(new RecipeBook.Page(dish.id(), result, ingredients, List.of(), null, false));
            }
            dishes = read;
            version++;
        }
        return dishes;
    }

    /** The dishes as last read, a new number each time they change. */
    int version(StationView view) {
        dishes(view);
        return version;
    }

    /** Whether {@code stack} goes into one of the dishes put together on the board. */
    boolean goesIntoADish(StationView view, ItemStack stack) {
        return dishes(view).stream().anyMatch(page -> page.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }

    /** A dish picked in the book is the one the board makes as soon as the layers make it, even if they could grow into more. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        if (dishes.contains(page)) {
            CompoundTag pick = new CompoundTag();
            pick.putString(CuttingBoardStation.DISH, page.key().toString());
            view.sendAction(CuttingBoardStation.ACTION_PICK, pick);
        }
    }

    /** The dish the layers make, else what lies on the board to be cut. */
    @Override
    public Object current(StationView view) {
        String dish = view.state().data().getString(CuttingBoardStation.DISH);
        if (!dish.isEmpty()) {
            for (RecipeBook.Page page : dishes(view)) {
                if (BuiltInRegistries.ITEM.getKey(page.dish().getItem()).toString().equals(dish)) {
                    return page.key();
                }
            }
        }
        ItemStack onBoard = view.state().getItem(CuttingBoardStation.BOARD_SLOT);
        return Cutting.recipesFor(view.level(), onBoard).stream().findFirst().map(recipe -> Cutting.id(view.level(), recipe)).orElse(null);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        List<ItemStack> there = new ArrayList<>(BoardLogic.layers(view.state()));
        there.add(view.state().getItem(CuttingBoardStation.BOARD_SLOT));
        return there;
    }
}
