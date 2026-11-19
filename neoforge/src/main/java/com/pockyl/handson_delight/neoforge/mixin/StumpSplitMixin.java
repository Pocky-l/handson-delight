package com.pockyl.handson_delight.neoforge.mixin;

import com.snissac.sabletrees.block.ChoppingBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.pockyl.handson_delight.station.CaughtDrops;

/**
 * The planks of a log split on Sable Trees' stump at the station go to the cook: while the station catches, its axe stroke throws
 * them nowhere. Its left click in the world drops them as before.
 */
@Pseudo
@Mixin(value = ChoppingBlock.class, remap = false)
abstract class StumpSplitMixin {
    @Redirect(method = "trySplit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean handson$toTheCook(ServerLevel level, Entity entity) {
        if (entity instanceof ItemEntity item && CaughtDrops.take(item.getItem(), item.getX(), item.getY(), item.getZ(),
                item.getDeltaMovement().x, item.getDeltaMovement().y, item.getDeltaMovement().z)) {
            return true;
        }
        return level.addFreshEntity(entity);
    }
}
