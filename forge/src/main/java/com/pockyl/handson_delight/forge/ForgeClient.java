package com.pockyl.handson_delight.forge;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;

import com.pockyl.handson_delight.assembly.BoardDishes;
import com.pockyl.handson_delight.client.HandsOnDelightClient;

/** Client setup on Forge. */
final class ForgeClient {
    private ForgeClient() {
    }

    static void init() {
        HandsOnDelightClient.init();
        // The next server may not have the mod: its sandwiches are crafted.
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> BoardDishes.clear());
    }
}
