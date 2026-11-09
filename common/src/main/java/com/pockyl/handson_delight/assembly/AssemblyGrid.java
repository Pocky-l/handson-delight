package com.pockyl.handson_delight.assembly;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A 3 x 3 crafting grid the board lays a dish's layers in, one item each where the recipe wants it, so the recipe itself makes the
 * dish and says what is left of its ingredients: a bucket of milk gives back the bucket, FD's dough keeps its water. No player's
 * menu owns it: the crafting table's rule does not apply.
 */
final class AssemblyGrid implements CraftingContainer {
    private static final int SIZE = 3;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE * SIZE, ItemStack.EMPTY);

    /** One item of every layer, layer i where its ingredient lies in the recipe. */
    AssemblyGrid(AssemblyRecipes.Assembly dish, List<ItemStack> layers, int[] taken) {
        for (int layer = 0; layer < layers.size(); layer++) {
            items.set(dish.slots()[taken[layer]], layers.get(layer).copyWithCount(1));
        }
    }

    /** The grid slot of layer {@code layer}. */
    static int slot(AssemblyRecipes.Assembly dish, int[] taken, int layer) {
        return dish.slots()[taken[layer]];
    }

    @Override
    public int getWidth() {
        return SIZE;
    }

    @Override
    public int getHeight() {
        return SIZE;
    }

    @Override
    public List<ItemStack> getItems() {
        return List.copyOf(items);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public void fillStackedContents(StackedContents contents) {
        items.forEach(contents::accountSimpleStack);
    }
}
