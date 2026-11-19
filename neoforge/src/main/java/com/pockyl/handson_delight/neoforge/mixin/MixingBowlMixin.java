package com.pockyl.handson_delight.neoforge.mixin;

import com.lance5057.extradelight.util.BottleFluidRegistry;
import com.lance5057.extradelight.workstations.FancyTank;
import com.lance5057.extradelight.workstations.mixingbowl.recipes.MixingBowlRecipe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.pockyl.handson_delight.station.KitchenBowl;

import java.util.ArrayList;
import java.util.List;

/**
 * Extra Delight's mixing bowl as a {@link KitchenBowl}. Its places are read and set through its own item handler, so that it starts
 * its stirs over and finds its recipe as when its menu changes them; liquids go in and out of its own tank a bottle (250 mB) or a
 * container at a time, as its menu pours them, but the empty container goes back to the cook instead of dropping on the floor. An
 * egg is cracked and the last stir is made by its own code.
 */
@Pseudo
@Mixin(targets = "com.lance5057.extradelight.workstations.mixingbowl.MixingBowlBlockEntity", remap = false)
abstract class MixingBowlMixin implements KitchenBowl {
    @Shadow
    private int stirs;

    @Shadow
    MixingBowlRecipe curRecipe;

    @Shadow
    public abstract IItemHandlerModifiable getItemHandler();

    @Shadow
    public abstract FancyTank getFluidTank();

    @Shadow
    public abstract InteractionResult mix(Player player, ItemStack utensil);

    @Shadow
    public abstract ItemInteractionResult handleEgg(Player player, ItemStack stack);

    @Shadow
    public abstract void updateInventory();

    @Override
    public BlockEntity handson$blockEntity() {
        return (BlockEntity) (Object) this;
    }

    @Override
    public ItemStack handson$item(int slot) {
        return getItemHandler().getStackInSlot(slot);
    }

    @Override
    public void handson$setItem(int slot, ItemStack stack) {
        getItemHandler().setStackInSlot(slot, stack);
    }

    @Override
    public List<Liquid> handson$liquids() {
        List<Liquid> liquids = new ArrayList<>();
        for (FluidStack fluid : getFluidTank().getAsList()) {
            if (!fluid.isEmpty()) {
                liquids.add(new Liquid(BuiltInRegistries.FLUID.getKey(fluid.getFluid()), fluid.getAmount()));
            }
        }
        return liquids;
    }

    @Override
    public ItemStack handson$pour(ItemStack full, boolean simulate) {
        if (full.isEmpty()) {
            return null;
        }
        ItemStack one = full.copyWithCount(1);
        FancyTank tank = getFluidTank();
        // A potion is poured only as water: the bowl's own list takes any potion for a water bottle.
        FluidStack bottled = one.is(Items.POTION) && !handson$isWater(one) ? FluidStack.EMPTY : BottleFluidRegistry.getFluidFromBottle(one);
        if (!bottled.isEmpty()) {
            FluidStack poured = bottled.copyWithAmount(BOTTLE);
            if (tank.fill(poured, IFluidHandler.FluidAction.SIMULATE) != BOTTLE) {
                return null;
            }
            if (!simulate) {
                tank.fill(poured, IFluidHandler.FluidAction.EXECUTE);
            }
            return one.is(Items.POTION) ? new ItemStack(Items.GLASS_BOTTLE) : one.getCraftingRemainingItem().copyWithCount(1);
        }
        IFluidHandlerItem container = one.getCapability(Capabilities.FluidHandler.ITEM);
        if (container == null) {
            return null;
        }
        FluidStack inside = container.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        // The bowl takes a liquid whole or not at all.
        if (inside.isEmpty() || tank.fill(inside.copy(), IFluidHandler.FluidAction.SIMULATE) != inside.getAmount()) {
            return null;
        }
        // The container is a copy: it is emptied either way, the bowl filled only for real.
        FluidStack drained = container.drain(inside.getAmount(), IFluidHandler.FluidAction.EXECUTE);
        if (!simulate) {
            tank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        }
        return container.getContainer().copy();
    }

    @Override
    public Liquid handson$liquidOf(ItemStack full) {
        if (full.isEmpty()) {
            return null;
        }
        ItemStack one = full.copyWithCount(1);
        FluidStack bottled = one.is(Items.POTION) && !handson$isWater(one) ? FluidStack.EMPTY : BottleFluidRegistry.getFluidFromBottle(one);
        if (!bottled.isEmpty()) {
            return new Liquid(BuiltInRegistries.FLUID.getKey(bottled.getFluid()), BOTTLE);
        }
        IFluidHandlerItem container = one.getCapability(Capabilities.FluidHandler.ITEM);
        FluidStack inside = container == null ? FluidStack.EMPTY : container.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        return inside.isEmpty() ? null : new Liquid(BuiltInRegistries.FLUID.getKey(inside.getFluid()), inside.getAmount());
    }

    @Unique
    private static boolean handson$isWater(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.is(Potions.WATER);
    }

    @Override
    public ItemStack handson$draw(ItemStack empty, boolean simulate) {
        if (empty.isEmpty()) {
            return null;
        }
        FancyTank tank = getFluidTank();
        List<FluidStack> liquids = tank.getAsList();
        // The liquid poured last comes out first: it lies on top.
        for (int i = liquids.size() - 1; i >= 0; i--) {
            FluidStack liquid = liquids.get(i);
            if (liquid.getAmount() >= BOTTLE) {
                ItemStack bottle = BottleFluidRegistry.getBottleFromFluid(liquid.copyWithAmount(BOTTLE));
                boolean fits = !bottle.isEmpty() && (ItemStack.isSameItem(bottle.getCraftingRemainingItem(), empty)
                        || bottle.is(Items.POTION) && empty.is(Items.GLASS_BOTTLE));
                if (fits) {
                    if (!simulate) {
                        tank.drain(liquid.copyWithAmount(BOTTLE), IFluidHandler.FluidAction.EXECUTE);
                    }
                    return bottle.copyWithCount(1);
                }
            }
            IFluidHandlerItem container = empty.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
            if (container == null) {
                continue;
            }
            int taken = container.fill(liquid.copy(), IFluidHandler.FluidAction.SIMULATE);
            if (taken <= 0) {
                continue;
            }
            container.fill(liquid.copyWithAmount(taken), IFluidHandler.FluidAction.EXECUTE);
            if (!simulate) {
                tank.drain(liquid.copyWithAmount(taken), IFluidHandler.FluidAction.EXECUTE);
            }
            return container.getContainer().copy();
        }
        return null;
    }

    @Override
    public boolean handson$separate(Player player) {
        return handleEgg(player, new ItemStack(Items.EGG)) == ItemInteractionResult.SUCCESS;
    }

    @Override
    public int handson$stirs() {
        return stirs;
    }

    @Override
    public void handson$setStirs(int stirs) {
        this.stirs = stirs;
        handson$blockEntity().setChanged();
    }

    @Override
    public boolean handson$mix(Player player, ItemStack utensil) {
        // Read again: after a load the bowl knows no recipe until something in it changes.
        updateInventory();
        MixingBowlRecipe recipe = curRecipe;
        if (recipe == null || !recipe.getUtensil().test(utensil)) {
            return false;
        }
        stirs = recipe.getStirs();
        return mix(player, utensil) == InteractionResult.SUCCESS;
    }
}
