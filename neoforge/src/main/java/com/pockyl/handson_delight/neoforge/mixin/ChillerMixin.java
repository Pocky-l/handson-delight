package com.pockyl.handson_delight.neoforge.mixin;

import com.lance5057.extradelight.workstations.FancyTank;
import com.lance5057.extradelight.workstations.chiller.ChillerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.pockyl.handson_delight.neoforge.EdTanks;
import com.pockyl.handson_delight.station.KitchenChiller;

/**
 * Extra Delight's chiller as a {@link KitchenChiller}: its inventory, its tank, its cold and its drip pan as the station reads and
 * fills them. How it sets, melts its ice and drips stays its own. It already sends what is in it to the clients near it every tick.
 */
@Pseudo
@Mixin(value = ChillerBlockEntity.class, remap = false)
abstract class ChillerMixin implements KitchenChiller {
    @Shadow
    @Final
    private ItemStackHandler inventory;
    @Shadow
    @Final
    private FancyTank fluid;
    @Shadow
    @Final
    private FluidTank dripTray;
    @Shadow
    private int cookTime;
    @Shadow
    private int cookTimeTotal;
    @Shadow
    private int chilltime;
    @Shadow
    private int chillDuration;

    @Unique
    private BlockEntity handson$self() {
        return (BlockEntity) (Object) this;
    }

    @Override
    public ItemStack handson$item(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public void handson$setItem(int slot, ItemStack stack) {
        inventory.setStackInSlot(slot, stack);
        handson$self().setChanged();
    }

    @Override
    public Fluid handson$fluid() {
        FluidStack inside = fluid.getFluid();
        return inside.isEmpty() ? Fluids.EMPTY : inside.getFluid();
    }

    @Override
    public int handson$amount() {
        // Its tank's own getFluidAmount() answers a full bucket whatever it holds.
        return fluid.getFluid().getAmount();
    }

    @Override
    public ItemStack handson$pour(ItemStack full) {
        return EdTanks.pour(fluid, full);
    }

    @Override
    public ItemStack handson$draw(ItemStack empty) {
        return EdTanks.draw(fluid, empty);
    }

    @Override
    public int handson$time() {
        return cookTime;
    }

    @Override
    public int handson$timeTotal() {
        return cookTimeTotal;
    }

    @Override
    public int handson$chill() {
        return chilltime;
    }

    @Override
    public int handson$chillTotal() {
        return chillDuration;
    }

    @Override
    public int handson$pan() {
        return dripTray.getFluidAmount();
    }

    @Override
    public ItemStack handson$drawPan(ItemStack empty) {
        ItemStack one = empty.copyWithCount(1);
        FluidStack water = dripTray.getFluid();
        if (water.isEmpty()) {
            return null;
        }
        // The tank hands out its own stack, which drains with it: what it holds is read first.
        Fluid kind = water.getFluid();
        // Its own drip slot fills a bucket only from a full pan, and a bottle from a bottleful.
        if (one.is(Items.BUCKET)) {
            if (water.getAmount() < KitchenChiller.PAN || kind.getBucket() == Items.AIR) {
                return null;
            }
            dripTray.drain(KitchenChiller.BUCKET, IFluidHandler.FluidAction.EXECUTE);
            return new ItemStack(kind.getBucket());
        }
        if (one.is(Items.GLASS_BOTTLE)) {
            if (water.getAmount() < KitchenChiller.BOTTLE || !kind.isSame(Fluids.WATER)) {
                return null;
            }
            dripTray.drain(KitchenChiller.BOTTLE, IFluidHandler.FluidAction.EXECUTE);
            return PotionContents.createItemStack(Items.POTION, Potions.WATER);
        }
        FluidActionResult drawn = FluidUtil.tryFillContainer(one, dripTray, Integer.MAX_VALUE, null, true);
        return drawn.isSuccess() ? drawn.getResult() : null;
    }

    @Override
    public void handson$tipPan() {
        if (dripTray.getFluidAmount() > 0) {
            dripTray.drain(dripTray.getFluidAmount(), IFluidHandler.FluidAction.EXECUTE);
        }
    }
}
