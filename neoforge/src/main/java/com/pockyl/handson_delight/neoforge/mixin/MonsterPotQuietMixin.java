package com.pockyl.handson_delight.neoforge.mixin;

import net.yirmiri.dungeonsdelight.common.block.monster_pot.MonsterPotBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.client.HandsOnDelightClient;
import com.pockyl.handson_delight.station.KitchenPot;
import com.pockyl.handson_delight.station.PotLogic;

/** The monster pot's own boiling sounds come at random: a scene showing it with food has its own, an empty pot keeps them. */
@Pseudo
@Mixin(MonsterPotBlock.class)
abstract class MonsterPotQuietMixin {
    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void handson$quietWhileShown(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo callback) {
        if (HandsOnDelightClient.isShown(pos) && level.getBlockEntity(pos) instanceof KitchenPot pot && PotLogic.hasFood(pot)) {
            callback.cancel();
        }
    }
}
