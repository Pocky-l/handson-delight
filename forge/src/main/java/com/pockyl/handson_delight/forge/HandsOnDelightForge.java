package com.pockyl.handson_delight.forge;

import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.station.SteamerStation;
import com.pockyl.handson_delight.station.Youkai;

/** Forge entry point. */
@Mod(HandsOnDelight.MOD_ID)
public final class HandsOnDelightForge {
    public HandsOnDelightForge() {
        HandsOnDelight.init();
        ForgeNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(HandsOnDelightForge::rackOnShift);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ForgeClient::init);
    }

    /**
     * Shift with a steamer rack on a steamer tower: the block's use is skipped on Shift with an item in the hand, so this comes before
     * the rack item's own placing ({@link SteamerStation#stacksOnShift}).
     */
    private static void rackOnShift(PlayerInteractEvent.RightClickBlock event) {
        if (!Youkai.isLoaded() || !HandsOnConfig.youkaiSteamer()) {
            return;
        }
        if (SteamerStation.stacksOnShift(event.getEntity(), event.getLevel(), event.getPos(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        }
    }
}
