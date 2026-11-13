package com.pockyl.handson_delight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;

/**
 * Whether a tool is carved into the board: Farmer's Delight clears it only when its own use takes the item off. The station puts
 * things on the board and takes them off by itself, so it clears it too, or the next food would stand on the board as a carved knife
 * and could never be cut.
 */
@Mixin(value = CuttingBoardBlockEntity.class, remap = false)
public interface CuttingBoardBlockEntityAccessor {
    @Accessor("isItemCarvingBoard")
    void handson$setCarving(boolean carving);
}
