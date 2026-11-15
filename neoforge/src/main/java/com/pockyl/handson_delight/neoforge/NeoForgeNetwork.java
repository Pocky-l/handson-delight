package com.pockyl.handson_delight.neoforge;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.assembly.BoardDishes;

/** The one packet of the mod: the board's dishes, for recipe viewers. Either side may lack the mod; the channel tells. */
final class NeoForgeNetwork {
    private static final String VERSION = "1";

    private NeoForgeNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION).optional();
        registrar.playToClient(BoardDishes.Payload.TYPE, BoardDishes.Payload.CODEC, (payload, context) -> BoardDishes.receive(payload));
        // BoardDishes.NO_BOARD: a dedicated server always has it, to see which clients name it; a game client has it only with the
        // board's station off. A game whose board is on has none: its own server and the players of a LAN game count as having it.
        if (FMLEnvironment.dist.isDedicatedServer() || !HandsOnConfig.cuttingBoard()) {
            registrar.playToClient(BoardDishes.NoBoard.TYPE, BoardDishes.NoBoard.CODEC, (payload, context) -> {
            });
        }
    }

    /** Whether the client has the channel; false for a connection without a network channel (another mod's mock player). */
    static boolean present(ServerPlayer player) {
        Connection connection = player.connection.getConnection();
        return connection != null && connection.channel() != null && player.connection.hasChannel(BoardDishes.Payload.TYPE);
    }

    /** Whether the client has the mod and does not name {@link BoardDishes#NO_BOARD}. */
    static boolean hasBoard(ServerPlayer player) {
        return present(player) && !player.connection.hasChannel(BoardDishes.NoBoard.TYPE);
    }

    static void send(ServerPlayer player) {
        if (player.connection != null && present(player)) {
            PacketDistributor.sendToPlayer(player, BoardDishes.payload());
        }
    }
}
