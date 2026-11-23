package com.pockyl.handson_delight.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A layer's tints, which Minecraft 1.21.11 keeps to itself: for the board's dyed items. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
    @Accessor("tintLayers")
    int[] handson$tintLayers();
}
