package com.pockyl.handson_delight.station;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The client's copy of a block entity, which decides on that client whether a click opens the station or is the block's own use (a
 * cup that fills, a bucket that pours). The block's own use is also guessed on the client: Farmer's Respite's kettle fills or drains
 * its client copy at once. When the server does not do the same (another hand item there, the use stopped by another mod), nothing
 * changed on the server, so nothing corrects the copy: a kettle the server knows empty keeps 250 mB on the client, every bucket opens
 * the station, and the station, asking the same copy, pours nothing in (GH #31). Opening a station sends the block entity again, and
 * so does every use at the station, used or refused.
 */
public final class ClientCopy {
    private ClientCopy() {
    }

    /** Sends {@code entity} as the server has it to {@code player}'s client; nothing on a client. */
    public static void resend(BlockEntity entity, Player player) {
        if (!(player instanceof ServerPlayer server) || entity == null || entity.getLevel() == null || entity.getLevel().isClientSide()) {
            return;
        }
        Packet<ClientGamePacketListener> packet = entity.getUpdatePacket();
        if (packet != null) {
            server.connection.send(packet);
        }
    }

    /** Sends {@code entity} as the server has it to every client that sees it; nothing on a client. */
    public static void broadcast(BlockEntity entity) {
        if (entity == null || entity.getLevel() == null || entity.getLevel().isClientSide()) {
            return;
        }
        entity.getLevel().sendBlockUpdated(entity.getBlockPos(), entity.getBlockState(), entity.getBlockState(), Block.UPDATE_CLIENTS);
    }
}
