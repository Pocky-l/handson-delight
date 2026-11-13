package com.pockyl.handson_delight.neoforge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
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
import net.yirmiri.dungeonsdelight.common.block.monster_pot.MonsterPotBlockEntity;
import net.yirmiri.dungeonsdelight.common.block.monster_pot.MonsterPotMenu;
import net.yirmiri.dungeonsdelight.common.block.monster_pot.MonsterPotRecipe;
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
import vectorwing.farmersdelight.client.recipebook.CookingPotRecipeBookTab;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.platform.Platform;
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
@Mixin(MonsterPotBlockEntity.class)
abstract class MonsterPotMixin implements KitchenPot {
    @Unique
    private static final ResourceLocation MONSTER_COOKING = ResourceLocation.fromNamespaceAndPath(FarmersDelightContent.DUNGEONS_DELIGHT,
            "monster_cooking");

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
    private boolean processCooking(RecipeHolder<MonsterPotRecipe> recipe, MonsterPotBlockEntity pot) {
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
    private static List<RecipeHolder<MonsterPotRecipe>> handson$monsterRecipes(Level level) {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(MONSTER_COOKING);
        return type == null ? List.of() : level.getRecipeManager().getAllRecipesFor((RecipeType<MonsterPotRecipe>) type);
    }

    /** A monster recipe as Farmer's Delight's: the same id, ingredients, dish, container, experience and time, its tab by its place. */
    @Unique
    private static RecipeHolder<CookingPotRecipe> handson$asCooking(RecipeHolder<MonsterPotRecipe> holder, RegistryAccess access) {
        MonsterPotRecipe recipe = holder.value();
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.addAll(recipe.getIngredients());
        CookingPotRecipeBookTab[] tabs = CookingPotRecipeBookTab.values();
        CookingPotRecipeBookTab tab = recipe.getRecipeBookTab() == null ? CookingPotRecipeBookTab.MEALS
                : tabs[Math.min(recipe.getRecipeBookTab().ordinal(), tabs.length - 1)];
        return new RecipeHolder<>(holder.id(), new CookingPotRecipe(recipe.getGroup(), tab, ingredients, recipe.getResultItem(access),
                recipe.getOutputContainer(), recipe.getExperience(), recipe.getCookTime()));
    }

    @Override
    public PotCooking handson$cooking() {
        return handson$cooking;
    }

    @Override
    public List<RecipeHolder<CookingPotRecipe>> handson$recipes(Level level) {
        List<RecipeHolder<CookingPotRecipe>> recipes = new ArrayList<>();
        for (RecipeHolder<MonsterPotRecipe> recipe : handson$monsterRecipes(level)) {
            recipes.add(handson$asCooking(recipe, level.registryAccess()));
        }
        return recipes;
    }

    @Override
    public Optional<RecipeHolder<CookingPotRecipe>> handson$findRecipe(Level level, List<ItemStack> items) {
        RecipeWrapper wrapper = handson$wrapper(items);
        return handson$monsterRecipes(level).stream().filter(recipe -> recipe.value().matches(wrapper, level)).findFirst()
                .map(recipe -> handson$asCooking(recipe, level.registryAccess()));
    }

    /** {@code items} in the ingredient slots of a pot's inventory, as the pot hands it to its recipes. */
    @Unique
    private static RecipeWrapper handson$wrapper(List<ItemStack> items) {
        ItemStackHandler slots = new ItemStackHandler(MonsterPotBlockEntity.INVENTORY_SIZE);
        for (int i = 0; i < Math.min(items.size(), MonsterPotBlockEntity.MEAL_DISPLAY_SLOT); i++) {
            slots.setStackInSlot(i, items.get(i).copy());
        }
        return new RecipeWrapper(slots);
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

    /** What the pot's own recipe of that id makes of what lies in the pot, as its own step makes it. */
    @Override
    public ItemStack handson$cooked(CookingPotRecipe recipe) {
        Level level = handson$self().getLevel();
        if (level == null) {
            return recipe.getResultItem(RegistryAccess.EMPTY);
        }
        RecipeWrapper wrapper = handson$wrapper(KitchenPot.ingredients(this));
        return handson$monsterRecipes(level).stream().filter(monster -> monster.value().matches(wrapper, level)).findFirst()
                .map(monster -> monster.value().assemble(wrapper, level.registryAccess()))
                .orElseGet(() -> Platform.INSTANCE.cooked(level, recipe, KitchenPot.ingredients(this)));
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
    public boolean handson$cookOnce(RecipeHolder<CookingPotRecipe> recipe) {
        Level level = handson$self().getLevel();
        if (level == null) {
            return false;
        }
        return handson$monsterRecipes(level).stream().filter(monster -> monster.id().equals(recipe.id())).findFirst()
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
    private void handson$waitForTheSpoon(RecipeHolder<MonsterPotRecipe> recipe, MonsterPotBlockEntity pot,
            CallbackInfoReturnable<Boolean> callback) {
        Level level = handson$self().getLevel();
        if (level != null && !handson$cooking.beforeCooking(handson$asCooking(recipe, level.registryAccess()))) {
            callback.setReturnValue(false);
        }
    }

    /** The step set the recipe's time as the total: the pot's own time is the config's. */
    @Inject(method = "processCooking", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER,
            target = "Lnet/yirmiri/dungeonsdelight/common/block/monster_pot/MonsterPotBlockEntity;cookTimeTotal:I"))
    private void handson$timeAsConfigured(RecipeHolder<MonsterPotRecipe> recipe, MonsterPotBlockEntity pot,
            CallbackInfoReturnable<Boolean> callback) {
        cookTimeTotal = handson$cooking.timeFor(cookTimeTotal);
    }

    @Inject(method = "cookingTick", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/yirmiri/dungeonsdelight/common/block/"
            + "monster_pot/MonsterPotBlockEntity;processCooking(Lnet/minecraft/world/item/crafting/RecipeHolder;"
            + "Lnet/yirmiri/dungeonsdelight/common/block/monster_pot/MonsterPotBlockEntity;)Z"))
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

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void handson$save(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        handson$cooking.save(tag);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void handson$load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        handson$cooking.load(tag);
    }
}
