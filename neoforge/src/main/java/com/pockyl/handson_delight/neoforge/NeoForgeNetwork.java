package com.pockyl.handson_delight.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import com.pockyl.handson_delight.assembly.BoardDishes;

/** The one packet of the mod: the board's dishes, for recipe viewers. Either side may lack the mod; the channel tells. */
final class NeoForgeNetwork {
    private static final String VERSION = "1";

    private NeoForgeNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION).optional()
                .playToClient(BoardDishes.Payload.TYPE, BoardDishes.Payload.CODEC, (payload, context) -> BoardDishes.receive(payload));
    }

    static boolean present(ServerPlayer player) {
        return player.connection.hasChannel(BoardDishes.Payload.TYPE);
    }

    static void send(ServerPlayer player) {
        if (player.connection != null && present(player)) {
            PacketDistributor.sendToPlayer(player, BoardDishes.payload());
        }
    }
}
