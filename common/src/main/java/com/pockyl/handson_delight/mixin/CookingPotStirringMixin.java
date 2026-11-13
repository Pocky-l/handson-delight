package com.pockyl.handson_delight.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.KitchenPot;
import com.pockyl.handson_delight.station.PotCooking;

/**
 * Farmer's Delight's pot as a {@link KitchenPot}: its cooking goes through {@link PotCooking}, which waits for the spoon and cooks
 * a stirred pot as one batch. Pots of other mods built on this class keep cooking as Farmer's Delight does.
 */
@Mixin(value = CookingPotBlockEntity.class, remap = false)
abstract class CookingPotStirringMixin implements KitchenPot {
    @Shadow
    private int cookTime;
    @Shadow
    private int cookTimeTotal;
    @Shadow
    @Final
    protected ContainerData cookingPotData;
    @Unique
    private final PotCooking handson$cooking = new PotCooking(this);

    @Shadow
    private boolean processCooking(RecipeHolder<CookingPotRecipe> recipe, CookingPotBlockEntity pot) {
        throw new AssertionError();
    }

    @Shadow
    protected abstract void ejectIngredientRemainder(ItemStack remainder);

    @Unique
    private CookingPotBlockEntity handson$self() {
        return (CookingPotBlockEntity) (Object) this;
    }

    @Override
    public PotCooking handson$cooking() {
        return handson$cooking;
    }

    @Override
    public int handson$slots() {
        return CookingPotBlockEntity.MEAL_DISPLAY_SLOT;
    }

    @Override
    public ItemStack handson$getItem(int slot) {
        return Platform.INSTANCE.getItem(handson$self(), slot);
    }

    @Override
    public void handson$setItem(int slot, ItemStack stack) {
        Platform.INSTANCE.setItem(handson$self(), slot, stack);
    }

    @Override
    public ItemStack handson$meal() {
        return handson$self().getMeal();
    }

    @Override
    public boolean handson$isContainerValid(ItemStack container) {
        return handson$self().isContainerValid(container);
    }

    @Override
    public boolean handson$servesIn(ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack handson$serve(ItemStack container) {
        return handson$self().useHeldItemOnMeal(container);
    }

    @Override
    public boolean handson$isHeated() {
        return handson$self().isHeated();
    }

    @Override
    public ContainerData handson$data() {
        return cookingPotData;
    }

    @Override
    public void handson$awardRecipes(Player player) {
        handson$self().awardUsedRecipes(player, handson$self().getDroppableInventory());
    }

    @Override
    public boolean handson$isInMenu() {
        Level level = handson$self().getLevel();
        if (level != null) {
            for (Player player : level.players()) {
                if (player.containerMenu instanceof CookingPotMenu menu && menu.blockEntity == handson$self()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean handson$isOurs() {
        return handson$self().getBlockState().is(FarmersDelightContent.COOKING_POT.get());
    }

    @Override
    public ItemStack handson$cooked(CookingPotRecipe recipe) {
        Level level = handson$self().getLevel();
        return level == null ? recipe.getResultItem(RegistryAccess.EMPTY)
                : Platform.INSTANCE.cooked(level, recipe, KitchenPot.ingredients(this));
    }

    @Override
    public ItemStack handson$resultOf(CookingPotRecipe recipe, RegistryAccess access) {
        return recipe.getResultItem(access);
    }

    @Override
    public ItemStack handson$containerOf(CookingPotRecipe recipe, RegistryAccess access) {
        return recipe.getOutputContainer();
    }

    @Override
    public int handson$cookTime() {
        return cookTime;
    }

    @Override
    public void handson$setCookTime(int ticks) {
        cookTime = ticks;
    }

    @Override
    public int handson$cookTimeTotal() {
        return cookTimeTotal;
    }

    @Override
    public boolean handson$cookOnce(RecipeHolder<CookingPotRecipe> recipe) {
        return processCooking(recipe, handson$self());
    }

    @Override
    public void handson$throwOut(ItemStack remainder) {
        ejectIngredientRemainder(remainder);
    }

    @Override
    public BlockEntity handson$blockEntity() {
        return handson$self();
    }

    @Inject(method = "cookingTick", at = @At("HEAD"))
    private static void handson$seeWhoFeedsIt(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity pot, CallbackInfo callback) {
        ((KitchenPot) pot).handson$cooking().watchFood(level.getGameTime());
    }

    @Inject(method = "ejectIngredientRemainder", at = @At("HEAD"), cancellable = true)
    private void handson$keepLeftover(ItemStack remainder, CallbackInfo callback) {
        if (handson$cooking.keepLeftover(remainder)) {
            callback.cancel();
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void handson$save(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        handson$cooking.save(tag);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = true)
    private void handson$load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        handson$cooking.load(tag);
    }

    @Inject(method = "processCooking", at = @At("HEAD"), cancellable = true)
    private void handson$waitForTheSpoon(RecipeHolder<CookingPotRecipe> recipe, CookingPotBlockEntity pot,
            CallbackInfoReturnable<Boolean> callback) {
        if (!handson$cooking.beforeCooking(recipe)) {
            callback.setReturnValue(false);
        }
    }

    /** The step set the recipe's time as the total: the pot's own time is the config's. */
    @Inject(method = "processCooking", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER,
            target = "Lvectorwing/farmersdelight/common/block/entity/CookingPotBlockEntity;cookTimeTotal:I"))
    private void handson$timeAsConfigured(RecipeHolder<CookingPotRecipe> recipe, CookingPotBlockEntity pot, CallbackInfoReturnable<Boolean> callback) {
        cookTimeTotal = handson$cooking.timeFor(cookTimeTotal);
    }

    @Inject(method = "cookingTick", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lvectorwing/farmersdelight/common/block/entity/"
            + "CookingPotBlockEntity;processCooking(Lnet/minecraft/world/item/crafting/RecipeHolder;"
            + "Lvectorwing/farmersdelight/common/block/entity/CookingPotBlockEntity;)Z"))
    private static void handson$afterTheStep(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity pot, CallbackInfo callback) {
        ((KitchenPot) pot).handson$cooking().afterCooking();
    }
}
