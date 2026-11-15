package com.pockyl.handson_delight.neoforge.mixin;

import dev.xkmc.youkaishomecoming.content.item.fluid.IYHFluidItem;
import dev.xkmc.youkaishomecoming.content.item.fluid.YHFluidHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import vectorwing.farmersdelight.common.block.entity.HeatableBlockEntity;

import com.pockyl.handson_delight.station.KitchenKettle;

import java.util.ArrayList;
import java.util.List;

/**
 * Youkai's Feasts' kettle as a {@link KitchenKettle}: four leaves, one a slot, in a kettle of water (1000 mB) that comes to the boil
 * over heat, a tick a millibucket, and then brews for its recipe's time into a whole kettle of tea, poured a bottle (250 mB) at a
 * time. It brews as it always does; the station only reads it and uses it by hand as its own clicks do: its drink into its container,
 * a water bottle or a bucket in, and back out of a liquid that is no drink of it. Its classes build on the L2 libraries nested in its
 * jar, so it is reached by name, its slots and tank through the block capabilities it registers. It has no lid of its own: the station's lid is
 * kept here, off only while somebody works at it.
 */
@Pseudo
@Mixin(targets = "dev.xkmc.youkaishomecoming.content.pot.kettle.KettleBlockEntity", remap = false)
abstract class YoukaiKettleMixin implements KitchenKettle {
    @Unique
    private static final ResourceLocation HANDSON$BREWING = ResourceLocation.tryBuild("youkaisfeasts", "kettle");
    @Unique
    private static final int HANDSON$BOTTLE = 250;

    @Shadow
    private int heat;

    @Unique
    private boolean handson$open;
    @Unique
    private RecipeManager handson$readFrom;
    @Unique
    private List<Brewing> handson$brewing = List.of();

    @Unique
    private BlockEntity handson$self() {
        return (BlockEntity) (Object) this;
    }

