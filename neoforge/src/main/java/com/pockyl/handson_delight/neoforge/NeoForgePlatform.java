package com.pockyl.handson_delight.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import net.neoforged.neoforgespi.language.IModInfo;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Farmer's Delight on NeoForge: the pot keeps its items in NeoForge's item handler. */
public final class NeoForgePlatform implements Platform {
    @Override
    public ItemStack getItem(CookingPotBlockEntity pot, int slot) {
        return pot.getInventory().getStackInSlot(slot);
    }

    @Override
    public void setItem(CookingPotBlockEntity pot, int slot, ItemStack stack) {
        pot.getInventory().setStackInSlot(slot, stack);
    }

    @Override
    public Optional<RecipeHolder<CookingPotRecipe>> findRecipe(Level level, List<ItemStack> items) {
        return level.getRecipeManager().getRecipeFor(ModRecipeTypes.COOKING.get(), new RecipeWrapper(slots(items)), level);
    }

    @Override
    public ItemStack cooked(Level level, CookingPotRecipe recipe, List<ItemStack> items) {
        return recipe.assemble(new RecipeWrapper(slots(items)), level.registryAccess());
    }

    /** The pot's slots holding copies of {@code items}, from the first one on. */
    private static ItemStackHandler slots(List<ItemStack> items) {
        ItemStackHandler slots = new ItemStackHandler(CookingPotBlockEntity.INVENTORY_SIZE);
        for (int i = 0; i < Math.min(items.size(), CookingPotBlockEntity.MEAL_DISPLAY_SLOT); i++) {
            slots.setStackInSlot(i, items.get(i).copy());
        }
        return slots;
    }

    @Override
    public List<RecipeHolder<CookingPotRecipe>> cookingRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.COOKING.get());
    }

    @Override
    public ItemStack getItem(CuttingBoardBlockEntity board) {
        return board.getInventory().getStackInSlot(0);
    }

    @Override
    public void setItem(CuttingBoardBlockEntity board, ItemStack stack) {
        ((IItemHandlerModifiable) board.getInventory()).setStackInSlot(0, stack);
    }

    @Override
    public void setItem(SkilletBlockEntity skillet, ItemStack stack) {
        ((IItemHandlerModifiable) skillet.getInventory()).setStackInSlot(0, stack);
    }

    @Override
    public ItemStack getItem(AbstractStoveBlockEntity stove, int slot) {
        return stove.getItems().getStackInSlot(slot);
    }

    @Override
    public void setItem(AbstractStoveBlockEntity stove, int slot, ItemStack stack) {
        stove.getItems().setStackInSlot(slot, stack);
    }

    @Override
    public boolean isFarmersDelightAddon(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(mod -> mod.getModInfo().getDependencies().stream()
                        .anyMatch(dependency -> dependency.getType() == IModInfo.DependencyType.REQUIRED
                                && dependency.getModId().equals(FarmersDelightContent.MOD_ID)))
                .orElse(false);
    }

    @Override
    public void sendBoardDishes(ServerPlayer player) {
        NeoForgeNetwork.send(player);
    }

    @Override
    public boolean hasThisMod(ServerPlayer player) {
        // No connection, or one that goes nowhere (game tests): no client.
        return player instanceof FakePlayer || player.connection == null || player.connection.getConnection().channel() == null
                || NeoForgeNetwork.present(player);
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}
