package com.pockyl.handson_delight.mixin;

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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;

import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.CaughtDrops;
import com.pockyl.handson_delight.station.CuttingBoardStation;
import com.pockyl.handson_delight.station.KitchenBoard;

/**
 * The layers of a dish lie on the board for good: saved with it, sent to the clients in its update tag, cleared with it. Also keeps
 * the board quiet while it cuts the rest of a batch: Farmer's Delight's sound plays once, not for every item.
 */
@Mixin(CuttingBoardBlockEntity.class)
abstract class CuttingBoardBlockEntityMixin implements KitchenBoard {
    @Unique
    private static final String LAYERS = HandsOnDelight.MOD_ID + ":layers";

    @Unique
    private final NonNullList<ItemStack> handson$layers = NonNullList.withSize(CuttingBoardStation.MOST_LAYERS, ItemStack.EMPTY);

    @Unique
    private CuttingBoardBlockEntity handson$self() {
        return (CuttingBoardBlockEntity) (Object) this;
    }

    @Override
    public ItemStack handson$stored() {
        return Platform.INSTANCE.getItem(handson$self());
    }

    @Override
    public void handson$store(ItemStack stack) {
        Platform.INSTANCE.setItem(handson$self(), stack);
    }

    @Override
    public boolean handson$isCarving() {
        return handson$self().isItemCarvingBoard();
    }

    @Override
    public void handson$setCarving(boolean carving) {
        ((CuttingBoardBlockEntityAccessor) handson$self()).handson$writeCarving(carving);
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
        BlockEntity self = (BlockEntity) (Object) this;
        self.setChanged();
        if (self.getLevel() != null) {
            self.getLevel().sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void handson$saveLayers(ValueOutput output, CallbackInfo callback) {
        if (handson$layers.stream().anyMatch(stack -> !stack.isEmpty())) {
            // Its own compound with the items in it, as the layers were saved before Minecraft 26.x.
            ContainerHelper.saveAllItems(output.child(LAYERS), handson$layers);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void handson$loadLayers(ValueInput input, CallbackInfo callback) {
        handson$layers.clear();
        input.child(LAYERS).ifPresent(layers -> ContainerHelper.loadAllItems(layers, handson$layers));
    }

    /**
     * The board goes (broken, replaced): what lies on its item falls with it, as the item does. Since Minecraft 1.21.5 the board's
     * block entity drops its contents here, no longer its block as it is removed.
     */
    @Inject(method = "preRemoveSideEffects", at = @At("HEAD"))
    private void handson$dropLayers(BlockPos pos, BlockState state, CallbackInfo callback) {
        Level level = ((BlockEntity) (Object) this).getLevel();
        if (level != null) {
            Containers.dropContents(level, pos, handson$layers);
        }
        handson$layers.clear();
    }

    @Inject(method = "playProcessingSound", at = @At("HEAD"), cancellable = true, remap = false)
    private void handson$oneSoundForABatch(SoundEvent sound, ItemStack tool, ItemStack item, CallbackInfo callback) {
        if (CaughtDrops.isQuiet()) {
            callback.cancel();
        }
    }
}
