package com.pockyl.handson_delight.neoforge.mixin;

import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.neoforged.neoforge.network.payload.MinecraftRegisterPayload;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.assembly.BoardDishes;

import java.util.Set;

/**
 * A client whose cutting board is off names {@link BoardDishes#NO_BOARD} to the server it joins, as soon as the two have agreed on
 * their channels. That agreement keeps only the channels both sides made: the server of a LAN game whose host has the board on
 * never made this one, so it took every guest for one with the board on, and a guest with it off lost the board's dishes from the
 * crafting grid. Named this way, the channel is the server's to see whatever it made; before the recipe book goes out.
 */
@Mixin(value = NetworkRegistry.class, remap = false)
abstract class NoBoardRegisterMixin {
    @Inject(method = "initializeNeoForgeConnection(Lnet/minecraft/network/protocol/configuration/ClientConfigurationPacketListener;"
            + "Lnet/neoforged/neoforge/network/registration/NetworkPayloadSetup;)V", at = @At("TAIL"))
    private static void handson$nameNoBoard(ClientConfigurationPacketListener listener, NetworkPayloadSetup setup, CallbackInfo callback) {
        if (!HandsOnConfig.cuttingBoard()) {
            listener.send(new MinecraftRegisterPayload(Set.of(BoardDishes.NO_BOARD)));
        }
    }
}
