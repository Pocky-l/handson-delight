package com.pockyl.handson_delight.neoforge.mixin;

import com.farmersrespite.common.block.entity.KettleBlockEntity;
import com.farmersrespite.common.crafting.KettleRecipe;
import com.farmersrespite.core.registry.FRRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
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

import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.station.KitchenKettle;

import java.util.ArrayList;
import java.util.List;

/**
 * Farmer's Respite's kettle (its unofficial port to 1.21.1, which keeps the mod id, the block and its assets, but has its own code)
 * as a {@link KitchenKettle}: it brews as it always does, the station only reads it and uses it by hand, through the kettle's own
 * {@code fluidExtract} for what goes in and out, asked first without doing it for whether a use works. Its inventory has no slot for
 * the drink its menu shows: the station's container and output slots (3 and 4, as on 1.20.1) are its 2 and 3.
 */
@Pseudo
@Mixin(value = KettleBlockEntity.class, remap = false)
abstract class KettleMixin implements KitchenKettle {
    @Shadow
    @Final
    private ItemStackHandler inventory;
    @Shadow
    @Final
    private FluidTank fluidTank;
    @Shadow
    protected ContainerData kettleData;
    @Shadow
    private boolean checkNewRecipe;

    @Shadow
    public abstract ItemStack fluidExtract(KettleBlockEntity kettle, ItemStack stack, boolean doFill);

    /** A drink takes as long as the config's {@code kettle_time} makes it, however it got into the kettle. */
    @Redirect(method = "processBrewing", at = @At(value = "INVOKE",
            target = "Lcom/farmersrespite/common/crafting/KettleRecipe;getBrewTime()I"))
    private int handson$brewTime(KettleRecipe recipe) {
        return HandsOnConfig.scaled(recipe.getBrewTime(), HandsOnConfig.kettleTime());
    }

    /**
     * The kettle writes no tank while it is empty and reads one only when it is written, so the copy a client loads from the server
     * kept the last drink drawn at the station: that client took the empty kettle for full, opened the station on a bucket the
     * server would pour in, and poured nothing there either. No tank written is an empty tank.
     */
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void handson$emptyWhenUnwritten(CompoundTag compound, HolderLookup.Provider registries, CallbackInfo info) {
        if (!compound.contains("FluidTank")) {
            fluidTank.setFluid(FluidStack.EMPTY);
        }
    }

    @Unique
    private KettleBlockEntity handson$self() {
        return (KettleBlockEntity) (Object) this;
    }

    /** The kettle's own slot of one the station counts as 1.20.1's kettle has them; -1 for the drink shown in the menu. */
    @Unique
    private static int handson$slot(int slot) {
        return slot < INGREDIENTS ? slot : slot == CONTAINER_SLOT ? KettleBlockEntity.SLOT_CONTAINER
                : slot == OUTPUT_SLOT ? KettleBlockEntity.SLOT_OUTPUT : -1;
    }

    @Override
    public ItemStack handson$getItem(int slot) {
        int own = handson$slot(slot);
        return own < 0 ? ItemStack.EMPTY : inventory.getStackInSlot(own);
    }

    @Override
    public void handson$setItem(int slot, ItemStack stack) {
        int own = handson$slot(slot);
        if (own >= 0) {
            inventory.setStackInSlot(own, stack);
        }
    }

    @Override
    public Fluid handson$fluid() {
        return fluidTank.isEmpty() ? Fluids.EMPTY : fluidTank.getFluid().getFluid();
    }

    @Override
    public int handson$amount() {
        return fluidTank.getFluidAmount();
    }

    @Override
    public int handson$capacity() {
        return fluidTank.getCapacity();
    }

    @Override
    public ContainerData handson$data() {
        return kettleData;
    }

    @Override
    public boolean handson$isHeated() {
        KettleBlockEntity self = handson$self();
        return self.getLevel() != null && self.isHeated(self.getLevel(), self.getBlockPos());
    }

    @Override
    public ItemStack handson$useOne(ItemStack held) {
        if (held.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack back = fluidExtract(handson$self(), held.copyWithCount(1), true);
        if (back.isEmpty()) {
            return ItemStack.EMPTY;
        }
        held.shrink(1);
        // The kettle looks for a recipe again only when its ingredients change: leaves put in before the water would never brew.
        checkNewRecipe = true;
        return back;
    }

    @Override
    public boolean handson$canServe(ItemStack stack) {
        return !stack.isEmpty() && !fluidTank.isEmpty() && handson$isEmptyVessel(stack)
                && !fluidExtract(handson$self(), stack.copyWithCount(1), false).isEmpty();
    }

    @Override
    public boolean handson$canPour(ItemStack stack) {
        return !stack.isEmpty() && !handson$isEmptyVessel(stack) && !fluidExtract(handson$self(), stack.copyWithCount(1), false).isEmpty();
    }

    @Override
    public boolean handson$isVessel(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.GLASS_BOTTLE) || stack.is(Items.POTION) || handson$handlerOf(stack) != null) {
            return true;
        }
        Fluid inside = KettleBlockEntity.getFluidForItem(stack.getItem());
        if (inside != null && inside != Fluids.EMPTY) {
            return true;
        }
        return !fluidTank.isEmpty() && stack.is(KettleBlockEntity.getEmptyContainerForFluid(fluidTank.getFluid().getFluid()));
    }

    /** Whether {@code stack} is a vessel with nothing in it: a bottle, an empty bucket or tank, a cup of the kettle's drink. */
    @Unique
    private boolean handson$isEmptyVessel(ItemStack stack) {
        if (stack.is(Items.GLASS_BOTTLE)) {
            return true;
        }
        IFluidHandlerItem handler = handson$handlerOf(stack);
        if (handler != null) {
            return handler.getTanks() == 0 || handler.getFluidInTank(0).isEmpty();
        }
        Fluid inside = KettleBlockEntity.getFluidForItem(stack.getItem());
        return !stack.is(Items.POTION) && (inside == null || inside == Fluids.EMPTY);
    }

    @Override
    public ItemStack handson$drinkOf(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return ItemStack.EMPTY;
        }
        Item drink = KettleBlockEntity.getItemForFluid(fluid);
        if (drink != null && drink != Items.AIR) {
            return new ItemStack(drink);
        }
        return fluid.getBucket() == Items.AIR ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
    }

    @Override
    public int handson$tint(Fluid fluid) {
        return fluid == Fluids.EMPTY ? 0 : IClientFluidTypeExtensions.of(fluid).getTintColor();
    }

    @Override
    public List<Brewing> handson$recipes() {
        List<Brewing> recipes = new ArrayList<>();
        Level level = handson$self().getLevel();
        if (level == null) {
            return recipes;
        }
        for (RecipeHolder<KettleRecipe> holder : level.getRecipeManager().getAllRecipesFor(FRRecipeTypes.BREWING.get())) {
            KettleRecipe recipe = holder.value();
            FluidStack base = recipe.getInputFluid();
            FluidStack result = recipe.getResultFluid();
            if (base == null || base.isEmpty() || result == null || result.isEmpty()) {
                continue;
            }
            recipes.add(new Brewing(holder.id(), List.copyOf(recipe.getIngredients()), base.getFluid(), base.getAmount(), result.getFluid(),
                    recipe.getBrewTime()));
        }
        return recipes;
    }

    @Override
    public void handson$awardRecipes(Player player) {
        handson$self().clearUsedRecipes(player);
    }

    @Override
    public BlockEntity handson$blockEntity() {
        return handson$self();
    }

    @Unique
    private static IFluidHandlerItem handson$handlerOf(ItemStack stack) {
        return stack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
    }
}
