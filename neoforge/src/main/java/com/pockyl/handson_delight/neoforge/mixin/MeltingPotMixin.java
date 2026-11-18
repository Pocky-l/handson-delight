package com.pockyl.handson_delight.neoforge.mixin;

import com.lance5057.extradelight.util.BottleFluidRegistry;
import com.lance5057.extradelight.workstations.meltingpot.MeltingPotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.pockyl.handson_delight.station.KitchenMeltingPot;

/** Extra Delight's melting pot as {@link KitchenMeltingPot}: its input slot and its tank, drawn from as its own bottle slot draws. */
@Pseudo
@Mixin(value = MeltingPotBlockEntity.class, remap = false)
abstract class MeltingPotMixin extends BlockEntity implements KitchenMeltingPot {
    @Unique
    private static final int BOTTLE = 250;
    @Unique
    private static final int BUCKET = 1000;

    @Shadow
    @Final
    private ItemStackHandler items;
    @Shadow
    @Final
    private FluidTank fluids;
    @Shadow
    public int cookingTime;
    @Shadow
    public int cookingProgress;

    private MeltingPotMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Shadow
    public abstract boolean isHeated();

    @Shadow
    public abstract void updateInventory();

    @Override
    public BlockEntity handson$blockEntity() {
        return this;
    }

    @Override
    public ItemStack handson$input() {
        return items.getStackInSlot(MeltingPotBlockEntity.INPUT_SLOT);
    }

    @Override
    public void handson$setInput(ItemStack stack) {
        items.setStackInSlot(MeltingPotBlockEntity.INPUT_SLOT, stack);
    }

    @Override
    public Fluid handson$fluid() {
        return fluids.getFluid().getFluid();
    }

    @Override
    public int handson$amount() {
        return fluids.getFluidAmount();
    }

    @Override
    public int handson$cookTime() {
        return cookingProgress;
    }

    @Override
    public int handson$cookTimeTotal() {
        return cookingTime;
    }

    @Override
    public boolean handson$isHeated() {
        return isHeated();
    }

    @Override
    public ItemStack handson$draw(ItemStack empty) {
        FluidStack in = fluids.getFluid();
        if (empty.isEmpty() || in.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack full = ItemStack.EMPTY;
        int amount = 0;
        if (empty.is(Items.BUCKET)) {
            if (in.getAmount() >= BUCKET && in.getFluid().getBucket() != Items.AIR) {
                full = new ItemStack(in.getFluid().getBucket());
                amount = BUCKET;
            }
        } else if (empty.is(Items.GLASS_BOTTLE)) {
            // Extra Delight's list of bottled liquids matches a bottle's worth exactly, not a tank of it.
            ItemStack bottle = in.getAmount() >= BOTTLE ? BottleFluidRegistry.getBottleFromFluid(in.copyWithAmount(BOTTLE)) : ItemStack.EMPTY;
            if (!bottle.isEmpty()) {
                full = bottle.copyWithCount(1);
                amount = BOTTLE;
            }
        }
        if (full.isEmpty()) {
            return ItemStack.EMPTY;
        }
        fluids.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        updateInventory();
        return full;
    }

    @Override
    public ItemStack handson$containerOf(Fluid liquid) {
        if (liquid == Fluids.EMPTY) {
            return ItemStack.EMPTY;
        }
        ItemStack bottle = BottleFluidRegistry.getBottleFromFluidWithoutSize(liquid);
        if (!bottle.isEmpty()) {
            return bottle.copyWithCount(1);
        }
        return liquid.getBucket() == Items.AIR ? ItemStack.EMPTY : new ItemStack(liquid.getBucket());
    }
}
