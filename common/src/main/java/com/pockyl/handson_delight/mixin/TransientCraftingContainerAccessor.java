package com.pockyl.handson_delight.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The menu a crafting grid belongs to: a player's crafting table or inventory, or a machine's own. */
@Mixin(TransientCraftingContainer.class)
public interface TransientCraftingContainerAccessor {
    @Accessor("menu")
    AbstractContainerMenu handson$menu();
}
