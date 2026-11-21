package com.pockyl.handson_delight;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import com.pockyl.handson_delight.station.BasinStation;
import com.pockyl.handson_delight.station.BowlStation;
import com.pockyl.handson_delight.station.ChoppingBlockStation;
import com.pockyl.handson_delight.station.CookingPotStation;
import com.pockyl.handson_delight.station.CuttingBoardStation;
import com.pockyl.handson_delight.station.EdVatStation;
import com.pockyl.handson_delight.station.CuisineBoardStation;
import com.pockyl.handson_delight.station.GrillStation;
import com.pockyl.handson_delight.station.KegStation;
import com.pockyl.handson_delight.station.OvenStation;
import com.pockyl.handson_delight.station.KettleStation;
import com.pockyl.handson_delight.station.ChillerStation;
import com.pockyl.handson_delight.station.MeltingPotStation;
import com.pockyl.handson_delight.station.SkilletStation;
import com.pockyl.handson_delight.station.StoveStation;
import com.pockyl.handson_delight.station.SteamerStation;
import com.pockyl.handson_delight.station.VatStation;
import com.pockyl.handson_delight.station.YoukaiPotStation;

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
        if (HandsOnConfig.youkaiTank()) {
            KegStation.registerTank();
        }
        if (HandsOnConfig.oven()) {
            OvenStation.register();
        }
        if (HandsOnConfig.vat()) {
            VatStation.register();
        }
        if (HandsOnConfig.meltingPot()) {
            MeltingPotStation.register();
        }
        if (HandsOnConfig.chiller()) {
            ChillerStation.register();
        }
        if (HandsOnConfig.cask()) {
            KegStation.registerCask();
        }
        if (HandsOnConfig.grill()) {
            GrillStation.register();
        }
        if (HandsOnConfig.basin()) {
            BasinStation.register();
        }
        if (HandsOnConfig.kettle()) {
            KettleStation.register();
        }
        if (HandsOnConfig.cuisineBoard()) {
            CuisineBoardStation.register();
        }
        if (HandsOnConfig.choppingBlock()) {
            ChoppingBlockStation.register();
        }
        if (HandsOnConfig.youkaiPots()) {
            YoukaiPotStation.register();
        }
        if (HandsOnConfig.youkaiSteamer()) {
            SteamerStation.register();
        }
        if (HandsOnConfig.mixingBowl()) {
            BowlStation.register();
        }
        if (HandsOnConfig.edVat()) {
            EdVatStation.register();
        }
    }

    @SuppressWarnings("removal")
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
