package com.pockyl.handson_delight.neoforge.mixin;

import com.chenjdy.farmers_spell.block.entity.AlchemistPotBlockEntity;
import com.chenjdy.farmers_spell.block.entity.container.AlchemistPotMenu;
import com.chenjdy.farmers_spell.recipe.AlchemistCookingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
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
import com.pockyl.handson_delight.neoforge.SpellScrolls;
import com.pockyl.handson_delight.platform.Platform;
import com.pockyl.handson_delight.station.KitchenPot;
import com.pockyl.handson_delight.station.PotCooking;
import com.pockyl.handson_delight.station.ScrollPot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Farmer's Spell's alchemist pot as a {@link KitchenPot}: Farmer's Delight's pot in a shape of its own, six ingredients, a meal, a
 * container and the served dish, and as a {@link ScrollPot} an Iron's Spells scroll in a slot of its own. It cooks its own recipes
 * ({@code farmers_spell:alchemist_cooking}, Farmer's Delight's with a school some of them need) before Farmer's Delight's, through its
 * own code; the station only reads them the same way.
 */
@Pseudo
@Mixin(AlchemistPotBlockEntity.class)
abstract class AlchemistPotMixin implements KitchenPot, ScrollPot {
    @Unique
    private static final ResourceLocation ALCHEMIST_COOKING = ResourceLocation.fromNamespaceAndPath(FarmersDelightContent.FARMERS_SPELL,
            "alchemist_cooking");

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
    @Shadow
    @Final
    private ItemStackHandler scrollHandler;
    @Unique
    private final PotCooking handson$cooking = new PotCooking(this);

    @Shadow
    private boolean processCooking(CookingPotRecipe recipe, ResourceLocation id, AlchemistPotBlockEntity pot) {
        throw new AssertionError();
    }

    @Shadow
    protected abstract void ejectIngredientRemainder(ItemStack remainder);

