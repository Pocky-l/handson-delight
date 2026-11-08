package com.pockyl.handson_delight.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.assembly.AssemblyRecipes;
import com.pockyl.handson_delight.platform.Platform;

/**
 * A sandwich is put together on the cutting board, by hand: a player's crafting table and inventory grid do not make it. Automation
 * crafts with grids of its own (Create's crafters, AE2's assemblers, Refined Storage) and goes on making it, as without this mod. So
 * does a player whose client lacks the mod: it cannot open the board, the crafting table stays theirs.
 */
@Mixin(ShapelessRecipe.class)
abstract class ShapelessRecipeMixin {
    @Inject(method = "matches(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"),
            cancellable = true)
    private void handson$onTheBoardInstead(CraftingContainer grid, Level level, CallbackInfoReturnable<Boolean> callback) {
        ShapelessRecipe recipe = (ShapelessRecipe) (Object) this;
        if (grid instanceof TransientCraftingContainer table && HandsOnConfig.removeCraftingRecipes()
                && AssemblyRecipes.isMadeOnTheBoard(recipe.getId())) {
            AbstractContainerMenu menu = ((TransientCraftingContainerAccessor) table).handson$menu();
            Player player = menu instanceof CraftingMenu crafting ? ((CraftingMenuAccessor) crafting).handson$player()
                    : menu instanceof InventoryMenu inventory ? ((InventoryMenuAccessor) inventory).handson$owner() : null;
            if (player != null && !(player instanceof ServerPlayer server && !Platform.INSTANCE.hasThisMod(server))) {
                callback.setReturnValue(false);
            }
        }
    }
}
