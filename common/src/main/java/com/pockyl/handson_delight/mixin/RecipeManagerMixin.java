package com.pockyl.handson_delight.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_delight.assembly.AssemblyRecipes;

import java.util.Optional;

/**
 * Food put together on the cutting board: its recipes are found as the manager is made and kept for the board as well. They stay in
 * the manager, for automation; the lookups of a player's crafting grid leave them out ({@link AssemblyRecipes#notInThisGrid}, the grid
 * marked by {@code CraftingMenuMixin} and {@code ResultSlotMixin}). Since Minecraft 26.x recipes load as a registry before the manager
 * is made of them: there is no JSON left to read on the way, so the board reads each recipe as its own codec writes it.
 */
@Mixin(RecipeManager.class)
abstract class RecipeManagerMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void handson$keepAssembled(HolderLookup.Provider registries, CallbackInfo callback) {
        AssemblyRecipes.load((RecipeManager) (Object) this, registries);
    }

    /**
     * A player's grid makes what else its stacks match, as if the dish were not there (a datapack's recipe of its ingredients). Every
     * lookup ends here but one whose recipe to try first matches, which {@code CraftingMenuMixin} looks past.
     */
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;"
            + "Lnet/minecraft/world/level/Level;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private void handson$notInThisGrid(RecipeType<?> type, RecipeInput input, Level level,
            CallbackInfoReturnable<Optional<RecipeHolder<?>>> callback) {
        RecipeHolder<?> found = callback.getReturnValue().orElse(null);
        if (found != null && AssemblyRecipes.notInThisGrid(found.id(), input)) {
            callback.setReturnValue(handson$firstInThisGrid(type, input, level));
        }
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<RecipeHolder<?>> handson$firstInThisGrid(RecipeType<?> type, RecipeInput input, Level level) {
        return (Optional) AssemblyRecipes.firstInThisGrid((RecipeManager) (Object) this, (RecipeType) type, input, level);
    }
}
