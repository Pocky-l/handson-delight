package com.pockyl.handson_delight.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

import com.pockyl.handson_delight.HandsOnDelight;

/** Forge entry point. */
@Mod(HandsOnDelight.MOD_ID)
public final class HandsOnDelightForge {
    public HandsOnDelightForge() {
        HandsOnDelight.init();
        ForgeNetwork.register();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ForgeClient::init);
    }
}
