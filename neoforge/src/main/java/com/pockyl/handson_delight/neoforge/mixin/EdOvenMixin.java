package com.pockyl.handson_delight.neoforge.mixin;

import com.lance5057.extradelight.workstations.oven.OvenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.SyncedBlockEntity;

import com.pockyl.handson_delight.station.KitchenEdOven;

import java.util.List;

/**
 * Extra Delight's oven as a {@link KitchenEdOven}. It sends the clients near it what lies in it whenever that changes, but not how far it
 * has baked: that goes along in the same data, and while it bakes it is sent every half second, so what lies in it browns for everybody.
 */
@Pseudo
@Mixin(value = OvenBlockEntity.class, remap = false)
abstract class EdOvenMixin extends SyncedBlockEntity implements KitchenEdOven {
    @Unique
    private static final int SYNC_TICKS = 10;

    @Shadow
    @Final
    private ItemStackHandler inventory;
    @Shadow
    private int cookTime;
    @Shadow
    private int cookTimeTotal;

    private EdOvenMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Shadow
    public abstract boolean isHeated();

    @Shadow
    public abstract void awardUsedRecipes(Player player, List<ItemStack> items);

    @Override
    public ItemStack handson$item(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public void handson$setItem(int slot, ItemStack stack) {
        inventory.setStackInSlot(slot, stack);
    }

    @Override
    public int handson$cookTime() {
        return cookTime;
    }

    @Override
    public void handson$setCookTime(int ticks) {
        cookTime = ticks;
    }

    @Override
    public int handson$cookTimeTotal() {
        return cookTimeTotal;
    }

    @Override
    public boolean handson$isHeated() {
        return isHeated();
    }

    @Override
    public void handson$award(Player player) {
        awardUsedRecipes(player, List.of());
    }

    @Override
    public void handson$sync() {
        inventoryChanged();
    }

    /** How far it has baked goes to the clients with what lies in it; its own load reads both. */
    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void handson$withBaking(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> info) {
        info.getReturnValue().putInt("CookTime", cookTime);
        info.getReturnValue().putInt("CookTimeTotal", cookTimeTotal);
    }

    @Inject(method = "cookingTick", at = @At("TAIL"))
    private static void handson$sendBaking(Level level, BlockPos pos, BlockState state, OvenBlockEntity oven, CallbackInfo info) {
        if ((Object) oven instanceof KitchenEdOven kitchen && kitchen.handson$cookTime() > 0 && level.getGameTime() % SYNC_TICKS == 0) {
            kitchen.handson$sync();
        }
    }
}
