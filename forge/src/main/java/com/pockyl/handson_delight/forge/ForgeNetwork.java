package com.pockyl.handson_delight.forge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import com.pockyl.handson_delight.assembly.BoardDishes;

import java.util.function.Supplier;

/** The one packet of the mod: the board's dishes, for recipe viewers. Either side may lack the mod; the channel tells. */
final class ForgeNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(BoardDishes.CHANNEL, () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private ForgeNetwork() {
    }

    static void register() {
        CHANNEL.messageBuilder(Dishes.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((dishes, buffer) -> BoardDishes.write(buffer))
                .decoder(buffer -> new Dishes(BoardDishes.read(buffer)))
                .consumerNetworkThread(ForgeNetwork::handle)
                .add();
    }

    static boolean present(ServerPlayer player) {
        return CHANNEL.isRemotePresent(player.connection.connection);
    }

    static void send(ServerPlayer player) {
        if (present(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Dishes(null));
        }
    }

    private static void handle(Dishes dishes, Supplier<NetworkEvent.Context> context) {
        BoardDishes.receive(dishes.book());
        context.get().setPacketHandled(true);
    }

    /** Written from {@code AssemblyRecipes.book()}: the server sends no other list. */
    private record Dishes(CompoundTag book) {
    }
}