    @Unique
    private IItemHandlerModifiable handson$items() {
        BlockEntity self = handson$self();
        IItemHandler handler = self.getLevel() == null ? null
                : self.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, self.getBlockPos(), self.getBlockState(), self, null);
        return handler instanceof IItemHandlerModifiable modifiable ? modifiable : null;
    }

    @Unique
    private IFluidHandler handson$tank() {
        BlockEntity self = handson$self();
        return self.getLevel() == null ? null
                : self.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, self.getBlockPos(), self.getBlockState(), self, null);
    }

    @Unique
    private FluidStack handson$inside() {
        IFluidHandler tank = handson$tank();
        return tank == null ? FluidStack.EMPTY : tank.getFluidInTank(0);
    }

    /** The drink the kettle pours of {@code fluid} and the container it goes into, as Youkai's Feasts knows it; null for none. */
    @Unique
    private static IYHFluidItem handson$drink(FluidStack fluid) {
        return !fluid.isEmpty() && YHFluidHandler.of(fluid) instanceof IYHFluidItem drink ? drink : null;
    }

    @Override
    public int handson$ingredients() {
        return MOST_INGREDIENTS;
    }

    @Override
    public int handson$perSlot() {
        return 1;
    }

    @Override
    public boolean handson$lidInBlock() {
        return false;
    }

    @Override
    public boolean handson$isOpen() {
        return handson$open;
    }

    @Override
    public void handson$setOpen(boolean open) {
        handson$open = open;
    }

    @Override
    public boolean handson$turnsOnTray() {
        return true;
    }

    @Override
    public boolean handson$boilsFirst() {
        return true;
    }

    @Override
    public float handson$boil() {
        FluidStack inside = handson$inside();
        return inside.isEmpty() || !inside.getFluid().is(FluidTags.WATER) ? -1.0F : Math.min(1.0F, (float) heat / inside.getAmount());
    }

    @Override
    public int handson$ticksToBoil() {
        FluidStack inside = handson$inside();
        return inside.isEmpty() || !inside.getFluid().is(FluidTags.WATER) ? 0 : Math.max(0, inside.getAmount() - heat);
    }

    @Override
    public ItemStack handson$getItem(int slot) {
        IItemHandlerModifiable items = handson$items();
        return items == null || slot >= items.getSlots() ? ItemStack.EMPTY : items.getStackInSlot(slot);
    }

    @Override
    public void handson$setItem(int slot, ItemStack stack) {
        IItemHandlerModifiable items = handson$items();
        if (items != null && slot < items.getSlots()) {
            items.setStackInSlot(slot, stack);
        }
    }

    @Override
    public Fluid handson$fluid() {
        FluidStack inside = handson$inside();
        return inside.isEmpty() ? Fluids.EMPTY : inside.getFluid();
    }

    @Override
    public int handson$amount() {
        return handson$inside().getAmount();
    }

    @Override
    public int handson$capacity() {
        IFluidHandler tank = handson$tank();
        return tank == null ? 1000 : tank.getTankCapacity(0);
    }

    @Override
    public ContainerData handson$data() {
        YoukaiTimedAccess timed = (YoukaiTimedAccess) this;
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> timed.handson$recipeProgress();
                    case 1 -> timed.handson$totalTime();
                    default -> heat;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    timed.handson$setRecipeProgress(value);
                } else if (index == 2) {
                    heat = value;
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    @Override
    public boolean handson$isHeated() {
        BlockEntity self = handson$self();
        return self.getLevel() != null && ((HeatableBlockEntity) this).isHeated(self.getLevel(), self.getBlockPos());
    }

    @Override
    public ItemStack handson$useOne(ItemStack held) {
        IFluidHandler tank = handson$tank();
        if (held.isEmpty() || tank == null) {
            return ItemStack.EMPTY;
        }
        if (handson$canServe(held)) {
            IYHFluidItem drink = handson$drink(handson$inside());
            if (drink != null) {
                tank.drain(drink.amount(), IFluidHandler.FluidAction.EXECUTE);
                held.shrink(1);
                return drink.asStack(1);
            }
            FluidActionResult filled = FluidUtil.tryFillContainer(held.copyWithCount(1), tank, Integer.MAX_VALUE, null, true);
            if (filled.isSuccess()) {
                held.shrink(1);
                return filled.getResult();
            }
        } else if (handson$canPour(held)) {
            if (handson$isWaterBottle(held)) {
                tank.fill(new FluidStack(Fluids.WATER, HANDSON$BOTTLE), IFluidHandler.FluidAction.EXECUTE);
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

    /** Its drink into the drink's container (a bottle of tea); out of plain water, an empty bucket or the like, as its own clicks do. */
    @Override
    public boolean handson$canServe(ItemStack stack) {
        FluidStack inside = handson$inside();
        IFluidHandler tank = handson$tank();
        if (stack.isEmpty() || inside.isEmpty() || tank == null) {
            return false;
        }
        IYHFluidItem drink = handson$drink(inside);
        if (drink != null) {
            return stack.is(drink.getContainer()) && inside.getAmount() >= drink.amount();
        }
        return !handson$isWaterBottle(stack) && FluidUtil.tryFillContainer(stack.copyWithCount(1), tank, Integer.MAX_VALUE, null, false).isSuccess();
    }

    /** A water bottle, or anything a liquid is poured from (a water bucket), with room for it. */
    @Override
    public boolean handson$canPour(ItemStack stack) {
        IFluidHandler tank = handson$tank();
        if (stack.isEmpty() || tank == null) {
            return false;
        }
        if (handson$isWaterBottle(stack)) {
            return tank.fill(new FluidStack(Fluids.WATER, HANDSON$BOTTLE), IFluidHandler.FluidAction.SIMULATE) == HANDSON$BOTTLE;
        }
        return FluidUtil.tryEmptyContainer(stack.copyWithCount(1), tank, Integer.MAX_VALUE, null, false).isSuccess();
    }

    @Unique
    private static boolean handson$isWaterBottle(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return stack.is(Items.POTION) && contents != null && contents.is(Potions.WATER);
    }

    @Override
    public boolean handson$isVessel(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (handson$isWaterBottle(stack) || stack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM) != null) {
            return true;
        }
        for (Brewing recipe : handson$recipes()) {
            IYHFluidItem drink = handson$drink(new FluidStack(recipe.result(), recipe.baseAmount()));
            if (drink != null && (stack.is(drink.getContainer()) || stack.is(drink.asStack(1).getItem()))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack handson$drinkOf(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return ItemStack.EMPTY;
        }
        IYHFluidItem drink = handson$drink(new FluidStack(fluid, 1000));
        if (drink != null) {
            return drink.asStack(1);
        }
        return fluid.getBucket() == Items.AIR ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
    }

    @Override
    public int handson$tint(Fluid fluid) {
        return fluid == Fluids.EMPTY ? 0 : IClientFluidTypeExtensions.of(fluid).getTintColor();
    }

    /** Its brewing recipes, read once for each recipe manager (the book and the scene ask every frame). */
    @Override
    public List<Brewing> handson$recipes() {
        Level level = handson$self().getLevel();
        if (level == null) {
            return List.of();
        }
        RecipeManager manager = level.getRecipeManager();
        if (manager == handson$readFrom) {
            return handson$brewing;
        }
        List<Brewing> recipes = new ArrayList<>();
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(HANDSON$BREWING);
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (type == null || holder.value().getType() != type || !(holder.value() instanceof YoukaiKettleRecipeAccess brewing)) {
                continue;
            }
            FluidStack result = brewing.handson$result();
            List<Ingredient> input = brewing.handson$input();
            if (result == null || result.isEmpty() || input == null || input.isEmpty()) {
                continue;
            }
            // Its kettle brews only a full kettle of boiling water.
            recipes.add(new Brewing(holder.id(), List.copyOf(input), Fluids.WATER, 1000, result.getFluid(), brewing.handson$time()));
        }
        handson$readFrom = manager;
        handson$brewing = List.copyOf(recipes);
        return handson$brewing;
    }

    /** Its kettle gives nothing for a drink but the drink. */
    @Override
    public void handson$awardRecipes(Player player) {
    }

    @Override
    public BlockEntity handson$blockEntity() {
        return handson$self();
    }
}
