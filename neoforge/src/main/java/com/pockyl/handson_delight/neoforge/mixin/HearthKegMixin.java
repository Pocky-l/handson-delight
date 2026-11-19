package com.pockyl.handson_delight.neoforge.mixin;

import alabaster.hearthandharvest.common.block.entity.KegBlockEntity;
import alabaster.hearthandharvest.common.crafting.KegRecipe;
import alabaster.hearthandharvest.common.fluid.HHFluidHandling;
import alabaster.hearthandharvest.common.item.WineBottleItem;
import alabaster.hearthandharvest.common.registry.HHDataMaps;
import alabaster.hearthandharvest.common.registry.HHModDataComponents;
import alabaster.hearthandharvest.common.registry.HHModRecipeTypes;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import com.pockyl.handson_delight.station.KitchenKeg;

import java.util.ArrayList;
import java.util.List;

/**
 * Hearth and Harvest's keg as a {@link KitchenKeg}: it ferments as it always does, the station only reads it and uses it by hand. Its two
 * slots take stacks and are read in any order; what is poured in goes to its input tank, the drink it makes to its output tank, which
 * the tap draws; what ferments into a thing (a jar of pickles, a cheese wheel) waits in its two result slots, seen by the station as one.
 * Its menu's container slots are left alone. Liquids go in and out as its own menu moves them ({@link HHFluidHandling}), so its wine
 * bottles of four glasses take a full tank.
 */
@Pseudo
@Mixin(value = KegBlockEntity.class, remap = false)
abstract class HearthKegMixin implements KitchenKeg {
    /** Its result slots, which the station shows as one ({@link KitchenKeg#OUTPUT_SLOT}). */
    @Unique
    private static final int HANDSON$RESULT = 2;
    @Unique
    private static final int HANDSON$RESULT_TWO = 3;

    @Unique
    private RecipeManager handson$readFrom;
    @Unique
    private List<Fermenting> handson$fermenting = List.of();

    @Unique
    private KegBlockEntity handson$self() {
        return (KegBlockEntity) (Object) this;
    }

    /**
     * The keg that holds everything: itself, or, for a keg built into a big keg, its controller, whose tanks hold 8000 mB and whose
     * slots are the big keg's.
     */
    @Unique
    private KegBlockEntity handson$keg() {
        KegBlockEntity controller = handson$self().controller();
        return controller == null ? handson$self() : controller;
    }

    @Unique
    private FluidTank handson$input() {
        return handson$keg().getInputTank();
    }

    @Unique
    private FluidTank handson$output() {
        return handson$keg().getOutputTank();
    }

    /** Where its big keg's controller stands, or its own place. */
    @Override
    public BlockPos handson$home() {
        return handson$keg().getBlockPos();
    }

    @Override
    public int handson$ingredients() {
        return 2;
    }

    @Override
    public boolean handson$feelsWarmth() {
        return false;
    }

    @Override
    public boolean handson$laysOut() {
        return false;
    }

    @Override
    public boolean handson$keepsDrinkApart() {
        return true;
    }

    @Override
    public boolean handson$quickUse() {
        return false;
    }

    /** Its own reading: each ingredient from any slot, several of a kind from one. */
    @Override
    public boolean handson$makes(Fermenting recipe, List<ItemStack> items) {
        KegRecipe own = handson$recipe(recipe);
        return own != null && own.matchesItems(items);
    }

    @Override
    public ItemStack handson$getItem(int slot) {
        if (slot < 2) {
            return handson$keg().getInventory().getStackInSlot(slot);
        }
        return slot == OUTPUT_SLOT ? handson$keg().getInventory().getStackInSlot(handson$resultSlot()) : ItemStack.EMPTY;
    }

    @Override
    public void handson$setItem(int slot, ItemStack stack) {
        if (slot < 2) {
            handson$keg().getInventory().setStackInSlot(slot, stack);
        } else if (slot == OUTPUT_SLOT) {
            handson$keg().getInventory().setStackInSlot(handson$resultSlot(), stack);
        }
    }

