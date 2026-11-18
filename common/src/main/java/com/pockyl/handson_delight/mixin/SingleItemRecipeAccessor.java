package com.pockyl.handson_delight.mixin;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The recipe's result, which it no longer hands out since Minecraft 1.21.2: for {@code RecipeParts}. */
@Mixin(SingleItemRecipe.class)
public interface SingleItemRecipeAccessor {
    @Accessor("result")
    ItemStackTemplate handson$result();
}
