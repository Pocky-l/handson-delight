package com.pockyl.handson_delight.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** The recipe manager's map of every recipe by id: a new map each time the recipes are loaded or a server sends them. */
@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    @Accessor("byName")
    Map<ResourceLocation, RecipeHolder<?>> handson$byName();
}
