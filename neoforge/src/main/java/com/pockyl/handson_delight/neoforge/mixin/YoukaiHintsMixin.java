package com.pockyl.handson_delight.neoforge.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.HandsOnConfig;

/**
 * Youkai's Feasts' hints on the right of the screen while a block of it is looked at: squares of what it takes next
 * ({@code HintOverlay}, after three quarters of a second) and squares of what lies in it with its progress ({@code TileInfoDisplay}).
 * Off unless the config turns them on: they cover the world, and the stations show all that on the things themselves.
 */
@Pseudo
@Mixin(targets = {"dev.xkmc.youkaishomecoming.content.pot.overlay.HintOverlay",
        "dev.xkmc.youkaishomecoming.content.pot.overlay.TileInfoDisplay"}, remap = false)
abstract class YoukaiHintsMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void handson$hideHints(GuiGraphics graphics, DeltaTracker delta, CallbackInfo callback) {
        if (!HandsOnConfig.youkaiHints()) {
            callback.cancel();
        }
    }
}
