package com.pockyl.handson_delight.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import umpaz.brewinandchewin.BrewinAndChewin;
import umpaz.brewinandchewin.common.block.entity.KegBlockEntity;
import umpaz.brewinandchewin.common.container.AbstractedFluidTank;
import umpaz.brewinandchewin.common.container.AbstractedItemHandler;
import umpaz.brewinandchewin.common.crafting.FluidIngredientWithAmount;
import umpaz.brewinandchewin.common.crafting.KegFermentingRecipe;
import umpaz.brewinandchewin.common.crafting.KegPouringRecipe;
import umpaz.brewinandchewin.common.registry.BnCRecipeTypes;
import umpaz.brewinandchewin.common.utility.AbstractedFluidStack;
import umpaz.brewinandchewin.common.utility.FluidUnit;

import com.pockyl.handson_delight.station.KitchenKeg;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Brewin' and Chewin's keg as a {@link KitchenKeg}: it ferments as it always does, the station only reads it and uses it by hand. The
 * keg's code is the same on both loaders and counts liquids in the loader's unit (droplets on Fabric): the station gets millibuckets.
 * Members the keg inherits from Minecraft are called through Minecraft's own types, so that the Fabric jar names them as Fabric does.
 */
@Pseudo
@Mixin(value = KegBlockEntity.class, remap = false)
abstract class KegMixin implements KitchenKeg {
    @Shadow
    @Final
    private AbstractedItemHandler inventory;
    @Shadow
    @Final
    private AbstractedFluidTank fluidTank;
    @Shadow
    @Final
    protected ContainerData kegData;
    /** The keg's data keeps the ticks only: its temperature comes from the keg itself. */
    @Unique
    private ContainerData handson$data;

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
        return fluidTank.isEmpty() ? Fluids.EMPTY : fluidTank.getAbstractedFluid().fluid();
    }

    @Override
    public int handson$amount() {
        return fluidTank.isEmpty() ? 0 : handson$millibuckets(fluidTank.getAbstractedFluid().amount());
    }

    @Override
    public int handson$capacity() {
        return handson$millibuckets(fluidTank.getFluidCapacity());
    }

    @Override
    public ContainerData handson$data() {
        if (handson$data == null) {
            handson$data = KitchenKeg.withTemperature(kegData, () -> handson$self().getTemperature());
        }
        return handson$data;
    }

    @Override
    public void handson$feelTemperature() {
        if (handson$level() != null) {
            handson$self().updateTemperature();
        }
    }

    @Override
    public int handson$wantedTemperature() {
        return handson$self().getRecipeWithoutTemperature().map(holder -> holder.value().getTemperature()).orElse(0);
    }

    @Override
    public List<ItemStack> handson$use(ItemStack held, int most, boolean creative) {
        return handson$self().extractInWorld(held, most, creative);
    }

    @Override
    public boolean handson$canTap(ItemStack stack) {
        Optional<KegPouringRecipe> recipe = handson$recipeFor(stack);
        return recipe.isPresent() && ItemStack.isSameItem(stack, recipe.get().getContainer()) && !fluidTank.isEmpty()
                && fluidTank.getAbstractedFluid().fluid() == recipe.get().getRawFluid().fluid()
                && fluidTank.getAbstractedFluid().amount() >= recipe.get().getLoaderAmount();
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
                    && (fluidTank.isEmpty() || fluidTank.getAbstractedFluid().fluid() == pouring.getRawFluid().fluid())
                    && handson$loaderAmount() + pouring.getLoaderAmount() <= fluidTank.getFluidCapacity();
        }
        AbstractedFluidTank item = BrewinAndChewin.getHelper().getFluidContainerFromItem(stack.copyWithCount(1));
        if (item == null || item.isEmpty() || item.getAbstractedFluid().isEmpty()) {
            return false;
        }
        AbstractedFluidStack inside = item.getAbstractedFluid();
        if (!fluidTank.isEmpty() && fluidTank.getAbstractedFluid().fluid() != inside.fluid()) {
            return false;
        }
        if (fluidTank.isEmpty() && handson$pouring().stream().noneMatch(pouring -> pouring.getFluid(stack).fluid() == inside.fluid())) {
            // The keg takes only liquids it knows a drink of, as its own use does.
            return false;
        }
        return handson$loaderAmount() + inside.amount() <= fluidTank.getFluidCapacity();
    }

    @Override
    public boolean handson$isVessel(ItemStack stack) {
        if (stack.isEmpty() || handson$level() == null) {
            return false;
        }
        if (BrewinAndChewin.getHelper().getFluidContainerFromItem(stack.copyWithCount(1)) != null) {
            return true;
        }
        return handson$pouring().stream()
                .anyMatch(pouring -> ItemStack.isSameItem(stack, pouring.getContainer()) || ItemStack.isSameItem(stack, pouring.getOutput()));
    }

    @Override
    public ItemStack handson$drinkOf(Fluid fluid) {
        if (fluid == Fluids.EMPTY || handson$level() == null) {
            return ItemStack.EMPTY;
        }
        for (KegPouringRecipe pouring : handson$pouring()) {
            if (pouring.getRawFluid().fluid() == fluid && !pouring.getOutput().isEmpty()) {
                return pouring.getOutput().copy();
            }
        }
        return fluid.getBucket() == Items.AIR ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
    }

    @Override
    public List<Fermenting> handson$recipes() {
        List<Fermenting> recipes = new ArrayList<>();
        Level level = handson$level();
        if (level == null) {
            return recipes;
        }
        for (RecipeHolder<KegFermentingRecipe> holder : level.getRecipeManager().getAllRecipesFor(BnCRecipeTypes.FERMENTING)) {
            KegFermentingRecipe recipe = holder.value();
            Optional<FluidIngredientWithAmount> base = recipe.getFluidIngredient();
            Fluid baseFluid = base.flatMap(ingredient -> ingredient.ingredient().displayStacks().stream().findFirst())
                    .map(AbstractedFluidStack::fluid).orElse(Fluids.EMPTY);
            int baseAmount = baseFluid == Fluids.EMPTY ? 0 : handson$millibuckets(base.get().loaderAmount());
            Fluid resultFluid = recipe.getResult().left().map(AbstractedFluidStack::fluid).orElse(Fluids.EMPTY);
            ItemStack resultItem = recipe.getResult().right().map(ItemStack::copy).orElse(ItemStack.EMPTY);
            recipes.add(new Fermenting(holder.id(), List.copyOf(recipe.getIngredients()), baseFluid, baseAmount, resultFluid, resultItem,
                    recipe.getFermentTime(), recipe.getTemperature()));
        }
        return recipes;
    }

    @Override
    public void handson$awardRecipes(Player player) {
        ((RecipeCraftingHolder) (Object) this).awardUsedRecipes(player, handson$self().getDroppableInventory());
    }

    @Override
    public BlockEntity handson$blockEntity() {
        return (BlockEntity) (Object) this;
    }

    @Unique
    private Level handson$level() {
        return handson$blockEntity().getLevel();
    }

    @Unique
    private long handson$loaderAmount() {
        return fluidTank.isEmpty() ? 0 : fluidTank.getAbstractedFluid().amount();
    }

    @Unique
    private List<KegPouringRecipe> handson$pouring() {
        Level level = handson$level();
        return level == null ? List.of()
                : level.getRecipeManager().getAllRecipesFor(BnCRecipeTypes.KEG_POURING).stream().map(RecipeHolder::value).toList();
    }

    @Unique
    private Optional<KegPouringRecipe> handson$recipeFor(ItemStack stack) {
        return handson$level() == null || stack.isEmpty() ? Optional.empty() : handson$self().getPouringRecipe(stack);
    }

    @Unique
    private static int handson$millibuckets(long amount) {
        return (int) FluidUnit.convert(amount, FluidUnit.getLoaderUnit(), FluidUnit.MILLIBUCKET);
    }
}
