package com.pockyl.handson_delight.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.assembly.AssemblyRecipes;

import java.util.HashMap;
import java.util.Map;

/**
 * Food put together on the cutting board: its recipes are found before the manager reads them and kept for the board as well. They
 * stay in the manager, for automation; a player's crafting grid does not make them ({@code CraftingMenuMixin}).
 */
@Mixin(RecipeManager.class)
abstract class RecipeManagerMixin {
    @Shadow
    @Final
    private HolderLookup.Provider registries;

    @Unique
    private static final String APPLY =
            "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V";

    @Unique
    private final Map<ResourceLocation, JsonObject> handson$taken = new HashMap<>();

    @Inject(method = APPLY, at = @At("HEAD"))
    private void handson$findAssembled(Map<ResourceLocation, JsonElement> recipes, ResourceManager resources, ProfilerFiller profiler,
            CallbackInfo callback) {
        handson$taken.clear();
        AssemblyRecipes.collect(recipes, handson$taken);
    }

    @Inject(method = APPLY, at = @At("TAIL"))
    private void handson$keepAssembled(Map<ResourceLocation, JsonElement> recipes, ResourceManager resources, ProfilerFiller profiler,
            CallbackInfo callback) {
        // Only those the manager loaded: a recipe whose conditions fail is not made anywhere.
        AssemblyRecipes.load(handson$taken, ((RecipeManagerAccessor) this).handson$byName()::containsKey, registries);
        handson$taken.clear();
    }
}
