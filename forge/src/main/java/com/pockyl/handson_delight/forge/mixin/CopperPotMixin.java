package com.pockyl.handson_delight.forge.mixin;

import com.sammy.minersdelight.content.block.copper_pot.CopperPotBlockEntity;
import com.sammy.minersdelight.content.block.copper_pot.CopperPotMenu;
import com.sammy.minersdelight.logic.CupConversionReloadListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.items.ItemStackHandler;
import org.objectweb.asm.Opcodes;
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
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.utility.ItemUtils;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.station.KitchenPot;
import com.pockyl.handson_delight.station.PotCooking;

/**
 * Miner's Delight's copper pot as a {@link KitchenPot}: a copy of Farmer's Delight's pot with four ingredient slots, whose soups come
 * out in copper cups. It cooks at the station as Farmer's Delight's pot does, stirred, as one batch.
 */
@Pseudo
@Mixin(value = CopperPotBlockEntity.class, remap = false)
@SuppressWarnings("removal")
abstract class CopperPotMixin implements KitchenPot {
    @Unique
    private static final ResourceLocation CUP = new ResourceLocation(FarmersDelightContent.MINERS_DELIGHT, "copper_cup");

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
    private boolean processCooking(CookingPotRecipe recipe, CopperPotBlockEntity pot) {
        throw new AssertionError();
    }

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

    /** A bowl's serving fills two copper cups, as the copper pot serves it. */
    @Override
    public ItemStack handson$resultOf(CookingPotRecipe recipe, RegistryAccess access) {
        ItemStack result = recipe.getResultItem(access);
        Item cup = handson$cupOf(result.getItem());
        return cup == null ? result : new ItemStack(cup, Math.min(result.getCount() * 2, cup.getMaxStackSize()));
    }

    @Override
    public ItemStack handson$containerOf(CookingPotRecipe recipe, RegistryAccess access) {
        return handson$cupOf(recipe.getResultItem(access).getItem()) == null ? recipe.getOutputContainer()
                : new ItemStack(BuiltInRegistries.ITEM.get(CUP));
    }

    /**
     * The copper cup version of a dish, or null. The server reads them from Miner's Delight's data; a client of another server has
     * none, and finds them by name ({@code beef_stew} -> {@code miners_delight:beef_stew_cup}), as Miner's Delight names its own.
     */
    @Unique
    private static Item handson$cupOf(Item dish) {
        if (!CupConversionReloadListener.BOWL_TO_CUP.isEmpty()) {
            return CupConversionReloadListener.BOWL_TO_CUP.get(dish);
        }
        ResourceLocation named = new ResourceLocation(FarmersDelightContent.MINERS_DELIGHT, BuiltInRegistries.ITEM.getKey(dish).getPath() + "_cup");
        Item cup = BuiltInRegistries.ITEM.get(named);
        return cup == Items.AIR ? null : cup;
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
    public boolean handson$cookOnce(CookingPotRecipe recipe) {
        return processCooking(recipe, handson$self());
    }

    /** As the copper pot throws a remainder out itself: from its side, a little up. */
    @Override
    public void handson$throwOut(ItemStack remainder) {
        BlockEntity self = handson$self();
        Level level = self.getLevel();
        if (level == null) {
            return;
        }
        Direction side = self.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getCounterClockWise();
        BlockPos pos = self.getBlockPos();
        ItemUtils.spawnItemEntity(level, remainder.copy(), pos.getX() + 0.5 + side.getStepX() * 0.25, pos.getY() + 0.7,
                pos.getZ() + 0.5 + side.getStepZ() * 0.25, side.getStepX() * 0.08F, 0.25F, side.getStepZ() * 0.08F);
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
    private void handson$waitForTheSpoon(CookingPotRecipe recipe, CopperPotBlockEntity pot, CallbackInfoReturnable<Boolean> callback) {
        if (!handson$cooking.beforeCooking(recipe)) {
            callback.setReturnValue(false);
        }
    }

    /** The step set the recipe's time as the total: the pot's own time is the config's. */
    @Inject(method = "processCooking", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER,
            target = "Lcom/sammy/minersdelight/content/block/copper_pot/CopperPotBlockEntity;cookTimeTotal:I"))
    private void handson$timeAsConfigured(CookingPotRecipe recipe, CopperPotBlockEntity pot, CallbackInfoReturnable<Boolean> callback) {
        cookTimeTotal = handson$cooking.timeFor(cookTimeTotal);
    }

    @Inject(method = "cookingTick", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lcom/sammy/minersdelight/content/block/"
            + "copper_pot/CopperPotBlockEntity;processCooking(Lvectorwing/farmersdelight/common/crafting/CookingPotRecipe;"
            + "Lcom/sammy/minersdelight/content/block/copper_pot/CopperPotBlockEntity;)Z"))
    private static void handson$afterTheStep(Level level, BlockPos pos, BlockState state, CopperPotBlockEntity pot, CallbackInfo callback) {
        ((KitchenPot) pot).handson$cooking().afterCooking();
    }

    /** With a cook at the station, what the ingredients leave goes to them: no bucket lands on the floor beside the pot. */
    @Redirect(method = "processCooking", at = @At(value = "INVOKE", target = "Lvectorwing/farmersdelight/common/utility/ItemUtils;"
            + "spawnItemEntity(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;DDDDDD)V"))
    private void handson$keepLeftover(Level level, ItemStack stack, double x, double y, double z, double dx, double dy, double dz) {
        if (!handson$cooking.keepLeftover(stack)) {
            ItemUtils.spawnItemEntity(level, stack, x, y, z, dx, dy, dz);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void handson$save(CompoundTag tag, CallbackInfo callback) {
        handson$cooking.save(tag);
    }

    @Inject(method = "load", at = @At("TAIL"), remap = true)
    private void handson$load(CompoundTag tag, CallbackInfo callback) {
        handson$cooking.load(tag);
    }
}
