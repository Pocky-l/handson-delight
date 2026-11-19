package com.pockyl.handson_delight.neoforge.mixin;

import com.snissac.sabletrees.compat.FarmersDelightCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.station.CaughtDrops;

/**
 * Sable Trees' cut of Farmer's Delight's recipes on its stump, at the station: the pieces go to the cook instead of the floor, and the
 * rest of a batch after its first item makes no sound and no burst of its own, as at the board. Its own use in the world is as before.
 */
@Pseudo
@Mixin(value = FarmersDelightCompat.class, remap = false)
abstract class StumpCutMixin {
    @Redirect(method = "cut", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean handson$toTheCook(Level level, Entity entity) {
        if (entity instanceof ItemEntity item && CaughtDrops.take(item.getItem(), item.getX(), item.getY(), item.getZ(),
                item.getDeltaMovement().x, item.getDeltaMovement().y, item.getDeltaMovement().z)) {
            return true;
        }
        return level.addFreshEntity(entity);
    }

    @Redirect(method = "cut", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"))
    private <T extends ParticleOptions> int handson$oneBurstForABatch(ServerLevel level, T particle, double x, double y, double z,
            int count, double dx, double dy, double dz, double speed) {
        return CaughtDrops.isQuiet() ? 0 : level.sendParticles(particle, x, y, z, count, dx, dy, dz, speed);
    }

    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private static void handson$oneSoundForABatch(Level level, BlockPos pos, SoundEvent sound, ItemStack tool, CallbackInfo callback) {
        if (CaughtDrops.isQuiet()) {
            callback.cancel();
        }
    }
}
