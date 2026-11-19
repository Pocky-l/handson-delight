package com.pockyl.handson_delight.neoforge.mixin;

import alabaster.hearthandharvest.common.advancement.HHSimpleTrigger;
import alabaster.hearthandharvest.common.block.CaskBlock;
import alabaster.hearthandharvest.common.block.entity.CaskBlockEntity;
import alabaster.hearthandharvest.common.crafting.CaskRecipe;
import alabaster.hearthandharvest.common.registry.HHModRecipeTypes;
import alabaster.hearthandharvest.common.registry.HHModTriggers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.util.RecipeMatcher;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import com.pockyl.handson_delight.station.KitchenKeg;

import java.util.ArrayList;
import java.util.List;

/**
 * Hearth and Harvest's cask as a {@link KitchenKeg} with no liquid: it ages what lies in its four places as it always does, the
 * station only reads it and uses it by hand. Its lid is its block's seal (a redstone signal seals it too), it ages only sealed, and
 * unsealed it starts the thing aging over, its own rule. What has aged goes to its four result slots, seen by the station as one, and
 * is given out sealed, as its menu and its hoppers do. Its times grow shorter in the dark.
 */
@Pseudo
@Mixin(value = CaskBlockEntity.class, remap = false)
abstract class HearthCaskMixin implements KitchenKeg {
    @Unique
    private static final int HANDSON$FIRST_RESULT = 4;
    @Unique
    private static final int HANDSON$RESULTS = 4;

    @Unique
    private RecipeManager handson$readFrom;
    @Unique
    private int handson$readAtSpeed;
    @Unique
    private List<Fermenting> handson$aging = List.of();

    /** A new cask stands sealed, as the world always shows it; a cask loaded from the world keeps what it was. */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void handson$sealedFromTheStart(BlockPos pos, BlockState state, CallbackInfo info) {
        ((HearthCaskAccess) (Object) this).handson$setSealed(true);
    }

    @Unique
    private CaskBlockEntity handson$self() {
        return (CaskBlockEntity) (Object) this;
    }

    @Unique
    private ItemStackHandler handson$inventory() {
        return handson$self().getInventory();
    }

    @Override
    public boolean handson$feelsWarmth() {
        return false;
    }

    @Override
    public boolean handson$quickUse() {
        return false;
    }

    /** Off when unsealed: the block's own seal, which its redstone also keeps on. */
    @Override
    public Boolean handson$lidOff() {
        return !handson$self().isSealed();
    }

    /** Seals or unseals it as its own sneaking use does, with its sounds; a cask a redstone signal keeps sealed stays so. */
    @Override
    public void handson$setLidOff(boolean off) {
        Level level = handson$self().getLevel();
        if (level != null && !level.isClientSide() && handson$self().isSealed() == off) {
            CaskBlock.toggleSealed(level, handson$self().getBlockPos());
        }
    }

    /** Its own reading: exactly as many things as the recipe has, one of each, in any of its places. */
    @Override
    public boolean handson$makes(Fermenting recipe, List<ItemStack> items) {
        List<ItemStack> present = items.stream().filter(stack -> !stack.isEmpty()).toList();
        return present.size() == recipe.ingredients().size() && RecipeMatcher.findMatches(present, recipe.ingredients()) != null;
    }

    @Override
    public ItemStack handson$getItem(int slot) {
        if (slot < INGREDIENTS) {
            return handson$inventory().getStackInSlot(slot);
        }
        return slot == OUTPUT_SLOT ? handson$inventory().getStackInSlot(handson$resultSlot()) : ItemStack.EMPTY;
    }

    @Override
    public void handson$setItem(int slot, ItemStack stack) {
        if (slot < INGREDIENTS) {
            handson$inventory().setStackInSlot(slot, stack);
        } else if (slot == OUTPUT_SLOT) {
            handson$inventory().setStackInSlot(handson$resultSlot(), stack);
        }
    }

    /** The result slot the station sees: the first that holds something, else the first. */
    @Unique
    private int handson$resultSlot() {
        for (int slot = HANDSON$FIRST_RESULT; slot < HANDSON$FIRST_RESULT + HANDSON$RESULTS; slot++) {
            if (!handson$inventory().getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        return HANDSON$FIRST_RESULT;
    }

    @Override
    public Fluid handson$fluid() {
        return Fluids.EMPTY;
    }

    @Override
    public int handson$amount() {
        return 0;
    }

    @Override
    public int handson$capacity() {
        return 1000;
    }

    /** How many of its units a tick ages by: 4 in the dark (sky light 0 to 5), 2 in the dim (to 10), else 1, as it counts. */
    @Unique
    private int handson$speed() {
        Level level = handson$self().getLevel();
        int light = level == null ? 7 : level.getBrightness(LightLayer.SKY, handson$self().getBlockPos());
        return light <= 5 ? 4 : light <= 10 ? 2 : 1;
    }

    /** Its progress in ticks at the light it stands in, as the station reads a keg's; no warmth: the middle. */
    @Override
    public ContainerData handson$data() {
        HearthCaskAccess access = (HearthCaskAccess) this;
        int speed = handson$speed();
        boolean aging = handson$self().isAging() || access.handson$ageTime() > 0;
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> access.handson$ageTime() / speed;
                    case 1 -> aging ? access.handson$ageTimeTotal() / speed : 0;
                    case 2 -> 3;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
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

    /** No liquid goes in or out. */
    @Override
    public List<ItemStack> handson$use(ItemStack held, int most, boolean creative) {
        return List.of();
    }

    @Override
    public boolean handson$canTap(ItemStack stack) {
        return false;
    }

    @Override
    public boolean handson$canPour(ItemStack stack) {
        return false;
    }

    /** Nothing is poured or filled here: a bottle of wine is what ages. */
    @Override
    public boolean handson$isVessel(ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack handson$drinkOf(Fluid fluid) {
        return ItemStack.EMPTY;
    }

    /**
     * Its aging recipes, with the time each takes at the light the cask stands in (twice the recipe's own, at 1, 2 or 4 a tick); read
     * once for each recipe manager and light.
     */
    @Override
    public List<Fermenting> handson$recipes() {
        Level level = handson$self().getLevel();
        if (level == null) {
            return List.of();
        }
        RecipeManager manager = level.getRecipeManager();
        int speed = handson$speed();
        if (manager == handson$readFrom && speed == handson$readAtSpeed) {
            return handson$aging;
        }
        List<Fermenting> recipes = new ArrayList<>();
        for (RecipeHolder<CaskRecipe> holder : manager.getAllRecipesFor(HHModRecipeTypes.AGING.get())) {
            CaskRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
            if (result.isEmpty() || recipe.getIngredients().isEmpty()) {
                continue;
            }
            recipes.add(new Fermenting(holder.id(), List.copyOf(recipe.getIngredients()), Fluids.EMPTY, 0, Fluids.EMPTY, result,
                    Math.max(1, recipe.getCookTime()) * 2 / speed, 0));
        }
        handson$readFrom = manager;
        handson$readAtSpeed = speed;
        handson$aging = List.copyOf(recipes);
        return handson$aging;
    }

    /** What its result slot gives a player who takes from it: the experience of what it aged, its recipes, its advancement. */
    @Override
    public void handson$awardRecipes(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        handson$self().awardUsedRecipes(player, handson$self().getDroppableInventory());
        HHSimpleTrigger.trigger(HHModTriggers.CASK_AGED.get(), player);
    }

    @Override
    public BlockEntity handson$blockEntity() {
        return handson$self();
    }
}
