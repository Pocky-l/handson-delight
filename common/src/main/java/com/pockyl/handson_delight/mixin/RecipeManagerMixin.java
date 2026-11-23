package com.pockyl.handson_delight.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_delight.assembly.AssemblyRecipes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Food put together on the cutting board: its recipes are found before the manager reads them and kept for the board as well. They
 * stay in the manager, for automation; the lookups of a player's crafting grid leave them out ({@link AssemblyRecipes#notInThisGrid},
 * the grid marked by {@code CraftingMenuMixin} and {@code ResultSlotMixin}).
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

    /** A player's grid makes what else its stacks match, as if the dish were not there (a datapack's recipe of its ingredients). */
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;", at = @At("RETURN"),
            cancellable = true)
    private void handson$notInThisGrid(RecipeType<?> type, RecipeInput input, Level level, RecipeHolder<?> last,
            CallbackInfoReturnable<Optional<RecipeHolder<?>>> callback) {
        RecipeHolder<?> found = callback.getReturnValue().orElse(null);
        if (found != null && AssemblyRecipes.notInThisGrid(found.id(), input, level)) {
            callback.setReturnValue(handson$firstInThisGrid(type, input, level));
        }
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<RecipeHolder<?>> handson$firstInThisGrid(RecipeType<?> type, RecipeInput input, Level level) {
        return (Optional) AssemblyRecipes.firstInThisGrid((RecipeManager) (Object) this, (RecipeType) type, input, level);
    }

    /** Mods that list every match (Polymorph's choice among recipes) do not find a dish either; automation and other containers do. */
    @Inject(method = "getRecipesFor", at = @At("RETURN"), cancellable = true)
    private void handson$noneInThisGrid(RecipeType<?> type, RecipeInput input, Level level,
            CallbackInfoReturnable<List<RecipeHolder<?>>> callback) {
        List<RecipeHolder<?>> found = callback.getReturnValue();
        if (found.stream().anyMatch(recipe -> AssemblyRecipes.notInThisGrid(recipe.id(), input, level))) {
            callback.setReturnValue(found.stream().filter(recipe -> !AssemblyRecipes.notInThisGrid(recipe.id(), input, level))
                    .collect(Collectors.toList()));
        }
    }

    /**
     * What a player's grid leaves comes from the recipe that made what was taken: the one the grid makes, which may be another recipe
     * of a dish's ingredients; failing that the dish itself, as another mod's crafting table may make it by a lookup of its own.
     */
    @Inject(method = "getRemainingItemsFor", at = @At("HEAD"), cancellable = true)
    private void handson$leftByTheRecipeItself(RecipeType<?> type, RecipeInput input, Level level,
            CallbackInfoReturnable<NonNullList<ItemStack>> callback) {
        if (AssemblyRecipes.mayLeaveDishes(input, level)) {
            handson$recipeFor(type, input, level)
                    .or(() -> AssemblyRecipes.seeingEveryRecipe(() -> handson$recipeFor(type, input, level)))
                    .ifPresent(recipe -> callback.setReturnValue(handson$remainders(recipe, input)));
        }
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<RecipeHolder<?>> handson$recipeFor(RecipeType<?> type, RecipeInput input, Level level) {
        return (Optional) ((RecipeManager) (Object) this).getRecipeFor((RecipeType) type, input, level);
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static NonNullList<ItemStack> handson$remainders(RecipeHolder<?> recipe, RecipeInput input) {
        return ((Recipe) recipe.value()).getRemainingItems(input);
    }

    @Inject(method = APPLY, at = @At("TAIL"))
    private void handson$keepAssembled(Map<ResourceLocation, JsonElement> recipes, ResourceManager resources, ProfilerFiller profiler,
            CallbackInfo callback) {
        // Only those the manager loaded: a recipe whose conditions fail is not made anywhere.
        AssemblyRecipes.load(handson$taken, (RecipeManager) (Object) this, registries, resources);
        handson$taken.clear();
    }
}
