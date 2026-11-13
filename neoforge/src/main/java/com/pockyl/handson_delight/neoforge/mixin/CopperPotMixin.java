package com.pockyl.handson_delight.neoforge.mixin;

import com.sammy.minersdelight.content.block.copper_pot.CopperPotBlockEntity;
import com.sammy.minersdelight.content.block.copper_pot.CopperPotMenu;
import com.sammy.minersdelight.content.data.CupConversionDataMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.KitchenPot;
import com.pockyl.handson_delight.station.PotCooking;

import java.util.Optional;

/**
 * Miner's Delight's copper pot as a {@link KitchenPot}: a copy of Farmer's Delight's pot with four ingredient slots, whose soups come
 * out in copper cups. It cooks at the station as Farmer's Delight's pot does, stirred, as one batch.
 */
@Pseudo
@Mixin(CopperPotBlockEntity.class)
abstract class CopperPotMixin implements KitchenPot {
    @Unique
    private static final ResourceLocation CUP = ResourceLocation.fromNamespaceAndPath(FarmersDelightContent.MINERS_DELIGHT, "copper_cup");

    @Shadow
    private int cookTime;
    @Shadow
    private int cookTimeTotal;
    @Shadow
    @Final
    protected ContainerData cookingPotData;
    @Shadow
    @Final
    private ItemStackHandler inventory;
    @Unique
    private final PotCooking handson$cooking = new PotCooking(this);

    @Shadow
    private boolean processCooking(RecipeHolder<CookingPotRecipe> recipe, CopperPotBlockEntity pot) {
        throw new AssertionError();
    }

    @Shadow
    protected abstract void ejectIngredientRemainder(ItemStack remainder);

    @Unique
    private CopperPotBlockEntity handson$self() {
        return (CopperPotBlockEntity) (Object) this;
    }

    @Override
    public PotCooking handson$cooking() {
        return handson$cooking;
    }

    @Override
    public int handson$slots() {
        return CopperPotBlockEntity.MEAL_DISPLAY_SLOT;
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
    public ItemStack handson$meal() {
        return handson$self().getMeal();
    }

    @Override
    public boolean handson$isContainerValid(ItemStack container) {
        return handson$self().isContainerValid(container);
    }

    @Override
    public boolean handson$servesIn(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(CUP);
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
                if (player.containerMenu instanceof CopperPotMenu menu && menu.blockEntity == handson$self()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean handson$isOurs() {
        return handson$self().getBlockState().is(FarmersDelightContent.COPPER_POT.get());
    }

    @Override
    public ItemStack handson$cooked(CookingPotRecipe recipe) {
        Level level = handson$self().getLevel();
        return handson$inCups(level == null ? recipe.getResultItem(RegistryAccess.EMPTY)
                : Platform.INSTANCE.cooked(level, recipe, KitchenPot.ingredients(this)));
    }

    @Override
    public ItemStack handson$resultOf(CookingPotRecipe recipe, RegistryAccess access) {
        return handson$inCups(recipe.getResultItem(access));
    }

    @Override
    public ItemStack handson$containerOf(CookingPotRecipe recipe, RegistryAccess access) {
        Item cup = handson$cupOf(recipe.getResultItem(access).getItem());
        return cup == null ? recipe.getOutputContainer() : new ItemStack(cup).getCraftingRemainingItem();
    }

    /** A bowl's serving fills one cup more than it had bowls, as the copper pot serves it. */
    @Unique
    private ItemStack handson$inCups(ItemStack dish) {
        Item cup = handson$cupOf(dish.getItem());
        return cup == null ? dish : new ItemStack(cup, Math.min(dish.getCount() + 1, cup.getDefaultMaxStackSize()));
    }

    /**
     * The copper cup version of a dish, or null. Miner's Delight keeps them in an item data map that is not sent to clients: a client
     * of another server finds them by name ({@code beef_stew} -> {@code minersdelight:beef_stew_cup}), as Miner's Delight names its own.
     */
    @Unique
    private Item handson$cupOf(Item dish) {
        Optional<ItemStack> cup = CupConversionDataMap.getCupVariant(new ItemStack(dish));
        if (cup.isPresent()) {
            return cup.get().getItem();
        }
        Level level = handson$self().getLevel();
        if (level == null || !level.isClientSide()) {
            return null;
        }
        ResourceLocation named = ResourceLocation.fromNamespaceAndPath(FarmersDelightContent.MINERS_DELIGHT,
                BuiltInRegistries.ITEM.getKey(dish).getPath() + "_cup");
        Item byName = BuiltInRegistries.ITEM.get(named);
        return byName == Items.AIR ? null : byName;
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
    private static void handson$seeWhoFeedsIt(Level level, BlockPos pos, BlockState state, CopperPotBlockEntity pot, CallbackInfo callback) {
        ((KitchenPot) pot).handson$cooking().watchFood(level.getGameTime());
    }

    @Inject(method = "processCooking", at = @At("HEAD"), cancellable = true)
    private void handson$waitForTheSpoon(RecipeHolder<CookingPotRecipe> recipe, CopperPotBlockEntity pot, CallbackInfoReturnable<Boolean> callback) {
        if (!handson$cooking.beforeCooking(recipe)) {
            callback.setReturnValue(false);
        }
    }

    /** The step set the recipe's time as the total: the pot's own time is the config's. */
    @Inject(method = "processCooking", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER,
            target = "Lcom/sammy/minersdelight/content/block/copper_pot/CopperPotBlockEntity;cookTimeTotal:I"))
    private void handson$timeAsConfigured(RecipeHolder<CookingPotRecipe> recipe, CopperPotBlockEntity pot, CallbackInfoReturnable<Boolean> callback) {
        cookTimeTotal = handson$cooking.timeFor(cookTimeTotal);
    }

    @Inject(method = "cookingTick", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lcom/sammy/minersdelight/content/block/"
            + "copper_pot/CopperPotBlockEntity;processCooking(Lnet/minecraft/world/item/crafting/RecipeHolder;"
            + "Lcom/sammy/minersdelight/content/block/copper_pot/CopperPotBlockEntity;)Z"))
    private static void handson$afterTheStep(Level level, BlockPos pos, BlockState state, CopperPotBlockEntity pot, CallbackInfo callback) {
        ((KitchenPot) pot).handson$cooking().afterCooking();
    }

    /** With a cook at the station, what the ingredients leave goes to them: no bucket lands on the floor beside the pot. */
    @Inject(method = "ejectIngredientRemainder", at = @At("HEAD"), cancellable = true)
    private void handson$keepLeftover(ItemStack remainder, CallbackInfo callback) {
        if (handson$cooking.keepLeftover(remainder)) {
            callback.cancel();
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void handson$save(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        handson$cooking.save(tag);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void handson$load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        handson$cooking.load(tag);
    }
}
