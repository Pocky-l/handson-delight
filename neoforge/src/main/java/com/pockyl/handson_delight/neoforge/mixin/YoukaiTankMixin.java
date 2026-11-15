package com.pockyl.handson_delight.neoforge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import com.pockyl.handson_delight.neoforge.YoukaiFluids;
import com.pockyl.handson_delight.station.KegStation;
import com.pockyl.handson_delight.station.KitchenKeg;
import com.pockyl.handson_delight.station.StationRecipes;

import java.util.ArrayList;
import java.util.List;

/**
 * Youkai's Feasts' fermentation tank as a {@link KitchenKeg}: eight things, one a slot, and a tank of 1000 mB under a lid that is its
 * block's {@code open} state. Shut, a recipe of what is in it counts up to its time (back down while the lid is off) and then turns
 * the liquid into another (water into sake, juice into wine) or the things into others (tea leaves, natto), and the tank lifts its
 * own lid. It ferments as it always does; the station only reads it and uses it by hand as its own clicks do. Its classes build on
 * the L2 libraries nested in its jar, so it is reached by name, its slots and tank through the block capabilities it registers.
 */
@Pseudo
@Mixin(targets = "dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationTankBlockEntity", remap = false)
abstract class YoukaiTankMixin implements KitchenKeg {
    @Unique
    private static final ResourceLocation HANDSON$FERMENTING = ResourceLocation.tryBuild("youkaisfeasts", "fermentation");

    @Unique
    private RecipeManager handson$readFrom;
    @Unique
    private List<Fermenting> handson$fermenting = List.of();

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

    @Override
    public int handson$ingredients() {
        return KegStation.TANK_INGREDIENTS;
    }

    @Override
    public int handson$perSlot() {
        return 1;
    }

    @Override
    public boolean handson$hasOutput() {
        return false;
    }

    @Override
    public boolean handson$feelsWarmth() {
        return false;
    }

    @Override
    public Boolean handson$lidOff() {
        BlockState state = handson$self().getBlockState();
        return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
    }

    @Override
    public void handson$setLidOff(boolean off) {
        Level level = handson$self().getLevel();
        BlockPos pos = handson$self().getBlockPos();
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN) != off) {
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, off), Block.UPDATE_ALL);
        }
    }

    /** Its simple recipes (a single kind of thing, no liquid: tea leaves) turn every one of that kind lying in it, however many. */
    @Override
    public boolean handson$makes(Fermenting recipe, List<ItemStack> items) {
        if (handson$isSimple(recipe)) {
            List<ItemStack> there = items.stream().filter(stack -> !stack.isEmpty()).toList();
            return !there.isEmpty() && there.stream().allMatch(stack -> recipe.ingredients().get(0).test(stack));
        }
        // As the keg reads its recipes; not through KitchenKeg.super: a mixin cannot call an interface's default that way.
        return StationRecipes.matches(recipe.ingredients(), items);
    }

    @Unique
    private static boolean handson$isSimple(Fermenting recipe) {
        return recipe.ingredients().size() == 1 && recipe.base() == Fluids.EMPTY && recipe.resultFluid() == Fluids.EMPTY;
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
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    timed.handson$setRecipeProgress(value);
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    /** It ferments at any warmth. */
    @Override
    public void handson$feelTemperature() {
    }

    @Override
    public int handson$wantedTemperature() {
        return 0;
    }

    /**
     * One at a time, as its own clicks do: its drink into the drink's container, a liquid out into a bucket, a bucket or a bottle
     * poured in. A player in creative mode pours without emptying what is poured.
     */
    @Override
    public List<ItemStack> handson$use(ItemStack held, int most, boolean creative) {
        List<ItemStack> back = new ArrayList<>();
        IFluidHandler tank = handson$tank();
        for (int i = 0; i < most && !held.isEmpty(); i++) {
            ItemStack used = creative ? held.copy() : held;
            ItemStack got = YoukaiFluids.useOne(tank, used);
            if (got.isEmpty()) {
                break;
            }
            if (!creative) {
                back.add(got);
            }
        }
        return back;
    }

    @Override
    public boolean handson$canTap(ItemStack stack) {
        return YoukaiFluids.canServe(handson$tank(), stack);
    }

    @Override
    public boolean handson$canPour(ItemStack stack) {
        return !YoukaiFluids.canServe(handson$tank(), stack) && YoukaiFluids.canPour(handson$tank(), stack);
    }

    @Override
    public boolean handson$isVessel(ItemStack stack) {
        List<Fluid> liquids = new ArrayList<>();
        for (Fermenting recipe : handson$recipes()) {
            liquids.add(recipe.base());
            liquids.add(recipe.resultFluid());
        }
        return YoukaiFluids.isVessel(stack, liquids);
    }

    @Override
    public ItemStack handson$drinkOf(Fluid fluid) {
        return YoukaiFluids.drinkOf(fluid);
    }

    @Override
    public int handson$tint(Fluid fluid) {
        return fluid == Fluids.EMPTY ? 0 : IClientFluidTypeExtensions.of(fluid).getTintColor();
    }

    /** Its fermenting recipes, read once for each recipe manager (the book and the scene ask every frame). */
    @Override
    public List<Fermenting> handson$recipes() {
        Level level = handson$self().getLevel();
        if (level == null) {
            return List.of();
        }
        RecipeManager manager = level.getRecipeManager();
        if (manager == handson$readFrom) {
            return handson$fermenting;
        }
        List<Fermenting> recipes = new ArrayList<>();
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(HANDSON$FERMENTING);
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (type == null || holder.value().getType() != type || !(holder.value() instanceof YoukaiFermentRecipeAccess fermenting)) {
                continue;
            }
            List<Ingredient> input = fermenting.handson$ingredients() == null ? List.of() : List.copyOf(fermenting.handson$ingredients());
            FluidStack base = fermenting.handson$inputFluid();
            FluidStack made = fermenting.handson$outputFluid();
            boolean liquid = base != null && !base.isEmpty();
            boolean drink = made != null && !made.isEmpty();
            ItemStack thing = handson$madeThing(fermenting.handson$results());
            if (input.isEmpty() && !liquid || !drink && thing.isEmpty()) {
                continue;
            }
            recipes.add(new Fermenting(holder.id(), input, liquid ? base.getFluid() : Fluids.EMPTY, liquid ? base.getAmount() : 0,
                    drink ? made.getFluid() : Fluids.EMPTY, drink ? ItemStack.EMPTY : thing, fermenting.handson$time(), 0));
        }
        handson$readFrom = manager;
        handson$fermenting = List.copyOf(recipes);
        return handson$fermenting;
    }

    /** What a recipe makes as things: its first result, as many as it lists of it (six natto). */
    @Unique
    private static ItemStack handson$madeThing(List<ItemStack> results) {
        if (results == null || results.isEmpty() || results.get(0).isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack first = results.get(0);
        int count = 0;
        for (ItemStack result : results) {
            if (ItemStack.isSameItemSameComponents(result, first)) {
                count += result.getCount();
            }
        }
        return first.copyWithCount(count);
    }

    /** Its tank gives nothing for a drink but the drink. */
    @Override
    public void handson$awardRecipes(Player player) {
    }

    @Override
    public BlockEntity handson$blockEntity() {
        return handson$self();
    }
}
