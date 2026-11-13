package com.pockyl.handson_delight.forge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.yirmiri.dungeonsdelight.common.block.entity.MonsterPotBlockEntity;
import net.yirmiri.dungeonsdelight.common.block.entity.container.MonsterPotMenu;
import net.yirmiri.dungeonsdelight.common.block.entity.container.MonsterPotRecipe;
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
import com.pockyl.handson_delight.station.KitchenPot;
import com.pockyl.handson_delight.station.PotCooking;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dungeons Delight's monster pot as a {@link KitchenPot}: a copy of Farmer's Delight's pot, six ingredients, a meal, a container and
 * the served dish, with recipes of its own type ({@code dungeonsdelight:monster_cooking}) that copy Farmer's Delight's field for field.
 * The station reads them as Farmer's Delight's recipes; the pot itself still cooks its own, through its own code.
 */
@Pseudo
@Mixin(value = MonsterPotBlockEntity.class, remap = false)
@SuppressWarnings("removal")
abstract class MonsterPotMixin implements KitchenPot {
    @Unique
    private static final ResourceLocation MONSTER_COOKING = new ResourceLocation(FarmersDelightContent.DUNGEONS_DELIGHT, "monster_cooking");

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
    private boolean processCooking(MonsterPotRecipe recipe, MonsterPotBlockEntity pot) {
        throw new AssertionError();
    }

    @Shadow
    protected abstract void ejectIngredientRemainder(ItemStack remainder);

    @Unique
    private MonsterPotBlockEntity handson$self() {
        return (MonsterPotBlockEntity) (Object) this;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static List<MonsterPotRecipe> handson$monsterRecipes(Level level) {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(MONSTER_COOKING);
        return type == null ? List.of() : level.getRecipeManager().getAllRecipesFor((RecipeType<MonsterPotRecipe>) type);
    }

    /** A monster recipe as Farmer's Delight's: the same id, ingredients, dish, container, experience and time. */
    @Unique
    private static CookingPotRecipe handson$asCooking(MonsterPotRecipe recipe, RegistryAccess access) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.addAll(recipe.getIngredients());
        return new CookingPotRecipe(recipe.getId(), recipe.getGroup(), recipe.getRecipeBookTab(), ingredients, recipe.getResultItem(access),
                recipe.getOutputContainer(), recipe.getExperience(), recipe.getCookTime());
    }

    @Override
    public PotCooking handson$cooking() {
        return handson$cooking;
    }

    @Override
    public List<CookingPotRecipe> handson$recipes(Level level) {
        List<CookingPotRecipe> recipes = new ArrayList<>();
        for (MonsterPotRecipe recipe : handson$monsterRecipes(level)) {
            recipes.add(handson$asCooking(recipe, level.registryAccess()));
        }
        return recipes;
    }

    @Override
    public Optional<CookingPotRecipe> handson$findRecipe(Level level, List<ItemStack> items) {
        ItemStackHandler slots = new ItemStackHandler(MonsterPotBlockEntity.INVENTORY_SIZE);
        for (int i = 0; i < Math.min(items.size(), MonsterPotBlockEntity.MEAL_DISPLAY_SLOT); i++) {
            slots.setStackInSlot(i, items.get(i).copy());
        }
        RecipeWrapper wrapper = new RecipeWrapper(slots);
        return handson$monsterRecipes(level).stream().filter(recipe -> recipe.matches(wrapper, level)).findFirst()
                .map(recipe -> handson$asCooking(recipe, level.registryAccess()));
    }

    @Override
    public int handson$slots() {
        return MonsterPotBlockEntity.MEAL_DISPLAY_SLOT;
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
                if (player.containerMenu instanceof MonsterPotMenu menu && menu.blockEntity == handson$self()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean handson$isOurs() {
        return handson$self().getBlockState().is(FarmersDelightContent.MONSTER_POT.get());
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

    /** The pot's own step with its own recipe of that id. */
    @Override
    public boolean handson$cookOnce(CookingPotRecipe recipe) {
        Level level = handson$self().getLevel();
        if (level == null) {
            return false;
        }
        return level.getRecipeManager().byKey(recipe.getId()).filter(MonsterPotRecipe.class::isInstance).map(MonsterPotRecipe.class::cast)
                .map(monster -> processCooking(monster, handson$self())).orElse(false);
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
    private static void handson$seeWhoFeedsIt(Level level, BlockPos pos, BlockState state, MonsterPotBlockEntity pot, CallbackInfo callback) {
        ((KitchenPot) pot).handson$cooking().watchFood(level.getGameTime());
    }

    @Inject(method = "processCooking", at = @At("HEAD"), cancellable = true)
    private void handson$waitForTheSpoon(MonsterPotRecipe recipe, MonsterPotBlockEntity pot, CallbackInfoReturnable<Boolean> callback) {
        Level level = handson$self().getLevel();
        if (level != null && !handson$cooking.beforeCooking(handson$asCooking(recipe, level.registryAccess()))) {
            callback.setReturnValue(false);
        }
    }

    /** The step set the recipe's time as the total: the pot's own time is the config's. */
    @Inject(method = "processCooking", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER,
            target = "Lnet/yirmiri/dungeonsdelight/common/block/entity/MonsterPotBlockEntity;cookTimeTotal:I"))
    private void handson$timeAsConfigured(MonsterPotRecipe recipe, MonsterPotBlockEntity pot, CallbackInfoReturnable<Boolean> callback) {
        cookTimeTotal = handson$cooking.timeFor(cookTimeTotal);
    }

    @Inject(method = "cookingTick", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/yirmiri/dungeonsdelight/common/block/"
            + "entity/MonsterPotBlockEntity;processCooking(Lnet/yirmiri/dungeonsdelight/common/block/entity/container/MonsterPotRecipe;"
            + "Lnet/yirmiri/dungeonsdelight/common/block/entity/MonsterPotBlockEntity;)Z"))
    private static void handson$afterTheStep(Level level, BlockPos pos, BlockState state, MonsterPotBlockEntity pot, CallbackInfo callback) {
        ((KitchenPot) pot).handson$cooking().afterCooking();
    }

    /** With a cook at the station, what the ingredients leave goes to them: no bucket lands on the floor beside the pot. */
    @Inject(method = "ejectIngredientRemainder", at = @At("HEAD"), cancellable = true)
    private void handson$keepLeftover(ItemStack remainder, CallbackInfo callback) {
        if (handson$cooking.keepLeftover(remainder)) {
            callback.cancel();
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
