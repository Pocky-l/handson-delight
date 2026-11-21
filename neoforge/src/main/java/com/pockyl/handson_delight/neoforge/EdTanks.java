package com.pockyl.handson_delight.neoforge;

import com.lance5057.extradelight.util.BottleFluidRegistry;
import com.lance5057.extradelight.workstations.FancyTank;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Pouring into and drawing from the tanks of Extra Delight's blocks by hand, one container at a time, as its own liquid slots do:
 * a bucket, a bottle of a liquid it lists, or any container that holds a fluid. Loaded only with Extra Delight there.
 */
public final class EdTanks {
    private static final int BOTTLE = 250;
    private static final int BUCKET = 1000;

    private EdTanks() {
    }

    /** Pours the liquid of one of {@code full} in, all of it or nothing: the container left, or null when nothing went in. */
    public static ItemStack pour(FancyTank tank, ItemStack full) {
        ItemStack one = full.copyWithCount(1);
        if (one.getItem() instanceof BucketItem bucket) {
            FluidStack bucketful = new FluidStack(bucket.content, BUCKET);
            if (bucket.content == Fluids.EMPTY || tank.fill(bucketful, IFluidHandler.FluidAction.SIMULATE) != BUCKET) {
                return null;
            }
            tank.fill(bucketful, IFluidHandler.FluidAction.EXECUTE);
            return new ItemStack(Items.BUCKET);
        }
        FluidStack bottled = BottleFluidRegistry.getFluidFromBottle(one);
        if (!bottled.isEmpty()) {
            if (tank.fill(bottled.copy(), IFluidHandler.FluidAction.SIMULATE) != bottled.getAmount()) {
                return null;
            }
            tank.fill(bottled.copy(), IFluidHandler.FluidAction.EXECUTE);
            ItemStack left = one.getCraftingRemainingItem();
            return one.is(Items.POTION) || left.isEmpty() ? new ItemStack(Items.GLASS_BOTTLE) : left.copy();
        }
        FluidActionResult poured = FluidUtil.tryEmptyContainer(one, tank, Integer.MAX_VALUE, null, true);
        return poured.isSuccess() ? poured.getResult() : null;
    }

    /** Draws the liquid into one of {@code empty}: the container filled, or null when it takes none of it. */
    public static ItemStack draw(FancyTank tank, ItemStack empty) {
        ItemStack one = empty.copyWithCount(1);
        FluidStack inside = tank.getFluid();
        if (inside.isEmpty()) {
            return null;
        }
        if (one.is(Items.BUCKET)) {
            if (inside.getAmount() < BUCKET || inside.getFluid().getBucket() == Items.AIR) {
                return null;
            }
            tank.drain(inside.copyWithAmount(BUCKET), IFluidHandler.FluidAction.EXECUTE);
            return new ItemStack(inside.getFluid().getBucket());
        }
        ItemStack bottle = BottleFluidRegistry.getBottleFromFluid(inside.copyWithAmount(Math.min(inside.getAmount(), BOTTLE)));
        boolean takes = !bottle.isEmpty() && (ItemStack.isSameItem(bottle.getCraftingRemainingItem(), one)
                || bottle.is(Items.POTION) && one.is(Items.GLASS_BOTTLE));
        if (takes && inside.getAmount() >= BOTTLE) {
            tank.drain(inside.copyWithAmount(BOTTLE), IFluidHandler.FluidAction.EXECUTE);
            return bottle.copyWithCount(1);
        }
        FluidActionResult drawn = FluidUtil.tryFillContainer(one, tank, Integer.MAX_VALUE, null, true);
        return drawn.isSuccess() ? drawn.getResult() : null;
    }
}
