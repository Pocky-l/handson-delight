package com.pockyl.handson_delight.neoforge;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.pockyl.handson_delight.assembly.BoardDishes;
import com.pockyl.handson_delight.client.HandsOnDelightClient;

/** Client setup on NeoForge. */
final class NeoForgeClient {
    private NeoForgeClient() {
    }

    static void init() {
        HandsOnDelightClient.init();
        // The next server may not have the mod: its sandwiches are crafted.
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> BoardDishes.clear());
    }
}
