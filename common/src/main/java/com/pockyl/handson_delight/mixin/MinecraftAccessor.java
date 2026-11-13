package com.pockyl.handson_delight.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The game's item colours (leather armour's dye), for icons the board builds itself; Forge has a getter for them, Fabric does not. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("itemColors")
    ItemColors handson$itemColors();
}
