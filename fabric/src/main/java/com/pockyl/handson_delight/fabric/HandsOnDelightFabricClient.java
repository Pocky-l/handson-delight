package com.pockyl.handson_delight.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.assembly.BoardDishes;
import com.pockyl.handson_delight.client.HandsOnDelightClient;

/** Fabric entry point, client. */
public final class HandsOnDelightFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandsOnDelightClient.init();
        PotModels.register();
        ClientPlayNetworking.registerGlobalReceiver(BoardDishes.Payload.TYPE, (payload, context) -> BoardDishes.receive(payload));
        if (!HandsOnConfig.cuttingBoard()) {
            // Named to the server: this client cuts on Farmer's Delight's board and crafts the board's dishes in its grid.
            ClientPlayNetworking.registerGlobalReceiver(BoardDishes.NoBoard.TYPE, (payload, context) -> {
            });
        }
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HandsOnDelightClient.loggedOut());
    }
}
