package com.pockyl.handson_delight.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.KitchenBowl;

/** NeoForge entry point. */
@Mod(HandsOnDelight.MOD_ID)
public final class HandsOnDelightNeoForge {
    public HandsOnDelightNeoForge(IEventBus modBus, Dist dist) {
        if (Platform.INSTANCE.isLoaded(KitchenBowl.MOD_ID)) {
            KitchenBowl.use(new ExtraDelightBowls());
        }
        HandsOnDelight.init();
        modBus.addListener(NeoForgeNetwork::register);
        if (dist.isClient()) {
            NeoForgeClient.init();
            modBus.addListener(PotPack::add);
        }
    }
}
