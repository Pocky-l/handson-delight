package com.pockyl.handson_delight.neoforge.mixin;

import com.lance5057.extradelight.util.BottleFluidRegistry;
import com.lance5057.extradelight.workstations.FancyTank;
import com.lance5057.extradelight.workstations.vat.VatBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.station.KitchenEdVat;

import java.util.List;

/**
 * Extra Delight's vat as a {@link KitchenEdVat}, and with this mod:
 * <ul>
 * <li>What ages without its containers (bottles, bowls, leaves) is made all the same and stays in the vat as servings, drawn into
 * the containers when the cook takes it: Extra Delight wants them in its container slot for the whole of the aging, up to two weeks.
 * With the containers in that slot (its own menu, a hopper) it ages its own way; servings waiting in the vat go into containers put
 * there the same way, so a player without this mod still gets them.</li>
 * <li>The lid the station lifts off and holds aside while a cook works is kept in the vat, saved with it and dropped when it breaks.</li>
 * </ul>
 * The vat already sends what lies in it to the clients near it every tick: both go along in that data.
 */
@Pseudo
@Mixin(value = VatBlockEntity.class, remap = false)
abstract class EdVatMixin implements KitchenEdVat {
    /** Its item handler's insertItem, which it calls twice as it finishes: to ask for room, then to put what it made there. */
    @Unique
    private static final String INSERT =
            "Lnet/neoforged/neoforge/items/ItemStackHandler;insertItem"
                    + "(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;";
    @Unique
    private static final String HANDSON_SERVINGS = "handson_delight:servings";
    @Unique
    private static final String HANDSON_LID = "handson_delight:lid";

    @Shadow
    @Final
    private ItemStackHandler items;
    @Shadow
    @Final
    private FancyTank fluid;
    @Shadow
    private int cookTime;
    @Shadow
    private int cookTimeTotal;
    @Shadow
    private int stage;
    @Shadow
    private int stageTotal;

    @Unique
    private ItemStack handson$servings = ItemStack.EMPTY;
    @Unique
    private ItemStack handson$lid = ItemStack.EMPTY;
    /** What is being made goes into the servings: its containers were not in the vat. */
    @Unique
    private boolean handson$intoServings;

    @Unique
    private BlockEntity handson$self() {
        return (BlockEntity) (Object) this;
    }

    @Override
    public ItemStack handson$item(int slot) {
        return items.getStackInSlot(slot);
    }

    @Override
    public void handson$setItem(int slot, ItemStack stack) {
        items.setStackInSlot(slot, stack);
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
        ItemStack one = full.copyWithCount(1);
        if (one.getItem() instanceof BucketItem bucket) {
            FluidStack bucketful = new FluidStack(bucket.content, 1000);
            if (bucket.content == Fluids.EMPTY || fluid.fill(bucketful, IFluidHandler.FluidAction.SIMULATE) != 1000) {
                return null;
            }
            fluid.fill(bucketful, IFluidHandler.FluidAction.EXECUTE);
            return new ItemStack(Items.BUCKET);
        }
        FluidStack bottled = BottleFluidRegistry.getFluidFromBottle(one);
        if (!bottled.isEmpty()) {
            if (fluid.fill(bottled.copy(), IFluidHandler.FluidAction.SIMULATE) != bottled.getAmount()) {
                return null;
            }
            fluid.fill(bottled.copy(), IFluidHandler.FluidAction.EXECUTE);
            ItemStack left = one.getCraftingRemainingItem();
            return one.is(Items.POTION) || left.isEmpty() ? new ItemStack(Items.GLASS_BOTTLE) : left.copy();
        }
        FluidActionResult poured = FluidUtil.tryEmptyContainer(one, fluid, Integer.MAX_VALUE, null, true);
        return poured.isSuccess() ? poured.getResult() : null;
    }

    @Override
    public ItemStack handson$draw(ItemStack empty) {
        ItemStack one = empty.copyWithCount(1);
        FluidStack inside = fluid.getFluid();
        if (inside.isEmpty()) {
            return null;
        }
        if (one.is(Items.BUCKET)) {
            if (inside.getAmount() < 1000 || inside.getFluid().getBucket() == Items.AIR) {
                return null;
            }
            fluid.drain(inside.copyWithAmount(1000), IFluidHandler.FluidAction.EXECUTE);
            return new ItemStack(inside.getFluid().getBucket());
        }
        ItemStack bottle = BottleFluidRegistry.getBottleFromFluid(inside.copyWithAmount(Math.min(inside.getAmount(), 250)));
        boolean takes = !bottle.isEmpty() && (ItemStack.isSameItem(bottle.getCraftingRemainingItem(), one)
                || bottle.is(Items.POTION) && one.is(Items.GLASS_BOTTLE));
        if (takes && inside.getAmount() >= 250) {
            fluid.drain(inside.copyWithAmount(250), IFluidHandler.FluidAction.EXECUTE);
            return bottle.copyWithCount(1);
        }
        FluidActionResult drawn = FluidUtil.tryFillContainer(one, fluid, Integer.MAX_VALUE, null, true);
        return drawn.isSuccess() ? drawn.getResult() : null;
    }

