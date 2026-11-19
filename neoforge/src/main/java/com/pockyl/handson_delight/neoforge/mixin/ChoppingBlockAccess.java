package com.pockyl.handson_delight.neoforge.mixin;

import com.snissac.sabletrees.block.ChoppingBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sable Trees' cut of Farmer's Delight's recipes on its stump: set when Farmer's Delight is there, null without. */
@Pseudo
@Mixin(value = ChoppingBlock.class, remap = false)
public interface ChoppingBlockAccess {
    @Accessor("cuttingBoard")
    static ChoppingBlock.CuttingBoard handson$cuttingBoard() {
        throw new AssertionError();
    }
}
