package com.pockyl.handson_delight.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.assembly.AssemblyRecipes;

/**
 * Food put together on the cutting board: its recipes are found as the manager is made and kept for the board as well. They stay in
 * the manager, for automation; a player's crafting grid does not make them ({@code CraftingMenuMixin}). Since Minecraft 26.x recipes
 * load as a registry before the manager is made of them: there is no JSON left to read on the way, so the board reads each recipe as
 * its own codec writes it.
 */
@Mixin(RecipeManager.class)
abstract class RecipeManagerMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void handson$keepAssembled(HolderLookup.Provider registries, CallbackInfo callback) {
        AssemblyRecipes.load((RecipeManager) (Object) this, registries);
    }
}
