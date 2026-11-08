package com.pockyl.handson_delight.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whose crafting table it is. */
@Mixin(CraftingMenu.class)
public interface CraftingMenuAccessor {
    @Accessor("player")
    Player handson$player();
}
