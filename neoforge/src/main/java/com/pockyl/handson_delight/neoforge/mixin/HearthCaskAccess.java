package com.pockyl.handson_delight.neoforge.mixin;

import alabaster.hearthandharvest.common.block.entity.CaskBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * How far Hearth and Harvest's cask has aged the thing in it and how far it must: in its own units, which grow by 1, 2 or 4 a tick by the
 * sky light at it.
 */
@Pseudo
@Mixin(value = CaskBlockEntity.class, remap = false)
public interface HearthCaskAccess {
    @Accessor("ageTime")
    int handson$ageTime();

    @Accessor("ageTimeTotal")
    int handson$ageTimeTotal();

    @Accessor("sealed")
    void handson$setSealed(boolean sealed);
}
