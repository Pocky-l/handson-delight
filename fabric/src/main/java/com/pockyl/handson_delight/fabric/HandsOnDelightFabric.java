package com.pockyl.handson_delight.fabric;

import net.fabricmc.api.ModInitializer;

import com.pockyl.handson_delight.HandsOnDelight;

/** Fabric entry point, both sides. */
public final class HandsOnDelightFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        HandsOnDelight.init();
    }
}
