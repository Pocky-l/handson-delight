package com.pockyl.handson_delight.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.UUID;
import java.util.function.BiFunction;

/** Server players for the tests: with a connection that sends nowhere, as the crafting menu and the stats need one. */
public final class TestPlayers {
    /** The loader's fake player, set by the loader's tests. */
    private static BiFunction<ServerLevel, GameProfile, ServerPlayer> fakePlayers;

    private TestPlayers() {
    }

    public static void useFakePlayers(BiFunction<ServerLevel, GameProfile, ServerPlayer> make) {
        fakePlayers = make;
    }

    /**
     * A player counted as a client with the mod: the loader's fake player, whose connection sends nowhere. A player made by
     * {@link #serverPlayer} has a connection without a network channel, as another mod's mock player has, so no client with the mod.
     */
    static ServerPlayer withTheMod(GameTestHelper helper, String name) {
        return fakePlayers.apply(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    static ServerPlayer serverPlayer(GameTestHelper helper, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(profile, false));
        return player;
    }
}
