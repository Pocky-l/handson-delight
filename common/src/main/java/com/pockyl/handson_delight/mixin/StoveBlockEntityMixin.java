package com.pockyl.handson_delight.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity;

import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.station.Grill;
import com.pockyl.handson_delight.station.StoveCooking;
import com.pockyl.handson_delight.station.StoveTop;

/**
 * A stove grilling by hand: the doneness of each piece is kept in Farmer's Delight's block entity, saved with it and sent to the
 * clients in its update tag. Every tick, before Farmer's Delight cooks, the pieces the cook laid grill by hand and Farmer's Delight's
 * count for them stays at nothing; the others cook its way.
 */
@Mixin(AbstractStoveBlockEntity.class)
abstract class StoveBlockEntityMixin implements StoveTop {
    @Unique
    private static final String GRILL = HandsOnDelight.MOD_ID + ":grill";
    /** Farmer's Delight's own key for the cooking times of the six places. */
    @Unique
    private static final String TOTAL_TIMES = "CookingTotalTimes";

    @Shadow(remap = false)
    @Final
    private int[] cookingProgress;

    @Shadow(remap = false)
    @Final
    private int[] cookingTime;

    @Unique
    private final Grill handson$grill = new Grill();

    @Unique
    private final boolean[] handson$cookingNow = new boolean[Grill.PLACES];

    @Override
    public boolean[] handson$cookingNow() {
        return handson$cookingNow;
    }

    @Override
    public Grill handson$grill() {
        return handson$grill;
    }

    @Override
    public int[] handson$progress() {
        return cookingProgress;
    }

    @Override
    public int[] handson$times() {
        return cookingTime;
    }

    @Inject(method = "serverTick", at = @At("HEAD"), remap = false)
    private static void handson$grillByHand(Level level, BlockPos pos, BlockState state, AbstractStoveBlockEntity stove,
            CallbackInfo callback) {
        StoveCooking.tick(stove);
    }

    /** Farmer's Delight cooks what is due on the grill: the pieces the station finished come back to it, cooked by its own code. */
    @Inject(method = "serverTick", at = @At(value = "INVOKE", target = COOK), remap = false)
    private static void handson$beforeCooking(Level level, BlockPos pos, BlockState state, AbstractStoveBlockEntity stove,
            CallbackInfo callback) {
        StoveCooking.beforeFarmersDelightCooks(stove);
    }

    @Inject(method = "serverTick", at = @At(value = "INVOKE", target = COOK, shift = At.Shift.AFTER), remap = false)
    private static void handson$afterCooking(Level level, BlockPos pos, BlockState state, AbstractStoveBlockEntity stove,
            CallbackInfo callback) {
        StoveCooking.afterFarmersDelightCooks(stove);
    }

    @Unique
    private static final String COOK = "Lvectorwing/farmersdelight/common/block/entity/AbstractStoveBlockEntity;cookAndOutputItems()V";

    /** Food put on with Farmer's Delight's own right click is its to cook. */
    @Inject(method = "placeFood", at = @At("HEAD"), remap = false)
    private void handson$farmersDelightsWay(Entity entity, ItemStack stack, int ticks, CallbackInfoReturnable<Boolean> callback) {
        int place = ((AbstractStoveBlockEntity) (Object) this).getNextEmptySlot();
        if (place >= 0 && place < Grill.PLACES) {
            handson$grill.farmersDelightsWay(place);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void handson$saveGrill(CompoundTag tag, CallbackInfo callback) {
        CompoundTag grill = new CompoundTag();
        handson$grill.save(grill);
        if (!grill.isEmpty()) {
            tag.put(GRILL, grill);
            tag.putIntArray(TOTAL_TIMES, StoveCooking.savedTimes((AbstractStoveBlockEntity) (Object) this));
        }
    }

    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void handson$sendGrill(CallbackInfoReturnable<CompoundTag> callback) {
        CompoundTag grill = new CompoundTag();
        handson$grill.save(grill);
        callback.getReturnValue().put(GRILL, grill);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void handson$loadGrill(CompoundTag tag, CallbackInfo callback) {
        handson$grill.load(tag.getCompound(GRILL));
    }
}
