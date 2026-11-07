package com.pockyl.handson_delight;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

import com.pockyl.handson_delight.client.HandsOnDelightClient;
import com.pockyl.handson_delight.station.CookingPotStation;

/** Turns the menus of Farmer's Delight into stations of Diegesis. The mod registers nothing: blocks and items stay theirs. */
@Mod(HandsOnDelight.MOD_ID)
public final class HandsOnDelight {
    public static final String MOD_ID = "handson_delight";

    public HandsOnDelight() {
        CookingPotStation.register();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> HandsOnDelightClient::init);
    }

    @SuppressWarnings("removal")
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