    @Override
    public int handson$stage() {
        return stage;
    }

    @Override
    public int handson$stages() {
        return stageTotal;
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
    public void handson$restart() {
        stage = 0;
        cookTime = 0;
        handson$self().setChanged();
    }

    @Override
    public ItemStack handson$servings() {
        return handson$servings;
    }

    @Override
    public void handson$setServings(ItemStack stack) {
        handson$servings = stack.copy();
        handson$self().setChanged();
    }

    @Override
    public ItemStack handson$lidAside() {
        return handson$lid;
    }

    @Override
    public void handson$setLidAside(ItemStack lid) {
        handson$lid = lid.copy();
        handson$self().setChanged();
    }

    @Inject(method = "writeNBT", at = @At("TAIL"))
    private void handson$save(CompoundTag tag, HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        if (!handson$servings.isEmpty()) {
            tag.put(HANDSON_SERVINGS, handson$servings.save(registries));
        }
        if (!handson$lid.isEmpty()) {
            tag.put(HANDSON_LID, handson$lid.save(registries));
        }
    }

    @Inject(method = "readNBT", at = @At("TAIL"))
    private void handson$load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        handson$servings = tag.contains(HANDSON_SERVINGS) ? ItemStack.parseOptional(registries, tag.getCompound(HANDSON_SERVINGS))
                : ItemStack.EMPTY;
        handson$lid = tag.contains(HANDSON_LID) ? ItemStack.parseOptional(registries, tag.getCompound(HANDSON_LID)) : ItemStack.EMPTY;
    }

    /**
     * Done, the vat asks whether its output has room for what it made. Made without its containers, it goes to the servings instead:
     * room there is room enough.
     */
    @Redirect(method = "tick", at = @At(value = "INVOKE", ordinal = 0,
            target = INSERT))
    private static ItemStack handson$room(ItemStackHandler handler, int slot, ItemStack result, boolean simulate, Level level, BlockPos pos,
            BlockState state, BlockEntity entity) {
        EdVatMixin self = (EdVatMixin) (Object) entity;
        self.handson$intoServings = HandsOnConfig.edVat() && handler.getStackInSlot(KitchenEdVat.CONTAINER_SLOT).isEmpty()
                && !KitchenEdVat.bottling(KitchenEdVat.recipes(level), result).isEmpty();
        if (!self.handson$intoServings) {
            return handler.insertItem(slot, result, simulate);
        }
        ItemStack waiting = self.handson$servings;
        boolean room = waiting.isEmpty() || ItemStack.isSameItemSameComponents(waiting, result)
                && waiting.getCount() + result.getCount() <= waiting.getMaxStackSize();
        return room ? ItemStack.EMPTY : result;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", ordinal = 1,
            target = INSERT))
    private static ItemStack handson$made(ItemStackHandler handler, int slot, ItemStack result, boolean simulate, Level level, BlockPos pos,
            BlockState state, BlockEntity entity) {
        EdVatMixin self = (EdVatMixin) (Object) entity;
        if (!self.handson$intoServings) {
            return handler.insertItem(slot, result, simulate);
        }
        self.handson$intoServings = false;
        ItemStack waiting = self.handson$servings;
        self.handson$setServings(waiting.isEmpty() ? result.copy() : waiting.copyWithCount(waiting.getCount() + result.getCount()));
        return ItemStack.EMPTY;
    }

    /** Servings waiting in the vat go into containers put in its container slot (its own menu, a hopper), one by one into its output. */
    @Inject(method = "tick", at = @At("TAIL"))
    private static void handson$bottle(Level level, BlockPos pos, BlockState state, BlockEntity entity, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        EdVatMixin self = (EdVatMixin) (Object) entity;
        ItemStack waiting = self.handson$servings;
        if (waiting.isEmpty()) {
            return;
        }
        ItemStack container = self.items.getStackInSlot(KitchenEdVat.CONTAINER_SLOT);
        ItemStack output = self.items.getStackInSlot(KitchenEdVat.OUTPUT_SLOT);
        List<KitchenEdVat.Aging> recipes = KitchenEdVat.recipes(level);
        if (!KitchenEdVat.draws(recipes, waiting, container)
                || !output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, waiting)
                        || output.getCount() >= output.getMaxStackSize())) {
            return;
        }
        self.items.setStackInSlot(KitchenEdVat.CONTAINER_SLOT, container.copyWithCount(container.getCount() - 1));
        self.items.setStackInSlot(KitchenEdVat.OUTPUT_SLOT,
                output.isEmpty() ? waiting.copyWithCount(1) : output.copyWithCount(output.getCount() + 1));
        self.handson$setServings(waiting.copyWithCount(waiting.getCount() - 1));
    }
}
