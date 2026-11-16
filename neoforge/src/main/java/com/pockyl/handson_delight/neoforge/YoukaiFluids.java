package com.pockyl.handson_delight.neoforge;

import dev.xkmc.youkaishomecoming.content.item.fluid.IYHFluidItem;
import dev.xkmc.youkaishomecoming.content.item.fluid.SlipBottleItem;
import dev.xkmc.youkaishomecoming.content.item.fluid.YHFluidHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Youkai's Feasts' liquids in and out of its kettle and fermentation tank, as its own clicks move them: its drinks (tea, sake, wine,
 * juice) into the container each is served in, a bottle (250 mB) at a time; anything in a container with a fluid handler both ways
 * (a bucket, its sake bottles, its flask, which holds any liquid); a water bottle in.
 */
public final class YoukaiFluids {
    /** A water bottle's worth, as its own clicks pour one. */
    private static final int BOTTLE = 250;

    private YoukaiFluids() {
    }

    /** The drink served of {@code fluid} and the container it goes into, as Youkai's Feasts knows it; null for none. */
    public static IYHFluidItem drink(FluidStack fluid) {
        return !fluid.isEmpty() && YHFluidHandler.of(fluid) instanceof IYHFluidItem drink ? drink : null;
    }

    /** Whether {@code stack} is its flask, which holds a whole tank of any liquid (1000 mB), empty or not. */
    public static boolean isFlask(ItemStack stack) {
        return SlipBottleItem.isSlipContainer(stack);
    }

    public static boolean isWaterBottle(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return stack.is(Items.POTION) && contents != null && contents.is(Potions.WATER);
    }

    /** Whether {@code stack} is filled from {@code tank} now: its drink's container, or what takes the liquid (an empty bucket). */
    public static boolean canServe(IFluidHandler tank, ItemStack stack) {
        if (stack.isEmpty() || tank == null) {
            return false;
        }
        FluidStack inside = tank.getFluidInTank(0);
        if (inside.isEmpty()) {
            return false;
        }
        IYHFluidItem drink = drink(inside);
        if (drink != null && stack.is(drink.getContainer())) {
            return inside.getAmount() >= drink.amount();
        }
        // Anything else that holds a liquid is filled as its own click fills it: its flask with wine or soy sauce, a bucket.
        return !isWaterBottle(stack) && FluidUtil.tryFillContainer(stack.copyWithCount(1), tank, Integer.MAX_VALUE, null, false).isSuccess();
    }

    /** Whether {@code stack} pours into {@code tank} now: a water bottle, or anything a liquid is poured from, with room for it. */
    public static boolean canPour(IFluidHandler tank, ItemStack stack) {
        if (stack.isEmpty() || tank == null) {
            return false;
        }
        if (isWaterBottle(stack)) {
            return tank.fill(new FluidStack(Fluids.WATER, BOTTLE), IFluidHandler.FluidAction.SIMULATE) == BOTTLE;
        }
        return FluidUtil.tryEmptyContainer(stack.copyWithCount(1), tank, Integer.MAX_VALUE, null, false).isSuccess();
    }

    /**
     * Uses one of {@code held} on {@code tank}: filled from it when it can be, else poured into it. {@code held} shrinks by the one used;
     * returned is what it became (the drink, the empty bottle or bucket), empty when nothing happened.
     */
    public static ItemStack useOne(IFluidHandler tank, ItemStack held) {
        if (held.isEmpty() || tank == null) {
            return ItemStack.EMPTY;
        }
        if (canServe(tank, held)) {
            IYHFluidItem drink = drink(tank.getFluidInTank(0));
            if (drink != null && held.is(drink.getContainer())) {
                tank.drain(drink.amount(), IFluidHandler.FluidAction.EXECUTE);
                held.shrink(1);
                return drink.asStack(1);
            }
            FluidActionResult filled = FluidUtil.tryFillContainer(held.copyWithCount(1), tank, Integer.MAX_VALUE, null, true);
            if (filled.isSuccess()) {
                held.shrink(1);
                return filled.getResult();
            }
        } else if (canPour(tank, held)) {
            if (isWaterBottle(held)) {
                tank.fill(new FluidStack(Fluids.WATER, BOTTLE), IFluidHandler.FluidAction.EXECUTE);
                held.shrink(1);
                return new ItemStack(Items.GLASS_BOTTLE);
            }
            FluidActionResult emptied = FluidUtil.tryEmptyContainer(held.copyWithCount(1), tank, Integer.MAX_VALUE, null, true);
            if (emptied.isSuccess()) {
                held.shrink(1);
                return emptied.getResult();
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Whether {@code stack} is a vessel, poured or filled and never dropped in: a water bottle, anything with a fluid handler, or a
     * drink of one of {@code drinks} or its container.
     */
    public static boolean isVessel(ItemStack stack, Iterable<Fluid> drinks) {
        if (stack.isEmpty()) {
            return false;
        }
        if (isWaterBottle(stack) || stack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM) != null) {
            return true;
        }
        for (Fluid fluid : drinks) {
            IYHFluidItem drink = fluid == Fluids.EMPTY ? null : drink(new FluidStack(fluid, 1000));
            if (drink != null && (stack.is(drink.getContainer()) || stack.is(drink.asStack(1).getItem()))) {
                return true;
            }
        }
        return false;
    }

    /** A serving of {@code fluid} as it is drawn: its drink, or its bucket; empty for none. */
    public static ItemStack drinkOf(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return ItemStack.EMPTY;
        }
        IYHFluidItem drink = drink(new FluidStack(fluid, 1000));
        if (drink != null) {
            return drink.asStack(1);
        }
        return fluid.getBucket() == Items.AIR ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
    }
}
