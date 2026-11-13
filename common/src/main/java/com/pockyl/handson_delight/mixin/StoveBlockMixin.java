package com.pockyl.handson_delight.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.AbstractStoveBlock;
import vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.StoveCooking;

/**
 * A player whose client has no Hands-On Delight sees Farmer's Delight's stove, which gives nothing back by hand: the pieces a cook laid
 * at the station, waiting to be turned or taken, would stay on the grill for good. Their use of the stove hands those pieces to Farmer's
 * Delight's way, so they cook and pop off as everything such a player lays does. Not while a cook works at the station.
 */
@Mixin(AbstractStoveBlock.class)
abstract class StoveBlockMixin {
    @Inject(method = "use", at = @At("HEAD"))
    private void handson$handOverWithoutTheMod(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> callback) {
        if (player instanceof ServerPlayer server && !Platform.INSTANCE.hasThisMod(server)
                && level.getBlockEntity(pos) instanceof AbstractStoveBlockEntity stove
                && Diegesis.activeStation(server.serverLevel(), pos).map(station -> station.users().isEmpty()).orElse(true)) {
            StoveCooking.handToFarmersDelight(stove);
        }
    }
}
