package com.pockyl.handson_delight.forge.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import umpaz.brewinandchewin.common.block.entity.KegBlockEntity;
import umpaz.brewinandchewin.common.crafting.KegFermentingRecipe;
import umpaz.brewinandchewin.common.crafting.KegPouringRecipe;
import umpaz.brewinandchewin.common.registry.BnCRecipeTypes;

import com.pockyl.handson_delight.station.KitchenKeg;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Brewin' and Chewin's keg as a {@link KitchenKeg}: it ferments as it always does, the station only reads it and uses it by hand. */
@Pseudo
@Mixin(value = KegBlockEntity.class, remap = false)
abstract class KegMixin implements KitchenKeg {
    @Shadow
    @Final
    private ItemStackHandler inventory;
    @Shadow
    @Final
    private FluidTank fluidTank;
    @Shadow
    @Final
    protected ContainerData kegData;

    @Unique
    private KegBlockEntity handson$self() {
        return (KegBlockEntity) (Object) this;
    }

    @Override
    public ItemStack handson$getItem(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public void handson$setItem(int slot, ItemStack stack) {
        inventory.setStackInSlot(slot, stack);
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
        return kegData;
    }

    @Override
    public void handson$feelTemperature() {
        if (handson$self().getLevel() != null) {
            handson$self().updateTemperature();
        }
    }

    @Override
    public int handson$wantedTemperature() {
        return handson$self().getRecipeWithoutTemperature().map(KegFermentingRecipe::getTemperature).orElse(0);
    }

    @Override
    public List<ItemStack> handson$use(ItemStack held, int most, boolean creative) {
        return handson$self().extractInWorld(held, most, creative);
    }

    @Override
    public boolean handson$canTap(ItemStack stack) {
        Optional<KegPouringRecipe> recipe = handson$recipeFor(stack);
        return recipe.isPresent() && ItemStack.isSameItem(stack, recipe.get().getContainer()) && !fluidTank.isEmpty()
                && fluidTank.getFluid().getFluid() == recipe.get().getRawFluid() && fluidTank.getFluidAmount() >= recipe.get().getAmount();
    }

    @Override
    public boolean handson$canPour(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Optional<KegPouringRecipe> recipe = handson$recipeFor(stack);
        if (recipe.isPresent()) {
            // A drink poured back: the same liquid, with room for it.
            KegPouringRecipe pouring = recipe.get();
            return pouring.canFill() && !ItemStack.isSameItem(stack, pouring.getContainer())
                    && (fluidTank.isEmpty() || fluidTank.getFluid().getFluid() == pouring.getRawFluid())
                    && fluidTank.getFluidAmount() + pouring.getAmount() <= fluidTank.getCapacity();
        }
        IFluidHandlerItem handler = stack.copyWithCount(1).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (handler == null || handler.getTanks() == 0 || handler.getFluidInTank(0).isEmpty()) {
            return false;
        }
        FluidStack inside = handler.getFluidInTank(0);
        if (!fluidTank.isEmpty() && !fluidTank.getFluid().isFluidEqual(inside)) {
            return false;
        }
        if (fluidTank.isEmpty() && handson$level().getRecipeManager().getAllRecipesFor(BnCRecipeTypes.KEG_POURING.get()).stream()
                .noneMatch(pouring -> pouring.getFluid(stack).isFluidEqual(inside))) {
            // The keg takes only liquids it knows a drink of, as its own use does.
            return false;
        }
        return fluidTank.getFluidAmount() + inside.getAmount() <= fluidTank.getCapacity();
    }

    @Override
    public boolean handson$isVessel(ItemStack stack) {
        if (stack.isEmpty() || handson$level() == null) {
            return false;
        }
        if (stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()) {
            return true;
        }
        return handson$level().getRecipeManager().getAllRecipesFor(BnCRecipeTypes.KEG_POURING.get()).stream()
                .anyMatch(pouring -> ItemStack.isSameItem(stack, pouring.getContainer()) || ItemStack.isSameItem(stack, pouring.getOutput()));
    }

    @Override
    public ItemStack handson$drinkOf(Fluid fluid) {
        if (fluid == Fluids.EMPTY || handson$level() == null) {
            return ItemStack.EMPTY;
        }
        for (KegPouringRecipe pouring : handson$level().getRecipeManager().getAllRecipesFor(BnCRecipeTypes.KEG_POURING.get())) {
            if (pouring.getRawFluid() == fluid && !pouring.getOutput().isEmpty()) {
                return pouring.getOutput().copy();
            }
        }
        return fluid.getBucket() == Items.AIR ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
    }

    @Override
    public List<Fermenting> handson$recipes() {
        List<Fermenting> recipes = new ArrayList<>();
        if (handson$level() == null) {
            return recipes;
        }
        for (KegFermentingRecipe recipe : handson$level().getRecipeManager().getAllRecipesFor(BnCRecipeTypes.FERMENTING.get())) {
            FluidStack base = recipe.getFluidIngredient();
            boolean liquid = base != null && !base.isEmpty();
            recipes.add(new Fermenting(recipe.getId(), List.copyOf(recipe.getIngredients()), liquid ? base.getFluid() : Fluids.EMPTY,
                    liquid ? base.getAmount() : 0, recipe.getResultFluid() == null ? Fluids.EMPTY : recipe.getResultFluid(),
                    recipe.getResultItem() == null ? ItemStack.EMPTY : new ItemStack(recipe.getResultItem(), recipe.getAmount()),
                    recipe.getFermentTime(), recipe.getTemperature()));
        }
        return recipes;
    }

    @Override
    public void handson$awardRecipes(Player player) {
        handson$self().awardUsedRecipes(player, handson$self().getDroppableInventory());
    }

    @Override
    public BlockEntity handson$blockEntity() {
        return handson$self();
    }

    @Unique
    private Level handson$level() {
        return handson$self().getLevel();
    }

    @Unique
    private Optional<KegPouringRecipe> handson$recipeFor(ItemStack stack) {
        return handson$level() == null || stack.isEmpty() ? Optional.empty() : handson$self().getPouringRecipe(stack);
    }
}
