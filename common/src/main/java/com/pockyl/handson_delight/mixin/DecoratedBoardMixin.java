package com.pockyl.handson_delight.mixin;

import com.phantomwing.choppersdelight.block.entity.DecoratedCuttingBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
 * Chopper's Delight's decorated board (a board with a banner pattern) as a {@link KitchenBoard}: a copy of Farmer's Delight's board,
 * one slot, a tool stuck in, Farmer's Delight's cutting recipes, so the station works at it as at Farmer's Delight's. A dish being put
 * together is kept in the board. Its slot is a NeoForge handler on NeoForge and Refabricated's on Fabric, so what lies there is set
 * through the board's own take and put, the same on both.
 */
@Pseudo
@Mixin(value = DecoratedCuttingBoardBlockEntity.class, remap = false)
abstract class DecoratedBoardMixin implements KitchenBoard {
    @Unique
    private static final String LAYERS = HandsOnDelight.MOD_ID + ":layers";

    @Shadow(remap = false)
    private boolean isItemCarvingBoard;
    @Unique
    private final NonNullList<ItemStack> handson$layers = NonNullList.withSize(CuttingBoardStation.MOST_LAYERS, ItemStack.EMPTY);

    @Unique
    private DecoratedCuttingBoardBlockEntity handson$self() {
        return (DecoratedCuttingBoardBlockEntity) (Object) this;
    }

    @Override
    public ItemStack handson$stored() {
        return handson$self().getStoredItem();
    }

    @Override
    public void handson$store(ItemStack stack) {
        // The board's take and put clear its tool mark, which setting the slot leaves alone; a stuck tool refuses the put.
        boolean carving = isItemCarvingBoard;
        handson$self().removeItem();
        isItemCarvingBoard = false;
        if (!stack.isEmpty()) {
            handson$self().addItem(stack.copy());
        }
        isItemCarvingBoard = carving;
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
        return (BlockEntity) (Object) this;
    }

    @Override
    public boolean handson$rendersBoard() {
        return true;
    }

    @Override
    public NonNullList<ItemStack> handson$layers() {
        return handson$layers;
    }

    @Override
    public void handson$layersChanged() {
        BlockEntity self = (BlockEntity) (Object) this;
        self.setChanged();
        if (self.getLevel() != null) {
            self.getLevel().sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void handson$saveLayers(ValueOutput output, CallbackInfo callback) {
        if (handson$layers.stream().anyMatch(stack -> !stack.isEmpty())) {
            ContainerHelper.saveAllItems(output.child(LAYERS), handson$layers);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = true)
    private void handson$loadLayers(ValueInput input, CallbackInfo callback) {
        handson$layers.clear();
        input.child(LAYERS).ifPresent(layers -> ContainerHelper.loadAllItems(layers, handson$layers));
    }

    /** The board goes (broken, replaced): what lies on it falls with it, as its own item does. */
    @Inject(method = "preRemoveSideEffects", at = @At("HEAD"), remap = true)
    private void handson$dropLayers(BlockPos pos, BlockState state, CallbackInfo callback) {
        Level level = ((BlockEntity) (Object) this).getLevel();
        if (level != null) {
            Containers.dropContents(level, pos, handson$layers);
        }
        handson$layers.clear();
    }

    @Inject(method = "playProcessingSound", at = @At("HEAD"), cancellable = true)
    private void handson$oneSoundForABatch(SoundEvent sound, ItemStack tool, ItemStack item, CallbackInfo callback) {
        if (CaughtDrops.isQuiet()) {
            callback.cancel();
        }
    }
}
