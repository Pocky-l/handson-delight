package com.pockyl.handson_delight.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whose inventory grid it is. */
@Mixin(InventoryMenu.class)
public interface InventoryMenuAccessor {
    @Accessor("owner")
    Player handson$owner();
}
