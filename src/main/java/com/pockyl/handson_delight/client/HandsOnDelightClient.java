package com.pockyl.handson_delight.client;

import net.minecraft.core.BlockPos;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.LiquidLook;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.station.CookingPotStation;

/** Client setup; only loaded on clients. */
public final class HandsOnDelightClient {
    private HandsOnDelightClient() {
    }

    public static void init() {
        DiegesisClient.registerBehavior(CookingPotStation.ID, PotBehavior::new);
        DiegesisClient.registerLiquid(HandsOnDelight.id("broth"), LiquidLook.of(0xE0B0A070).thick(0.6F));
    }

    /** Whether a scene stands in for the block at {@code pos} on this client. */
    public static boolean isShown(BlockPos pos) {
        return DiegesisClient.station(pos).isPresent();
    }
}
