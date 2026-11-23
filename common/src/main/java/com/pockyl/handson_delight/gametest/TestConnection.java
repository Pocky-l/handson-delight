package com.pockyl.handson_delight.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.UUID;
import java.util.function.Function;

/**
 * A packet as a dedicated server writes it to a player in the world and the player's client reads it. It goes through the encoder of
 * a server connection in play, with a player's listener on it, where the loader and the mods that change what a player is sent
 * (Polymer) know the connection and the player; a codec called anywhere else is not what the network does.
 */
final class TestConnection {
    private TestConnection() {
    }

    /** {@code payload} written by a player's server connection and read back as a client reads it. */
    @SuppressWarnings("unchecked")
    static <T extends CustomPacketPayload> T sent(ServerLevel level, T payload) {
        Function<ByteBuf, RegistryFriendlyByteBuf> registries = RegistryFriendlyByteBuf.decorator(level.registryAccess());
        ProtocolInfo<ClientGamePacketListener> play = GameProtocols.CLIENTBOUND_TEMPLATE.bind(registries);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                Connection.configureSerialization(channel.pipeline(), PacketFlow.SERVERBOUND, false, null);
                connection.configurePacketHandler(channel.pipeline());
            }
        });
        try {
            connection.setupOutboundProtocol(play);
            GameProfile profile = new GameProfile(UUID.randomUUID(), "recipe_reader");
            ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
            ServerGamePacketListenerImpl listener = new ServerGamePacketListenerImpl(level.getServer(), connection, player,
                    CommonListenerCookie.createInitial(profile, false));
            connection.setupInboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(registries, listener), listener);
            connection.send(new ClientboundCustomPayloadPacket(payload));
            ByteBuf frame = written(channel);
            try {
                int length = VarInt.read(frame);
                if (length != frame.readableBytes()) {
                    throw new IllegalStateException("the frame of " + payload.type().id() + " is cut");
                }
                Packet<? super ClientGamePacketListener> packet = play.codec().decode(frame);
                return (T) ((ClientboundCustomPayloadPacket) packet).payload();
            } finally {
                frame.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    /** The bytes the connection wrote; what else went out to the channel (the handlers' own messages) is passed over. */
    private static ByteBuf written(EmbeddedChannel channel) {
        for (Object message = channel.readOutbound(); message != null; message = channel.readOutbound()) {
            if (message instanceof ByteBuf bytes) {
                return bytes;
            }
        }
        throw new IllegalStateException("the connection wrote nothing");
    }
}