    /** The result slot the station sees: the first while it holds something or both are empty, else the second. */
    @Unique
    private int handson$resultSlot() {
        boolean firstEmpty = handson$keg().getInventory().getStackInSlot(HANDSON$RESULT).isEmpty();
        return firstEmpty && !handson$keg().getInventory().getStackInSlot(HANDSON$RESULT_TWO).isEmpty() ? HANDSON$RESULT_TWO : HANDSON$RESULT;
    }

    @Override
    public Fluid handson$fluid() {
        return !handson$input().isEmpty() ? handson$input().getFluid().getFluid() : handson$drawn();
    }

    @Override
    public int handson$amount() {
        return !handson$input().isEmpty() ? handson$input().getFluidAmount() : handson$output().getFluidAmount();
    }

    @Override
    public int handson$capacity() {
        return handson$input().getCapacity();
    }

    @Override
    public Fluid handson$drawn() {
        return handson$output().isEmpty() ? Fluids.EMPTY : handson$output().getFluid().getFluid();
    }

    @Override
    public int handson$drawnAmount() {
        return handson$output().getFluidAmount();
    }

    @Override
    public int handson$shownAmount() {
        return handson$input().getFluidAmount() + handson$output().getFluidAmount();
    }

    @Override
    public int handson$serving(Fluid fluid) {
        return GLASS;
    }

    /** A glass of a drink: its bottles hold four, and one is drunk at a time. */
    @Unique
    private static final int GLASS = 250;

    /**
     * {@code stack} filled from {@code from} as its menu fills it, or, an empty glass bottle where less than a full bottle of a drink is
     * left, a bottle of as many glasses as there are (its own opened bottle); with the millibuckets it takes. Null when it takes none.
     */
    @Unique
    private Pair<Integer, ItemStack> handson$fill(ItemStack stack, FluidTank from) {
        Pair<Integer, ItemStack> filling = HHFluidHandling.testFilling(stack.copyWithCount(1), from);
        if (filling.getFirst() > 0 && !filling.getSecond().isEmpty()) {
            return filling;
        }
        if (!stack.is(Items.GLASS_BOTTLE) || from.isEmpty() || from.getFluidAmount() < GLASS) {
            return null;
        }
        Item bottle = HHDataMaps.getBottleForFluid(from.getFluid().getFluid());
        if (!(bottle instanceof WineBottleItem wine) || wine.getMaxGlasses() <= 1) {
            return null;
        }
        int glasses = Math.min(wine.getMaxGlasses(), from.getFluidAmount() / GLASS);
        ItemStack opened = new ItemStack(bottle);
        if (glasses < wine.getMaxGlasses()) {
            opened.set(HHModDataComponents.SERVINGS.get(), glasses);
        }
        return Pair.of(glasses * GLASS, opened);
    }