    @Unique
    private AlchemistPotBlockEntity handson$self() {
        return (AlchemistPotBlockEntity) (Object) this;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static RecipeType<AlchemistCookingRecipe> handson$alchemistType() {
        return (RecipeType<AlchemistCookingRecipe>) BuiltInRegistries.RECIPE_TYPE.get(ALCHEMIST_COOKING);
    }

    /** Its own recipes, then Farmer's Delight's: the order the pot tries them in. */
    @Override
    public List<RecipeHolder<CookingPotRecipe>> handson$recipes(Level level) {
        List<RecipeHolder<CookingPotRecipe>> recipes = new ArrayList<>();
        RecipeType<AlchemistCookingRecipe> type = handson$alchemistType();
        if (type != null) {
            level.getRecipeManager().getAllRecipesFor(type).forEach(own -> recipes.add(handson$asCooking(own)));
        }
        recipes.addAll(Platform.INSTANCE.cookingRecipes(level));
        return recipes;
    }

    /** As the pot finds it: its own first match if the scroll's school lets it cook, else Farmer's Delight's. */
    @Override
    public Optional<RecipeHolder<CookingPotRecipe>> handson$findRecipe(Level level, List<ItemStack> items) {
        Optional<RecipeHolder<AlchemistCookingRecipe>> own = handson$ownMatch(level, items);
        if (own.isPresent() && handson$cooksUnder(own.get().value(), handson$school())) {
            return own.map(AlchemistPotMixin::handson$asCooking);
        }
        return Platform.INSTANCE.findRecipe(level, items);
    }

    @Override
    public Optional<RecipeHolder<CookingPotRecipe>> handson$waiting(Level level, List<ItemStack> items) {
        return handson$ownMatch(level, items).filter(recipe -> !handson$cooksUnder(recipe.value(), handson$school()))
                .map(AlchemistPotMixin::handson$asCooking);
    }

    /** Its own recipe as one of Farmer's Delight's, which it is. */
    @Unique
    private static RecipeHolder<CookingPotRecipe> handson$asCooking(RecipeHolder<AlchemistCookingRecipe> own) {
        return new RecipeHolder<>(own.id(), own.value());
    }

    @Unique
    private static boolean handson$cooksUnder(AlchemistCookingRecipe recipe, ResourceLocation school) {
        return recipe.getRequiredSchool() == null || recipe.getRequiredSchool().equals(school);
    }

    @Unique
    private static Optional<RecipeHolder<AlchemistCookingRecipe>> handson$ownMatch(Level level, List<ItemStack> items) {
        RecipeType<AlchemistCookingRecipe> type = handson$alchemistType();
        if (type == null) {
            return Optional.empty();
        }
        ItemStackHandler slots = new ItemStackHandler(AlchemistPotBlockEntity.MEAL_DISPLAY_SLOT + 3);
        for (int i = 0; i < Math.min(items.size(), AlchemistPotBlockEntity.MEAL_DISPLAY_SLOT); i++) {
            slots.setStackInSlot(i, items.get(i).copy());
        }
        return level.getRecipeManager().getRecipeFor(type, new RecipeWrapper(slots), level);
    }

    @Override
    public PotCooking handson$cooking() {
        return handson$cooking;
    }

    @Override
    public int handson$slots() {
        return AlchemistPotBlockEntity.MEAL_DISPLAY_SLOT;
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

    /** A fire under it, or a fire scroll hanging over it. */
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
                if (player.containerMenu instanceof AlchemistPotMenu menu && menu.blockEntity == handson$self()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean handson$isOurs() {
        return handson$self().getBlockState().is(FarmersDelightContent.ALCHEMIST_POT.get());
    }

    /** What the recipe makes of what lies in the pot, as the pot's own step makes it: its own recipes are Farmer's Delight's. */
    @Override
    public ItemStack handson$cooked(CookingPotRecipe recipe) {
        Level level = handson$self().getLevel();
        return level == null ? recipe.getResultItem(RegistryAccess.EMPTY) : Platform.INSTANCE.cooked(level, recipe, KitchenPot.ingredients(this));
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

    /** The pot's own step, one method for its recipes and Farmer's Delight's. */
    @Override
    public boolean handson$cookOnce(RecipeHolder<CookingPotRecipe> recipe) {
        Level level = handson$self().getLevel();
        if (level == null) {
            return false;
        }
        Optional<RecipeHolder<?>> known = level.getRecipeManager().byKey(recipe.id());
        return known.filter(found -> found.value() instanceof CookingPotRecipe)
                .map(found -> processCooking((CookingPotRecipe) found.value(), found.id(), handson$self())).orElse(false);
    }

    @Override
    public void handson$throwOut(ItemStack remainder) {
        ejectIngredientRemainder(remainder);
    }

    @Override
    public BlockEntity handson$blockEntity() {
        return handson$self();
    }

    @Override
    public ItemStack handson$scroll() {
        return scrollHandler.getStackInSlot(0);
    }

    @Override
    public void handson$setScroll(ItemStack scroll) {
        scrollHandler.setStackInSlot(0, scroll);
    }

    @Override
    public boolean handson$isScroll(ItemStack stack) {
        return SpellScrolls.isScroll(stack);
    }

    /** As the pot reads it: the school of the scroll's first spell. */
    @Override
    public ResourceLocation handson$schoolOf(ItemStack scroll) {
        return SpellScrolls.schoolOf(scroll);
    }

    @Override
    public ResourceLocation handson$schoolFor(CookingPotRecipe recipe) {
        return recipe instanceof AlchemistCookingRecipe alchemist ? alchemist.getRequiredSchool() : null;
    }

    @Override
    public boolean handson$isOwnRecipe(CookingPotRecipe recipe) {
        return recipe instanceof AlchemistCookingRecipe;
    }

    @Override
    public List<ItemStack> handson$scrollsOf(ResourceLocation school) {
        return SpellScrolls.scrollsOf(school);
    }

    @Override
    public Ingredient handson$scrollIngredient(ResourceLocation school) {
        return SpellScrolls.ingredient(school);
    }

    @Override
    public int handson$colorOf(ResourceLocation school) {
        return SpellScrolls.colorOf(school);
    }

    @Inject(method = "cookingTick", at = @At("HEAD"))
    private static void handson$seeWhoFeedsIt(Level level, BlockPos pos, BlockState state, AlchemistPotBlockEntity pot, CallbackInfo callback) {
        ((KitchenPot) pot).handson$cooking().watchFood(level.getGameTime());
    }

    @Inject(method = "processCooking", at = @At("HEAD"), cancellable = true)
    private void handson$waitForTheSpoon(CookingPotRecipe recipe, ResourceLocation id, AlchemistPotBlockEntity pot,
            CallbackInfoReturnable<Boolean> callback) {
        if (!handson$cooking.beforeCooking(new RecipeHolder<>(id, recipe))) {
            callback.setReturnValue(false);
        }
    }

    /** The step set the recipe's time (shortened by a gluttony scroll) as the total: the pot's own time is the config's. */
    @Inject(method = "processCooking", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER,
            target = "Lcom/chenjdy/farmers_spell/block/entity/AlchemistPotBlockEntity;cookTimeTotal:I"))
    private void handson$timeAsConfigured(CookingPotRecipe recipe, ResourceLocation id, AlchemistPotBlockEntity pot,
            CallbackInfoReturnable<Boolean> callback) {
        cookTimeTotal = handson$cooking.timeFor(cookTimeTotal);
    }

    @Inject(method = "cookingTick", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lcom/chenjdy/farmers_spell/block/entity/"
            + "AlchemistPotBlockEntity;processCooking(Lvectorwing/farmersdelight/common/crafting/CookingPotRecipe;"
            + "Lnet/minecraft/resources/ResourceLocation;Lcom/chenjdy/farmers_spell/block/entity/AlchemistPotBlockEntity;)Z"))
    private static void handson$afterTheStep(Level level, BlockPos pos, BlockState state, AlchemistPotBlockEntity pot, CallbackInfo callback) {
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
