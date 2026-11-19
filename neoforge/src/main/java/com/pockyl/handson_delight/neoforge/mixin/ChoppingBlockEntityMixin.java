package com.pockyl.handson_delight.neoforge.mixin;

import com.snissac.sabletrees.block.ChoppingBlock;
import com.snissac.sabletrees.block.ChoppingBlockEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.pockyl.handson_delight.station.CuttingBoardStation;
import com.pockyl.handson_delight.station.KitchenStump;

/**
 * Sable Trees' chopping block, the stump of a felled tree, as a {@link KitchenStump}: what lies on it is its one stack, cut by its own
 * cut of Farmer's Delight's recipes (which counts the food it cuts and grows dirty), a log split by its own axe stroke. It holds no
 * dish: its layers stay empty.
 */
@Pseudo
@Mixin(value = ChoppingBlockEntity.class, remap = false)
abstract class ChoppingBlockEntityMixin implements KitchenStump {
    /** Sable Trees' line for a stump too dirty for food. */
    @Unique
    private static final String DIRTY = "message.sabletrees.chopping_block_dirty";

    @Shadow(remap = false)
    private int foodCuts;
    @Unique
    private final NonNullList<ItemStack> handson$layers = NonNullList.withSize(CuttingBoardStation.MOST_LAYERS, ItemStack.EMPTY);

    @Unique
    private ChoppingBlockEntity handson$self() {
        return (ChoppingBlockEntity) (Object) this;
    }

    @Override
    public ItemStack handson$stored() {
        return handson$self().getLog();
    }

    @Override
    public void handson$store(ItemStack stack) {
        handson$self().setLog(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    @Override
    public boolean handson$isCarving() {
        return false;
    }

    @Override
    public void handson$setCarving(boolean carving) {
    }

    @Override
    public int handson$maxStack() {
        return 64;
    }

    /** No tool is stuck into a stump: it takes nothing that wears. */
    @Override
    public boolean handson$carve(ItemStack tool) {
        return false;
    }

    @Override
    public boolean handson$cut(ItemStack tool, Player player) {
        BlockEntity self = (BlockEntity) (Object) this;
        ItemStack stored = handson$stored();
        ChoppingBlock.CuttingBoard cut = ChoppingBlockAccess.handson$cuttingBoard();
        if (cut == null || stored.isEmpty() || handson$refuses(stored) || self.getLevel() == null) {
            return false;
        }
        return cut.cut(self.getLevel(), self.getBlockPos(), handson$self(), tool, player, InteractionHand.MAIN_HAND);
    }

    /**
     * Sable Trees splits the log with the axe in the main hand: the axe the cook works with is held there for the stroke, then the hand
     * gets back what it held. The axe is worn as the stroke wears it.
     */
    @Override
    public boolean handson$split(ItemStack axe, Player player) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, axe);
        try {
            return ChoppingBlock.trySplit(level, self.getBlockPos(), player);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }
    }

    @Override
    public boolean handson$isDirty() {
        return handson$self().isDirty();
    }

    @Override
    public int handson$foodCuts() {
        return foodCuts;
    }

    @Override
    public void handson$tellDirty(Player player) {
        player.displayClientMessage(Component.translatable(DIRTY), true);
    }

    @Override
    public BlockEntity handson$blockEntity() {
        return (BlockEntity) (Object) this;
    }

    /** Its renderer draws the stump's dirt as well as the stack: only the stack is the scene's ({@code StumpContentMixin}). */
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
    }
}