    /** Its own progress in ticks, as the station reads a keg's: its menu's data is scaled to a thousand. No warmth: the middle. */
    @Override
    public ContainerData handson$data() {
        HearthKegAccess access = (HearthKegAccess) (Object) handson$keg();
        KegBlockEntity keg = handson$keg();
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> access.handson$fermentTime();
                    case 1 -> access.handson$fermentTime() > 0 || keg.isFermenting() ? access.handson$fermentTimeTotal() : 0;
                    case 2 -> 3;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    access.handson$setFermentTime(value);
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    @Override
    public void handson$feelTemperature() {
    }

    @Override
    public int handson$wantedTemperature() {
        return 0;
    }

    @Override
    public List<ItemStack> handson$use(ItemStack held, int most, boolean creative) {
        return handson$useAt(held, most, creative, false);
    }

    /**
     * One container at a time, as its menu fills them: at the tap from the output tank; at the opening a full one pours into the input
     * tank (in creative mode without emptying it), an empty one takes the drink, or the input back while no drink is there.
     */
    @Override
    public List<ItemStack> handson$useAt(ItemStack held, int most, boolean creative, boolean tap) {
        List<ItemStack> back = new ArrayList<>();
        if (held.isEmpty()) {
            return back;
        }
        if (!tap && handson$canPour(held)) {
            Pair<FluidStack, ItemStack> emptying = HHFluidHandling.testEmptying(held.copyWithCount(1), handson$input());
            handson$input().fill(emptying.getFirst(), IFluidHandler.FluidAction.EXECUTE);
            if (!creative) {
                held.shrink(1);
                back.add(emptying.getSecond());
            }
            return back;
        }
        // A container dipped at the opening takes the drink as the tap gives it, and the liquid it is made from only while no drink is
        // there: what the keg shows is what it gives.
        FluidTank from = tap || handson$fill(held, handson$output()) != null ? handson$output() : handson$input();
        Pair<Integer, ItemStack> filling = handson$fill(held, from);
        if (filling != null) {
            from.drain(filling.getFirst(), IFluidHandler.FluidAction.EXECUTE);
            held.shrink(1);
            back.add(filling.getSecond());
        }
        return back;
    }

    @Override
    public boolean handson$canTap(ItemStack stack) {
        return !stack.isEmpty() && handson$fill(stack, handson$output()) != null;
    }

    /** A liquid one of its recipes ferments, poured in where there is room for all of it, as its menu takes it. */
    @Override
    public boolean handson$canPour(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        FluidStack inside = HHFluidHandling.testEmptying(stack.copyWithCount(1), handson$input()).getFirst();
        return !inside.isEmpty() && handson$recipes().stream().anyMatch(recipe -> recipe.base() != Fluids.EMPTY
                && recipe.base().isSame(inside.getFluid()));
    }

    @Override
    public boolean handson$canDrawBack(ItemStack stack) {
        return !stack.isEmpty() && (handson$fill(stack, handson$output()) != null || handson$fill(stack, handson$input()) != null);
    }

    @Override
    public boolean handson$isVessel(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.is(Items.GLASS_BOTTLE) || HHFluidHandling.isWaterBottle(stack) || HHDataMaps.getFluidForBottle(stack.getItem()) != null
                || stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
    }

    @Override
    public int handson$tint(Fluid fluid) {
        return fluid == Fluids.EMPTY ? 0 : IClientFluidTypeExtensions.of(fluid).getTintColor();
    }

    /** The bottle it fills with {@code fluid} (a wine, its brine), else the bucket. */
    @Override
    public ItemStack handson$drinkOf(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return ItemStack.EMPTY;
        }
        if (fluid.isSame(Fluids.WATER)) {
            return HHFluidHandling.waterBottle();
        }
        Item bottle = HHDataMaps.getBottleForFluid(fluid);
        if (bottle != null) {
            return new ItemStack(bottle);
        }
        return fluid.getBucket() == Items.AIR ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
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
        for (RecipeHolder<KegRecipe> holder : manager.getAllRecipesFor(HHModRecipeTypes.FERMENTING.get())) {
            KegRecipe recipe = holder.value();
            List<FluidStack> bases = recipe.getInputFluid() == null ? List.of() : recipe.getInputFluidStacks();
            Fluid base = bases.isEmpty() ? Fluids.EMPTY : bases.get(0).getFluid();
            FluidStack result = recipe.getResultFluid();
            recipes.add(new Fermenting(holder.id(), List.copyOf(recipe.getItemIngredients()), base, recipe.getInputAmount(),
                    result.isEmpty() ? Fluids.EMPTY : result.getFluid(), recipe.getResultItem().copy(), recipe.getFermentTime(), 0));
        }
        handson$readFrom = manager;
        handson$fermenting = List.copyOf(recipes);
        return handson$fermenting;
    }

    @Unique
    private KegRecipe handson$recipe(Fermenting recipe) {
        Level level = handson$self().getLevel();
        if (level == null) {
            return null;
        }
        return level.getRecipeManager().byKey(recipe.id()).map(RecipeHolder::value).filter(KegRecipe.class::isInstance)
                .map(KegRecipe.class::cast).orElse(null);
    }

    /** Its keg gives no experience and unlocks nothing: nothing to award. */
    @Override
    public void handson$awardRecipes(Player player) {
    }

    @Override
    public BlockEntity handson$blockEntity() {
        return handson$keg();
    }
}
