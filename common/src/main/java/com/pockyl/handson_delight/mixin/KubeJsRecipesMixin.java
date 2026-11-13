package com.pockyl.handson_delight.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.assembly.AssemblyRecipes;

import java.util.HashMap;
import java.util.Map;

/**
 * KubeJS for 1.20.1 loads the recipes itself as soon as a server script listens to its recipe event, and cancels the recipe manager's
 * own loading, so {@link RecipeManagerMixin} never sees its end: without this the board would make no dishes in such a modpack. The
 * board's recipes are found in the same JSON and read once KubeJS has put the recipes into the manager. Applied only with KubeJS
 * installed ({@code OptionalMixins}).
 */
@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.recipe.RecipesEventJS", remap = false)
abstract class KubeJsRecipesMixin {
    @Unique
    private final Map<ResourceLocation, JsonObject> handson$taken = new HashMap<>();

    @Inject(method = "post", at = @At("HEAD"), require = 0)
    private void handson$findAssembled(RecipeManager manager, Map<ResourceLocation, JsonElement> recipes, CallbackInfo callback) {
        handson$taken.clear();
        AssemblyRecipes.collect(recipes, handson$taken);
    }

    @Inject(method = "post", at = @At("TAIL"), require = 0)
    private void handson$keepAssembled(RecipeManager manager, Map<ResourceLocation, JsonElement> recipes, CallbackInfo callback) {
        AssemblyRecipes.load(handson$taken, ((RecipeManagerAccessor) manager).handson$byName());
        handson$taken.clear();
    }
}
