package com.pockyl.handson_delight.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import vectorwing.farmersdelight.common.block.AbstractStoveBlock;
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
import com.pockyl.handson_delight.platform.Platform;
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
    /**
     * Farmer's Delight's own key for the cooking times of the six places: since its 26.x port the times each piece takes are saved as
     * {@code CookingTimes}, how far each is under {@code CookingTotalTimes}.
     */
    @Unique
    private static final String TOTAL_TIMES = "CookingTimes";

    /** The time each piece takes. Farmer's Delight's 26.x port counts in {@code cookingTimesTotal} up to this. */
    @Shadow(remap = false)
    @Final
    private int[] cookingTimes;

    /** How far each piece is. */
    @Shadow(remap = false)
    @Final
    private int[] cookingTimesTotal;

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
        return cookingTimesTotal;
    }

    @Override
    public int[] handson$times() {
        return cookingTimes;
    }

    @Override
    public ItemStack handson$item(int slot) {
        return Platform.INSTANCE.getItem(handson$self(), slot);
    }

    @Override
    public void handson$setItem(int slot, ItemStack stack) {
        Platform.INSTANCE.setItem(handson$self(), slot, stack);
    }

    @Override
    public int handson$slots() {
        return (int) handson$self().streamItems().count();
    }

    @Override
    public boolean handson$covered() {
        AbstractStoveBlockEntity stove = handson$self();
        return stove.getLevel() != null && AbstractStoveBlock.isStoveTopCovered(stove.getLevel(), stove.getBlockPos(), stove.getBlockState());
    }

    @Unique
    private AbstractStoveBlockEntity handson$self() {
        return (AbstractStoveBlockEntity) (Object) this;
    }

    @Inject(method = "serverTick", at = @At("HEAD"), remap = false)
    private static void handson$grillByHand(ServerLevel level, BlockPos pos, BlockState state, AbstractStoveBlockEntity stove,
            CallbackInfo callback) {
        StoveCooking.tick(stove);
    }

    /** Farmer's Delight cooks what is due on the grill: the pieces the station finished come back to it, cooked by its own code. */
    @Inject(method = "serverTick", at = @At(value = "INVOKE", target = COOK), remap = false)
    private static void handson$beforeCooking(ServerLevel level, BlockPos pos, BlockState state, AbstractStoveBlockEntity stove,
            CallbackInfo callback) {
        StoveCooking.beforeFarmersDelightCooks(stove);
    }

    @Inject(method = "serverTick", at = @At(value = "INVOKE", target = COOK, shift = At.Shift.AFTER), remap = false)
    private static void handson$afterCooking(ServerLevel level, BlockPos pos, BlockState state, AbstractStoveBlockEntity stove,
            CallbackInfo callback) {
        StoveCooking.afterFarmersDelightCooks(stove);
    }

    @Unique
    private static final String COOK = "Lvectorwing/farmersdelight/common/block/entity/AbstractStoveBlockEntity;cookAndOutputItems(Lnet/minecraft/server/level/ServerLevel;)V";

    /** Food put on with Farmer's Delight's own right click is its to cook. */
    @Inject(method = "placeFood", at = @At("HEAD"), remap = false)
    private void handson$farmersDelightsWay(Entity entity, ItemStack stack, RecipeHolder<? extends AbstractCookingRecipe> recipe,
            CallbackInfoReturnable<Boolean> callback) {
        int place = ((AbstractStoveBlockEntity) (Object) this).getNextEmptySlot();
        if (place >= 0 && place < Grill.PLACES) {
            handson$grill.farmersDelightsWay(place);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void handson$saveGrill(ValueOutput output, CallbackInfo callback) {
        CompoundTag grill = new CompoundTag();
        handson$grill.save(grill);
        if (!grill.isEmpty()) {
            output.store(GRILL, CompoundTag.CODEC, grill);
            output.putIntArray(TOTAL_TIMES, StoveCooking.savedTimes((AbstractStoveBlockEntity) (Object) this));
        }
    }

    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void handson$sendGrill(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> callback) {
        CompoundTag grill = new CompoundTag();
        handson$grill.save(grill);
        callback.getReturnValue().put(GRILL, grill);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void handson$loadGrill(ValueInput input, CallbackInfo callback) {
        handson$grill.load(input.read(GRILL, CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }
}
