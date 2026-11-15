package com.pockyl.handson_delight;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import com.pockyl.handson_delight.station.BasinStation;
import com.pockyl.handson_delight.station.CookingPotStation;
import com.pockyl.handson_delight.station.CuttingBoardStation;
import com.pockyl.handson_delight.station.GrillStation;
import com.pockyl.handson_delight.station.KegStation;
import com.pockyl.handson_delight.station.OvenStation;
import com.pockyl.handson_delight.station.SkilletStation;
import com.pockyl.handson_delight.station.StoveStation;
import com.pockyl.handson_delight.station.VatStation;

/** Turns the menus of Farmer's Delight into stations of Diegesis. The mod registers nothing: blocks and items stay theirs. */
public final class HandsOnDelight {
    public static final String MOD_ID = "handson_delight";
    public static final Logger LOGGER = LogUtils.getLogger();

    private HandsOnDelight() {
    }

    /** Called by each loader's entry point on both sides. */
    public static void init() {
        HandsOnConfig.load();
        if (HandsOnConfig.cookingPot()) {
            CookingPotStation.register();
        }
        if (HandsOnConfig.cuttingBoard()) {
            CuttingBoardStation.register();
        }
        if (HandsOnConfig.skillet()) {
            SkilletStation.register();
        }
        if (HandsOnConfig.stove()) {
            StoveStation.register();
        }
        if (HandsOnConfig.keg()) {
            KegStation.register();
        }
        if (HandsOnConfig.oven()) {
            OvenStation.register();
        }
        if (HandsOnConfig.vat()) {
            VatStation.register();
        }
        if (HandsOnConfig.grill()) {
            GrillStation.register();
        }
        if (HandsOnConfig.basin()) {
            BasinStation.register();
        }
    }

    @SuppressWarnings("removal")
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
