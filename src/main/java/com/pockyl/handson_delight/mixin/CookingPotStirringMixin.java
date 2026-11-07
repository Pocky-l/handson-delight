package com.pockyl.handson_delight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.handson_delight.station.PotLogic;
import com.pockyl.handson_delight.station.PotStirring;

/**
 * Cooking moves on only while the pot is stirred; left alone it keeps how far it got. A pot with many servings in it cooks faster,
 * so that nobody stirs for an hour. Players at Farmer's Delight's own menu, on a client without this mod, cook as before: they have no spoon.
 */
@Mixin(value = CookingPotBlockEntity.class, remap = false)
abstract class CookingPotStirringMixin implements PotStirring {
    @Shadow
    private int cookTime;
    @Unique
    private long handson$stirredUntil;
    @Unique
    private float handson$speed = 1.0F;
    /** The part of a tick of cooking the speed left over. */
    @Unique
    private float handson$saved;

    @Override
    public void handson$stir(long until) {
        handson$stirredUntil = until;
    }

    @Override
    public boolean handson$isStirred(long gameTime) {
        return gameTime < handson$stirredUntil;
    }

    @Override
    public void handson$setSpeed(float speed) {
        handson$speed = speed;
    }

    @Inject(method = "processCooking", at = @At("HEAD"), cancellable = true)
    private void handson$waitForTheSpoon(CookingPotRecipe recipe, CookingPotBlockEntity pot, CallbackInfoReturnable<Boolean> callback) {
        if (pot.getLevel() == null) {
            return;
        }
        if (handson$isStirred(pot.getLevel().getGameTime())) {
            // The pot adds one itself.
            handson$saved += handson$speed - 1.0F;
            int whole = (int) handson$saved;
            handson$saved -= whole;
            cookTime += whole;
        } else if (!PotLogic.isInMenu(pot)) {
            callback.setReturnValue(false);
        }
    }
}
