package com.pockyl.handson_delight.forge.mixin;

import com.phantomwing.choppersdelight.block.entity.ModCuttingBoardBlockEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.station.CaughtDrops;
import com.pockyl.handson_delight.station.CuttingBoardStation;
import com.pockyl.handson_delight.station.KitchenBoard;

/**
 * Chopper's Delight's cutting boards of every wood as {@link KitchenBoard}s: copies of Farmer's Delight's board, one slot, a tool stuck
 * in, Farmer's Delight's cutting recipes, so the station works at them as at Farmer's Delight's. A dish being put together is kept
 * in the board as in Farmer's Delight's.
 */
@Pseudo
@Mixin(value = ModCuttingBoardBlockEntity.class, remap = false)
abstract class ChoppersBoardMixin implements KitchenBoard {
    @Unique
    private static final String LAYERS = HandsOnDelight.MOD_ID + ":layers";

    @Shadow
    @Final
    private ItemStackHandler inventory;
    @Shadow
    private boolean isItemCarvingBoard;
    @Unique
    private final NonNullList<ItemStack> handson$layers = NonNullList.withSize(CuttingBoardStation.MOST_LAYERS, ItemStack.EMPTY);

    @Unique
    private ModCuttingBoardBlockEntity handson$self() {
        return (ModCuttingBoardBlockEntity) (Object) this;
    }

    @Override
    public ItemStack handson$stored() {
        return inventory.getStackInSlot(0);
    }

    @Override
    public void handson$store(ItemStack stack) {
        inventory.setStackInSlot(0, stack);
    }

    @Override
    public boolean handson$isCarving() {
        return isItemCarvingBoard;
    }

    @Override
    public void handson$setCarving(boolean carving) {
        isItemCarvingBoard = carving;
    }

    @Override
    public int handson$maxStack() {
        return handson$self().getMaxStackSize();
    }

    @Override
    public boolean handson$carve(ItemStack tool) {
        return handson$self().carveToolOnBoard(tool);
    }

    @Override
    public boolean handson$cut(ItemStack tool, Player player) {
        return handson$self().processStoredItemUsingTool(tool, player);
    }

    @Override
    public BlockEntity handson$blockEntity() {
        return handson$self();
    }

    @Override
    public NonNullList<ItemStack> handson$layers() {
        return handson$layers;
    }

    @Override
    public void handson$layersChanged() {
        BlockEntity self = handson$self();
        self.setChanged();
        if (self.getLevel() != null) {
            self.getLevel().sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void handson$saveLayers(CompoundTag tag, CallbackInfo callback) {
        if (handson$layers.stream().anyMatch(stack -> !stack.isEmpty())) {
            tag.put(LAYERS, ContainerHelper.saveAllItems(new CompoundTag(), handson$layers));
        }
    }

    @Inject(method = "load", at = @At("TAIL"), remap = true)
    private void handson$loadLayers(CompoundTag tag, CallbackInfo callback) {
        handson$layers.clear();
        if (tag.contains(LAYERS)) {
            ContainerHelper.loadAllItems(tag.getCompound(LAYERS), handson$layers);
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"), remap = true)
    private void handson$clearLayers(CallbackInfo callback) {
        handson$layers.clear();
    }

    @Inject(method = "playProcessingSound", at = @At("HEAD"), cancellable = true)
    private void handson$oneSoundForABatch(String sound, ItemStack tool, ItemStack item, CallbackInfo callback) {
        if (CaughtDrops.isQuiet()) {
            callback.cancel();
        }
    }
}
