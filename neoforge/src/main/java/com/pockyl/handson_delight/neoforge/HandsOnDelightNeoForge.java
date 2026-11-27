package com.pockyl.handson_delight.neoforge;

import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.KitchenBowl;
import com.pockyl.handson_delight.station.KitchenYoukaiPot;
import com.pockyl.handson_delight.station.SteamerStation;

/** NeoForge entry point. */
@Mod(HandsOnDelight.MOD_ID)
public final class HandsOnDelightNeoForge {
    public HandsOnDelightNeoForge(IEventBus modBus, Dist dist) {
        if (Platform.INSTANCE.isLoaded(KitchenBowl.MOD_ID)) {
            KitchenBowl.use(new ExtraDelightBowls());
        }
        HandsOnDelight.init();
        modBus.addListener(NeoForgeNetwork::register);
        NeoForge.EVENT_BUS.addListener(HandsOnDelightNeoForge::rackOnShift);
        if (dist.isClient()) {
            NeoForgeClient.init();
            modBus.addListener(PotPack::add);
        }
    }

    /**
     * Shift with a steamer rack on a steamer tower: the block's use is skipped on Shift with an item in the hand, so this comes before
     * the rack item's own placing ({@link SteamerStation#stacksOnShift}).
     */
    private static void rackOnShift(PlayerInteractEvent.RightClickBlock event) {
        if (!Platform.INSTANCE.isLoaded(KitchenYoukaiPot.MOD_ID) || !HandsOnConfig.youkaiSteamer()) {
            return;
        }
        if (SteamerStation.stacksOnShift(event.getEntity(), event.getLevel(), event.getPos(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        }
    }
}
