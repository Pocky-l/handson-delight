package com.pockyl.handson_delight.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import com.pockyl.handson_delight.HandsOnDelight;

/** NeoForge entry point. */
@Mod(HandsOnDelight.MOD_ID)
public final class HandsOnDelightNeoForge {
    public HandsOnDelightNeoForge(IEventBus modBus, Dist dist) {
        HandsOnDelight.init();
        modBus.addListener(NeoForgeNetwork::register);
        if (dist.isClient()) {
            NeoForgeClient.init();
        }
    }
}
