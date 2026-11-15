package com.pockyl.handson_delight.neoforge;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.pockyl.handson_delight.client.HandsOnDelightClient;

/** Client setup on NeoForge. */
final class NeoForgeClient {
    private NeoForgeClient() {
    }

    static void init() {
        HandsOnDelightClient.init();
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> HandsOnDelightClient.loggedOut());
    }
}
