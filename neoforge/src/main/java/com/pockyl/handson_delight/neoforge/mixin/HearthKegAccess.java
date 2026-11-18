package com.pockyl.handson_delight.neoforge.mixin;

import alabaster.hearthandharvest.common.block.entity.KegBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How far Hearth and Harvest's keg has fermented, in ticks, and how many its recipe takes: its menu shows them scaled to a thousand. */
@Pseudo
@Mixin(value = KegBlockEntity.class, remap = false)
public interface HearthKegAccess {
    @Accessor("fermentTime")
    int handson$fermentTime();

    @Accessor("fermentTime")
    void handson$setFermentTime(int ticks);

    @Accessor("fermentTimeTotal")
    int handson$fermentTimeTotal();
}
